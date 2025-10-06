package io.kanbanai.paramsview;

import hudson.model.*;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@ExportedBean
public class ParamsViewAction implements Action {
  private final Job<?,?> job;

  public ParamsViewAction(@NonNull Job<?,?> job) {
    this.job = job;
  }

  @Override public String getIconFileName() {
    return job.hasPermission(Item.READ) ? "symbol-parameters.svg" : null;
  }

  @Override public String getDisplayName() { return "Params View"; }

  @Override public String getUrlName() { return "params-view"; }

  public Job<?,?> getJob() { return job; }

  public List<ParamDTO> getEffectiveParameters() {
    if (!job.hasPermission(Item.READ)) return Collections.emptyList();

    ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
    if (prop == null) return Collections.emptyList();

    List<ParameterDefinition> defs = prop.getParameterDefinitions();

    List<ParamDTO> out = new ArrayList<>();
    for (ParameterDefinition def : defs) {
      ParamDTO dto = new ParamDTO();
      dto.name = def.getName();
      dto.type = def.getClass().getName();
      dto.description = def.getDescription();

      ParameterValue dv = null;
      try { dv = def.getDefaultParameterValue(); } catch (Throwable ignore) {}
      if (dv != null) dto.defaultValue = safeValue(dv);

      if (def instanceof ChoiceParameterDefinition) {
        try {
          List<String> choices = ((ChoiceParameterDefinition) def).getChoices();
          dto.choices = new ArrayList<>(choices);
        } catch (Throwable ignore) {}
      }

      enrichDynamicChoicesByReflection(def, dto);

      out.add(dto);
    }
    return out;
  }

  @Exported
  public List<ParamDTO> getParameters() {
    return getEffectiveParameters();
  }

  // Direct JSON endpoint
  public void doApi(StaplerRequest req, StaplerResponse rsp) throws IOException {
    if (!job.hasPermission(Item.READ)) { 
      rsp.sendError(403); 
      return; 
    }
    List<ParamDTO> list = getEffectiveParameters();
    rsp.setContentType("application/json;charset=UTF-8");
    rsp.getWriter().write(ParamDTO.toJson(list));
  }

  private Object safeValue(ParameterValue pv) {
    try {
      if (pv instanceof StringParameterValue) return ((StringParameterValue) pv).getValue();
      if (pv instanceof BooleanParameterValue) return ((BooleanParameterValue) pv).getValue();
      if (pv instanceof TextParameterValue) return ((TextParameterValue) pv).getValue();
      if (pv instanceof PasswordParameterValue) return "******";
      Method getValue = pv.getClass().getMethod("getValue");
      return String.valueOf(getValue.invoke(pv));
    } catch (Throwable e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private void enrichDynamicChoicesByReflection(ParameterDefinition def, ParamDTO dto) {
    String cn = def.getClass().getName();
    try {
      if (cn.startsWith("org.biouno.unochoice.")) {
        try {
          Method m = def.getClass().getMethod("getChoices");
          Object r = m.invoke(def);
          dto.choices = normalizeChoices(r);
          if (!dto.choices.isEmpty()) return;
        } catch (NoSuchMethodException ignore) {}

        try {
          Method m = def.getClass().getMethod("getChoiceList");
          Object r = m.invoke(def);
          dto.choices = normalizeChoices(r);
          if (!dto.choices.isEmpty()) return;
        } catch (NoSuchMethodException ignore) {}
      }

      if (cn.equals("com.cwctravel.hudson.plugins.extended__choice__parameter.ExtendedChoiceParameterDefinition")) {
        try {
          Method m = def.getClass().getMethod("getChoices");
          Object r = m.invoke(def);
          dto.choices = normalizeChoices(r);
          if (!dto.choices.isEmpty()) return;
        } catch (NoSuchMethodException ignore) {}

        try {
          Method m = def.getClass().getMethod("getValue");
          Object r = m.invoke(def);
          if (r != null) {
            String csv = String.valueOf(r);
            dto.choices = Arrays.stream(csv.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
          }
        } catch (NoSuchMethodException ignore) {}
      }
    } catch (Throwable ignored) {
    }
  }

  private List<String> normalizeChoices(Object r) {
    if (r == null) return Collections.emptyList();
    if (r instanceof List) {
      return ((List<?>) r).stream().map(String::valueOf).collect(Collectors.toList());
    }
    if (r instanceof Map) {
      return ((Map<?,?>) r).values().stream().map(String::valueOf).collect(Collectors.toList());
    }
    if (r instanceof ListBoxModel) {
      List<String> list = new ArrayList<>();
      for (ListBoxModel.Option opt : (ListBoxModel) r) list.add(String.valueOf(opt.name));
      return list;
    }
    return Collections.singletonList(String.valueOf(r));
  }

  @ExportedBean
  public static class ParamDTO {
    @Exported public String name;
    @Exported public String type;
    @Exported public String description;
    @Exported public Object defaultValue;
    @Exported public List<String> choices = new ArrayList<>();

    public static String toJson(List<ParamDTO> list) {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"parameters\":[");
      for (int i=0;i<list.size();i++) {
        ParamDTO d = list.get(i);
        sb.append("{")
          .append("\"name\":").append(json(d.name)).append(",")
          .append("\"type\":").append(json(d.type)).append(",")
          .append("\"description\":").append(json(d.description)).append(",")
          .append("\"defaultValue\":").append(jsonObj(d.defaultValue)).append(",")
          .append("\"choices\":").append(jsonArray(d.choices))
          .append("}");
        if (i < list.size()-1) sb.append(",");
      }
      sb.append("]}");
      return sb.toString();
    }
    private static String json(String s){ return s==null?"null":"\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""; }
    private static String jsonObj(Object o){ return o==null?"null":json(String.valueOf(o)); }
    private static String jsonArray(List<String> arr){
      if (arr==null) return "[]";
      StringBuilder sb = new StringBuilder("["); 
      for (int i=0;i<arr.size();i++){ sb.append(json(arr.get(i))); if (i<arr.size()-1) sb.append(","); }
      sb.append("]"); 
      return sb.toString();
    }
  }
}
