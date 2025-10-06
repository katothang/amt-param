package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

@Extension
public class RenderedParametersApi implements RootAction {

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Rendered Parameters API";
    }

    @Override
    public String getUrlName() {
        return "amt-param";
    }

    // GET /rendered-params/get?job=jobName&params=param1:value1,param2:value2
    public void doGet(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "Job parameter is required");
            return;
        }

        Jenkins jenkins = Jenkins.get();
        Job<?, ?> job = jenkins.getItemByFullName(jobName, Job.class);
        
        if (job == null) {
            rsp.sendError(404, "Job not found: " + jobName);
            return;
        }

        try {
            job.checkPermission(Item.READ);
        } catch (Exception e) {
            rsp.sendError(403, "Access denied to job: " + jobName);
            return;
        }

        // Parse existing parameter values for cascade/dependent parameters
        Map<String, String> currentValues = parseParameterValues(req.getParameter("params"));

        RenderedParametersInfo info = getRenderedParameters(job, currentValues);
        
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(info.toJson());
    }

    private Map<String, String> parseParameterValues(String paramsStr) {
        Map<String, String> values = new HashMap<>();
        if (paramsStr != null && !paramsStr.isEmpty()) {
            String[] pairs = paramsStr.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    values.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return values;
    }

    private RenderedParametersInfo getRenderedParameters(Job<?, ?> job, Map<String, String> currentValues) {
        RenderedParametersInfo info = new RenderedParametersInfo();
        info.jobName = job.getName();
        info.jobFullName = job.getFullName();
        info.jobUrl = job.getUrl();
        info.buildWithParametersUrl = job.getUrl() + "buildWithParameters";
        
        ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
        if (prop != null) {
            for (ParameterDefinition def : prop.getParameterDefinitions()) {
                RenderedParameterInfo param = renderParameter(def, job, currentValues);
                info.parameters.add(param);
            }
        }
        
        return info;
    }

    private RenderedParameterInfo renderParameter(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        RenderedParameterInfo param = new RenderedParameterInfo();
        param.name = def.getName();
        param.type = def.getClass().getSimpleName();
        param.description = def.getDescription();
        
        // Get current value (either from currentValues or default)
        String currentValue = currentValues.get(def.getName());
        if (currentValue == null) {
            try {
                ParameterValue defaultValue = def.getDefaultParameterValue();
                if (defaultValue != null) {
                    currentValue = getParameterValueAsString(defaultValue);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        param.currentValue = currentValue;
        
        String className = def.getClass().getName();
        
        if (def instanceof ChoiceParameterDefinition) {
            ChoiceParameterDefinition choiceDef = (ChoiceParameterDefinition) def;
            param.choices = new ArrayList<>(choiceDef.getChoices());
            param.inputType = "select";
        } else if (def instanceof BooleanParameterDefinition) {
            param.choices = Arrays.asList("true", "false");
            param.inputType = "checkbox";
        } else if (def instanceof TextParameterDefinition) {
            param.inputType = "textarea";
        } else if (def instanceof PasswordParameterDefinition) {
            param.inputType = "password";
        } else if (def instanceof StringParameterDefinition) {
            param.inputType = "text";
        } else {
            // Handle Active Choices Plugin parameters
            param.choices = getRenderedChoices(def, job, currentValues);
            param.isDynamic = true;
            param.dependencies = getDependencies(def);
            
            if (className.contains("ChoiceParameter")) {
                param.inputType = "select";
            } else if (className.contains("CascadeChoiceParameter")) {
                param.inputType = "cascade_select";
            } else if (className.contains("DynamicReferenceParameter")) {
                param.inputType = "dynamic_reference";
            } else {
                param.inputType = "text";
            }
        }
        
        return param;
    }

    private List<String> getRenderedChoices(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        String className = def.getClass().getName();
        
        try {
            if (className.contains("org.biouno.unochoice")) {
                // Try to render choices with current parameter values
                choices = renderActiveChoicesParameter(def, job, currentValues);
            }
        } catch (Exception e) {
            System.err.println("Error rendering choices for parameter: " + def.getName() + " - " + e.getMessage());
        }
        
        return choices;
    }

    private List<String> renderActiveChoicesParameter(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Try to get the choice provider and render it
            Object choiceProvider = getChoiceProvider(def);
            if (choiceProvider != null) {
                choices = renderChoiceProvider(choiceProvider, job, currentValues);
            }
            
            // If that fails, try direct script evaluation
            if (choices.isEmpty()) {
                choices = evaluateChoiceScript(def, currentValues);
            }
            
            // Fallback to static choices
            if (choices.isEmpty()) {
                try {
                    java.lang.reflect.Method getChoicesMethod = def.getClass().getMethod("getChoices");
                    Object result = getChoicesMethod.invoke(def);
                    choices = normalizeChoicesResult(result);
                } catch (Exception e) {
                    // Ignore
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error in renderActiveChoicesParameter: " + e.getMessage());
        }
        
        return choices;
    }

    private Object getChoiceProvider(ParameterDefinition def) {
        try {
            java.lang.reflect.Method getChoiceProviderMethod = def.getClass().getMethod("getChoiceProvider");
            return getChoiceProviderMethod.invoke(def);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> renderChoiceProvider(Object choiceProvider, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Try to call getChoices with parameters
            Class<?> providerClass = choiceProvider.getClass();
            
            // Look for getChoices method with different signatures
            try {
                java.lang.reflect.Method getChoicesMethod = providerClass.getMethod("getChoices", Map.class);
                Object result = getChoicesMethod.invoke(choiceProvider, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) return choices;
            } catch (Exception e) {
                // Try without parameters
            }
            
            try {
                java.lang.reflect.Method getChoicesMethod = providerClass.getMethod("getChoices");
                Object result = getChoicesMethod.invoke(choiceProvider);
                choices = normalizeChoicesResult(result);
            } catch (Exception e) {
                // Ignore
            }
            
        } catch (Exception e) {
            System.err.println("Error rendering choice provider: " + e.getMessage());
        }
        
        return choices;
    }

    private List<String> evaluateChoiceScript(ParameterDefinition def, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Get the script
            java.lang.reflect.Method getScriptMethod = def.getClass().getMethod("getScript");
            Object script = getScriptMethod.invoke(def);
            
            if (script != null) {
                String scriptStr = script.toString();
                
                // Simple script evaluation for common patterns
                if (scriptStr.contains("return[") || scriptStr.contains("return [")) {
                    choices = extractChoicesFromScript(scriptStr, currentValues);
                }
            }
            
        } catch (Exception e) {
            // Ignore
        }
        
        return choices;
    }

    private List<String> extractChoicesFromScript(String script, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Replace parameter references with actual values
            String processedScript = script;
            for (Map.Entry<String, String> entry : currentValues.entrySet()) {
                processedScript = processedScript.replaceAll("\\$" + entry.getKey(), "'" + entry.getValue() + "'");
                processedScript = processedScript.replaceAll("\\$\\{" + entry.getKey() + "\\}", "'" + entry.getValue() + "'");
            }
            
            // Extract choices from return statement
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("return\\s*\\[([^\\]]+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(processedScript);
            if (matcher.find()) {
                String choicesStr = matcher.group(1);
                String[] parts = choicesStr.split(",");
                for (String part : parts) {
                    String clean = part.trim().replaceAll("^['\"]|['\"]$", "");
                    if (!clean.isEmpty()) {
                        choices.add(clean);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return choices;
    }

    private List<String> getDependencies(ParameterDefinition def) {
        List<String> dependencies = new ArrayList<>();
        
        try {
            // Try to get referenced parameters for cascade parameters
            java.lang.reflect.Method getReferencedParametersMethod = def.getClass().getMethod("getReferencedParameters");
            Object result = getReferencedParametersMethod.invoke(def);
            if (result != null) {
                if (result instanceof String) {
                    String[] deps = result.toString().split(",");
                    for (String dep : deps) {
                        dependencies.add(dep.trim());
                    }
                } else if (result instanceof java.util.Collection) {
                    for (Object item : (java.util.Collection<?>) result) {
                        dependencies.add(item.toString());
                    }
                }
            }
        } catch (Exception e) {
            // No dependencies or method not available
        }
        
        return dependencies;
    }

    private List<String> normalizeChoicesResult(Object result) {
        List<String> choices = new ArrayList<>();
        if (result == null) return choices;
        
        if (result instanceof java.util.List) {
            for (Object item : (java.util.List<?>) result) {
                if (item != null) {
                    choices.add(item.toString());
                }
            }
        } else if (result instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
            for (Object value : map.values()) {
                if (value != null) {
                    choices.add(value.toString());
                }
            }
        } else if (result.getClass().getName().contains("ListBoxModel")) {
            // Handle Jenkins ListBoxModel
            try {
                java.lang.reflect.Method iteratorMethod = result.getClass().getMethod("iterator");
                java.util.Iterator<?> iterator = (java.util.Iterator<?>) iteratorMethod.invoke(result);
                while (iterator.hasNext()) {
                    Object option = iterator.next();
                    java.lang.reflect.Method getNameMethod = option.getClass().getMethod("name");
                    Object name = getNameMethod.invoke(option);
                    if (name != null) {
                        choices.add(name.toString());
                    }
                }
            } catch (Exception e) {
                // Failed to process ListBoxModel
            }
        } else {
            choices.add(result.toString());
        }
        
        return choices;
    }

    private String getParameterValueAsString(ParameterValue pv) {
        try {
            if (pv instanceof StringParameterValue) return ((StringParameterValue) pv).getValue();
            if (pv instanceof BooleanParameterValue) return String.valueOf(((BooleanParameterValue) pv).getValue());
            if (pv instanceof TextParameterValue) return ((TextParameterValue) pv).getValue();
            if (pv instanceof PasswordParameterValue) return "";
            return String.valueOf(pv.getValue());
        } catch (Exception e) {
            return "";
        }
    }

    public static class RenderedParametersInfo {
        public String jobName;
        public String jobFullName;
        public String jobUrl;
        public String buildWithParametersUrl;
        public List<RenderedParameterInfo> parameters = new ArrayList<>();
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"jobName\":").append(json(jobName)).append(",");
            sb.append("\"jobFullName\":").append(json(jobFullName)).append(",");
            sb.append("\"jobUrl\":").append(json(jobUrl)).append(",");
            sb.append("\"buildWithParametersUrl\":").append(json(buildWithParametersUrl)).append(",");
            sb.append("\"parameters\":[");
            
            for (int i = 0; i < parameters.size(); i++) {
                sb.append(parameters.get(i).toJsonString());
                if (i < parameters.size() - 1) sb.append(",");
            }
            
            sb.append("]}");
            return sb.toString();
        }
        
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
    }

    public static class RenderedParameterInfo {
        public String name;
        public String type;
        public String description;
        public String currentValue;
        public String inputType;
        public List<String> choices = new ArrayList<>();
        public List<String> dependencies = new ArrayList<>();
        public boolean isDynamic = false;
        
        public String toJsonString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":").append(json(name)).append(",");
            sb.append("\"type\":").append(json(type)).append(",");
            sb.append("\"description\":").append(json(description)).append(",");
            sb.append("\"currentValue\":").append(json(currentValue)).append(",");
            sb.append("\"inputType\":").append(json(inputType)).append(",");
            sb.append("\"isDynamic\":").append(isDynamic).append(",");
            sb.append("\"dependencies\":").append(jsonArray(dependencies)).append(",");
            sb.append("\"choices\":").append(jsonArray(choices));
            sb.append("}");
            return sb.toString();
        }
        
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
        
        private String jsonArray(List<String> arr) {
            if (arr == null) return "[]";
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < arr.size(); i++) {
                sb.append(json(arr.get(i)));
                if (i < arr.size() - 1) sb.append(",");
            }
            sb.append("]");
            return sb.toString();
        }
    }
}