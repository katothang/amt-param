package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.util.*;

@Extension
public class BuildWithParametersApi implements RootAction {

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Build With Parameters API";
    }

    @Override
    public String getUrlName() {
        return "build-params";
    }

    // Simple endpoint: GET /build-params/params?job=test
    public void doParams(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "Job parameter is required. Use: /build-params/params?job=jobName");
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

        BuildWithParametersInfo info = getBuildWithParametersInfo(job);
        
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(info.toJson());
    }

    private BuildWithParametersInfo getBuildWithParametersInfo(Job<?, ?> job) {
        BuildWithParametersInfo info = new BuildWithParametersInfo();
        info.jobName = job.getName();
        info.jobFullName = job.getFullName();
        info.jobUrl = job.getUrl();
        info.jobDisplayName = job.getDisplayName();
        info.buildUrl = job.getUrl() + "build";
        info.buildWithParametersUrl = job.getUrl() + "buildWithParameters";
        
        ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
        if (prop != null) {
            for (ParameterDefinition def : prop.getParameterDefinitions()) {
                ParameterInfo param = new ParameterInfo();
                param.name = def.getName();
                param.type = def.getClass().getSimpleName();
                param.description = def.getDescription();
                
                // Get default value
                try {
                    ParameterValue defaultValue = def.getDefaultParameterValue();
                    if (defaultValue != null) {
                        param.defaultValue = getParameterValue(defaultValue);
                    }
                } catch (Exception e) {
                    param.defaultValue = null;
                }
                
                // Get choices and additional info based on parameter type
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
                    // Handle dynamic parameters from Active Choices Plugin
                    param.choices = getDynamicChoices(def, job);
                    param.isDynamic = true;
                    
                    // Try to get script for reference
                    try {
                        java.lang.reflect.Method getScriptMethod = def.getClass().getMethod("getScript");
                        Object script = getScriptMethod.invoke(def);
                        if (script != null) {
                            param.script = script.toString();
                        }
                    } catch (Exception e) {
                        // Script not available
                    }
                    
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
                
                info.parameters.add(param);
            }
        }
        
        return info;
    }

    private Object getParameterValue(ParameterValue pv) {
        try {
            if (pv instanceof StringParameterValue) return ((StringParameterValue) pv).getValue();
            if (pv instanceof BooleanParameterValue) return ((BooleanParameterValue) pv).getValue();
            if (pv instanceof TextParameterValue) return ((TextParameterValue) pv).getValue();
            if (pv instanceof PasswordParameterValue) return ""; // Don't show password defaults
            return pv.getValue();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> getDynamicChoices(ParameterDefinition def, Job<?, ?> job) {
        List<String> choices = new ArrayList<>();
        String className = def.getClass().getName();
        
        try {
            // Handle Active Choices Plugin parameters
            if (className.contains("org.biouno.unochoice")) {
                // Try to get choices using reflection
                try {
                    // Try getChoices() method
                    java.lang.reflect.Method getChoicesMethod = def.getClass().getMethod("getChoices");
                    Object result = getChoicesMethod.invoke(def);
                    choices = normalizeChoicesResult(result);
                    if (!choices.isEmpty()) return choices;
                } catch (Exception e) {
                    // Method not found or failed, try other methods
                }

                try {
                    // Try getChoiceListAsString() method
                    java.lang.reflect.Method getChoiceListMethod = def.getClass().getMethod("getChoiceListAsString");
                    Object result = getChoiceListMethod.invoke(def);
                    if (result != null) {
                        String choicesStr = result.toString();
                        if (!choicesStr.isEmpty()) {
                            choices = Arrays.asList(choicesStr.split("\\r?\\n"));
                            return choices;
                        }
                    }
                } catch (Exception e) {
                    // Method not found or failed
                }

                try {
                    // Try to get script and evaluate it (simplified approach)
                    java.lang.reflect.Method getScriptMethod = def.getClass().getMethod("getScript");
                    Object script = getScriptMethod.invoke(def);
                    if (script != null) {
                        String scriptStr = script.toString();
                        // For simple return statements, try to extract values
                        if (scriptStr.contains("return[") || scriptStr.contains("return [")) {
                            choices = extractChoicesFromScript(scriptStr);
                        }
                    }
                } catch (Exception e) {
                    // Script method not available or failed
                }
            }
            
            // Handle Extended Choice Parameter Plugin
            if (className.contains("ExtendedChoiceParameterDefinition")) {
                try {
                    java.lang.reflect.Method getValueMethod = def.getClass().getMethod("getValue");
                    Object result = getValueMethod.invoke(def);
                    if (result != null) {
                        String valueStr = result.toString();
                        choices = Arrays.asList(valueStr.split(","));
                        return choices;
                    }
                } catch (Exception e) {
                    // Method failed
                }
            }
            
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Error getting dynamic choices for parameter: " + def.getName() + " - " + e.getMessage());
        }
        
        return choices;
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

    private List<String> extractChoicesFromScript(String script) {
        List<String> choices = new ArrayList<>();
        try {
            // Simple regex to extract values from return['value1', 'value2'] or return["value1", "value2"]
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("return\\s*\\[([^\\]]+)\\]");
            java.util.regex.Matcher matcher = pattern.matcher(script);
            if (matcher.find()) {
                String choicesStr = matcher.group(1);
                // Split by comma and clean up quotes
                String[] parts = choicesStr.split(",");
                for (String part : parts) {
                    String clean = part.trim().replaceAll("^['\"]|['\"]$", "");
                    if (!clean.isEmpty()) {
                        choices.add(clean);
                    }
                }
            }
        } catch (Exception e) {
            // Failed to extract from script
        }
        return choices;
    }

    public static class BuildWithParametersInfo {
        public String jobName;
        public String jobFullName;
        public String jobUrl;
        public String jobDisplayName;
        public String buildUrl;
        public String buildWithParametersUrl;
        public List<ParameterInfo> parameters = new ArrayList<>();
        
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"jobName\":").append(json(jobName)).append(",");
            sb.append("\"jobFullName\":").append(json(jobFullName)).append(",");
            sb.append("\"jobUrl\":").append(json(jobUrl)).append(",");
            sb.append("\"jobDisplayName\":").append(json(jobDisplayName)).append(",");
            sb.append("\"buildUrl\":").append(json(buildUrl)).append(",");
            sb.append("\"buildWithParametersUrl\":").append(json(buildWithParametersUrl)).append(",");
            sb.append("\"parameters\":[");
            
            for (int i = 0; i < parameters.size(); i++) {
                ParameterInfo p = parameters.get(i);
                sb.append(p.toJsonString());
                if (i < parameters.size() - 1) sb.append(",");
            }
            
            sb.append("]}");
            return sb.toString();
        }
        
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
        
        private String jsonObj(Object o) { 
            return o == null ? "null" : json(String.valueOf(o)); 
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

    public static class ParameterInfo {
        public String name;
        public String type;
        public String description;
        public Object defaultValue;
        public String inputType;
        public List<String> choices = new ArrayList<>();
        public String script; // For dynamic parameters
        public boolean isDynamic = false;
        
        public String toJsonString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"name\":").append(json(name)).append(",");
            sb.append("\"type\":").append(json(type)).append(",");
            sb.append("\"description\":").append(json(description)).append(",");
            sb.append("\"defaultValue\":").append(jsonObj(defaultValue)).append(",");
            sb.append("\"inputType\":").append(json(inputType)).append(",");
            sb.append("\"isDynamic\":").append(isDynamic).append(",");
            if (script != null) {
                sb.append("\"script\":").append(json(script)).append(",");
            }
            sb.append("\"choices\":").append(jsonArray(choices));
            sb.append("}");
            return sb.toString();
        }
        
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
        
        private String jsonObj(Object o) { 
            return o == null ? "null" : json(String.valueOf(o)); 
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