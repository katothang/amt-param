package io.kanbanai.paramsview;

import hudson.Extension;
import hudson.model.*;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

// Active Choices imports
import org.biouno.unochoice.AbstractScriptableParameter;
import org.biouno.unochoice.ChoiceParameter;
import org.biouno.unochoice.CascadeChoiceParameter;
import org.biouno.unochoice.DynamicReferenceParameter;
import org.biouno.unochoice.model.Script;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * Jenkins Plugin API để lấy thông tin parameters của job
 * 
 * Plugin này cung cấp REST API để lấy thông tin đầy đủ về các parameters của một job,
 * giống như màn "Build with Parameters" của Jenkins UI.
 * 
 * Các tính năng chính:
 * - Lấy tất cả parameters của job (built-in và Active Choices)
 * - Render dynamic parameters với giá trị thực tế (sử dụng thư viện Active Choices trực tiếp)
 * - Xử lý cascade parameters (parameters phụ thuộc vào nhau)
 * - Hỗ trợ tất cả loại parameter: String, Boolean, Choice, Text, Password, Active Choices, etc.
 * 
 * Sử dụng thư viện Active Choices chính thức để đảm bảo tính chính xác 100%
 * 
 * @author KanbanAI
 * @since 1.0
 */
@Extension
public class RenderedParametersApi implements RootAction {

    @Override
    public String getIconFileName() {
        // Trả về null để không hiển thị trong menu sidebar
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Rendered Parameters API";
    }

    @Override
    public String getUrlName() {
        // URL endpoint: /amt-param
        return "amt-param";
    }

    /**
     * REST API endpoint để lấy thông tin parameters của một job
     * 
     * Cách sử dụng:
     * GET /amt-param/get?job=https://jenkins.thangnotes.dev/job/amt_clone1/&params=param1:value1,param2:value2
     * 
     * @param req StaplerRequest chứa parameters
     * @param rsp StaplerResponse để trả về kết quả JSON
     * @throws IOException Nếu có lỗi I/O
     * @throws ServletException Nếu có lỗi servlet
     */
    public void doGet(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // Lấy job URL từ query parameter
        String jobUrl = req.getParameter("job");
        if (jobUrl == null || jobUrl.isEmpty()) {
            rsp.sendError(400, "Job parameter is required (e.g., https://jenkins.example.com/job/jobName/)");
            return;
        }

        // Parse job name từ URL
        String jobName = extractJobNameFromUrl(jobUrl);
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "Invalid job URL format. Expected format: https://jenkins.example.com/job/jobName/");
            return;
        }

        // Tìm job trong Jenkins
        Jenkins jenkins = Jenkins.get();
        Job<?, ?> job = jenkins.getItemByFullName(jobName, Job.class);
        
        if (job == null) {
            rsp.sendError(404, "Job not found: " + jobName);
            return;
        }

        // Kiểm tra quyền READ của user đối với job
        try {
            job.checkPermission(Item.READ);
        } catch (Exception e) {
            rsp.sendError(403, "Access denied to job: " + jobName);
            return;
        }

        // Parse parameter values từ query string (cho cascade parameters)
        Map<String, String> currentValues = parseParameterValues(req.getParameter("params"));

        // Lấy thông tin đầy đủ của tất cả parameters
        RenderedParametersInfo info = getRenderedParameters(job, currentValues);
        
        // Trả về JSON response
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.getWriter().write(info.toJson());
    }

    /**
     * Extract job name từ Jenkins URL
     * Format: https://jenkins.thangnotes.dev/job/amt_clone1/ -> amt_clone1
     * Format: https://jenkins.thangnotes.dev/job/folder/job/jobName/ -> folder/jobName
     * 
     * @param jobUrl Jenkins job URL
     * @return Job name hoặc null nếu URL không hợp lệ
     */
    private String extractJobNameFromUrl(String jobUrl) {
        if (jobUrl == null || jobUrl.isEmpty()) {
            return null;
        }
        
        try {
            // Remove trailing slash
            String url = jobUrl.trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            
            // Parse job name từ URL pattern: .../job/name/job/name/...
            // Ví dụ: https://jenkins.example.com/job/folder/job/jobName -> folder/jobName
            String[] parts = url.split("/job/");
            if (parts.length < 2) {
                return null;
            }
            
            // Lấy tất cả các phần sau /job/ và join bằng /
            StringBuilder jobName = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    jobName.append("/");
                }
                jobName.append(parts[i]);
            }
            
            return jobName.toString();
            
        } catch (Exception e) {
            System.err.println("Lỗi khi parse job URL: " + jobUrl + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse parameter values từ query string
     * Format: "param1:value1,param2:value2,..."
     * 
     * @param paramsStr Query string chứa parameter values
     * @return Map<String, String> với key là tên parameter, value là giá trị
     */
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

    /**
     * Lấy thông tin đầy đủ của tất cả parameters trong một job
     * Tương tự màn "Build with Parameters" của Jenkins
     * 
     * @param job Job cần lấy parameters
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade parameters)
     * @return RenderedParametersInfo chứa thông tin đầy đủ của tất cả parameters
     */
    private RenderedParametersInfo getRenderedParameters(Job<?, ?> job, Map<String, String> currentValues) {
        RenderedParametersInfo info = new RenderedParametersInfo();
        info.jobName = job.getName();
        info.jobFullName = job.getFullName();
        info.jobUrl = job.getUrl();
        info.buildWithParametersUrl = job.getUrl() + "buildWithParameters";
        
        // Lấy ParametersDefinitionProperty - property chứa định nghĩa các parameters
        ParametersDefinitionProperty prop = job.getProperty(ParametersDefinitionProperty.class);
        if (prop != null) {
            // Duyệt qua từng parameter definition và render nó
            for (ParameterDefinition def : prop.getParameterDefinitions()) {
                RenderedParameterInfo param = renderParameter(def, job, currentValues);
                info.parameters.add(param);
            }
        }
        
        return info;
    }

    /**
     * Render một parameter definition thành thông tin đầy đủ để hiển thị
     * Xử lý tất cả các loại parameter: String, Boolean, Choice, Active Choices, etc.
     * 
     * @param def ParameterDefinition cần render
     * @param job Job chứa parameter
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade parameters)
     * @return RenderedParameterInfo chứa thông tin đầy đủ của parameter
     */
    private RenderedParameterInfo renderParameter(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        RenderedParameterInfo param = new RenderedParameterInfo();
        param.name = def.getName();
        param.type = def.getClass().getSimpleName();
        param.description = def.getDescription();
        
        // Lấy giá trị hiện tại: ưu tiên từ currentValues, nếu không có thì lấy default value
        String currentValue = currentValues.get(def.getName());
        if (currentValue == null) {
            try {
                ParameterValue defaultValue = def.getDefaultParameterValue();
                if (defaultValue != null) {
                    currentValue = getParameterValueAsString(defaultValue);
                }
            } catch (Exception e) {
                // Không có default value
            }
        }
        param.currentValue = currentValue;
        
        String className = def.getClass().getName();
        
        // Xử lý các loại parameter built-in của Jenkins
        if (def instanceof ChoiceParameterDefinition) {
            // Choice Parameter: dropdown với các lựa chọn cố định
            ChoiceParameterDefinition choiceDef = (ChoiceParameterDefinition) def;
            param.choices = new ArrayList<>(choiceDef.getChoices());
            param.inputType = "select";
        } else if (def instanceof BooleanParameterDefinition) {
            // Boolean Parameter: checkbox true/false
            param.choices = Arrays.asList("true", "false");
            param.inputType = "checkbox";
        } else if (def instanceof TextParameterDefinition) {
            // Text Parameter: textarea cho text nhiều dòng
            param.inputType = "textarea";
        } else if (def instanceof PasswordParameterDefinition) {
            // Password Parameter: input password
            param.inputType = "password";
        } else if (def instanceof StringParameterDefinition) {
            // String Parameter: input text đơn giản
            param.inputType = "text";
        } else {
            // Xử lý Active Choices Plugin parameters (dynamic parameters) thông qua Jenkins API động
            param.choices = getActiveChoicesViaJenkinsAPI(def, currentValues);
            param.isDynamic = true;
            param.dependencies = getReferencedParametersViaJenkinsAPI(def);
            
            // Xác định input type dựa trên class name
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

    /**
     * Lấy các choices từ Active Choices parameter sử dụng thư viện trực tiếp
     * 
     * @param def ParameterDefinition
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade parameters)
     * @return List các choices đã được render
     */
    private List<String> getActiveChoicesViaJenkinsAPI(ParameterDefinition def, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Kiểm tra xem parameter có phải là Active Choices type không
            if (def instanceof AbstractScriptableParameter) {
                AbstractScriptableParameter scriptableParam = (AbstractScriptableParameter) def;
                Script script = scriptableParam.getScript();
                
                if (script != null) {
                    // Gọi script.eval() trực tiếp từ thư viện
                    // Script có thể trả về nhiều kiểu: List, Map, Array, String, ListBoxModel
                    Object scriptResult = script.eval(currentValues);
                    
                    // Normalize kết quả về List<String>
                    choices = normalizeChoicesResult(scriptResult);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy Active Choices values cho parameter: " + def.getName() + " - " + e.getMessage());
            // Fallback: thử cách khác nếu không lấy được qua Script
            choices = getActiveChoicesFallback(def, currentValues);
        }
        
        return choices;
    }

    /**
     * Fallback method để lấy choices khi method chính thất bại
     * Thử nhiều cách khác nhau để lấy choices từ Active Choices parameter
     * 
     * @param def ParameterDefinition
     * @param currentValues Map các giá trị parameter hiện tại
     * @return List các choices
     */
    private List<String> getActiveChoicesFallback(ParameterDefinition def, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Thử 1: Gọi method getChoices(Map) - dành cho CascadeChoiceParameter
            try {
                java.lang.reflect.Method getChoicesMethod = def.getClass().getMethod("getChoices", Map.class);
                Object result = getChoicesMethod.invoke(def, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại, thử cách khác
            }
            
            // Thử 2: Gọi method getChoices() không tham số
            try {
                java.lang.reflect.Method getChoicesMethod = def.getClass().getMethod("getChoices");
                Object result = getChoicesMethod.invoke(def);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
            // Thử 3: Gọi method getChoicesForUI() - UI method của Active Choices
            try {
                java.lang.reflect.Method getChoicesForUIMethod = def.getClass().getMethod("getChoicesForUI");
                Object result = getChoicesForUIMethod.invoke(def);
                choices = normalizeChoicesResult(result);
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi fallback khi lấy Active Choices values: " + e.getMessage());
        }
        
        return choices;
    }

    /**
     * Lấy referenced parameters (dependencies) sử dụng thư viện trực tiếp
     * 
     * @param def ParameterDefinition
     * @return List tên các parameters được reference
     */
    private List<String> getReferencedParametersViaJenkinsAPI(ParameterDefinition def) {
        List<String> dependencies = new ArrayList<>();
        
        try {
            // Kiểm tra xem parameter có phải là Cascade hoặc Dynamic Reference type
            if (def instanceof CascadeChoiceParameter) {
                CascadeChoiceParameter cascadeParam = (CascadeChoiceParameter) def;
                String referencedParams = cascadeParam.getReferencedParameters();
                
                if (referencedParams != null && !referencedParams.isEmpty()) {
                    String[] params = referencedParams.split(",");
                    for (String param : params) {
                        String trimmed = param.trim();
                        if (!trimmed.isEmpty()) {
                            dependencies.add(trimmed);
                        }
                    }
                }
            } else if (def instanceof DynamicReferenceParameter) {
                DynamicReferenceParameter dynamicParam = (DynamicReferenceParameter) def;
                String referencedParams = dynamicParam.getReferencedParameters();
                
                if (referencedParams != null && !referencedParams.isEmpty()) {
                    String[] params = referencedParams.split(",");
                    for (String param : params) {
                        String trimmed = param.trim();
                        if (!trimmed.isEmpty()) {
                            dependencies.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy referenced parameters: " + e.getMessage());
        }
        
        return dependencies;
    }

    /**
     * Lấy danh sách các parameter mà parameter hiện tại phụ thuộc vào (dependencies)
     * Dùng cho Cascade Choice Parameter hoặc Dynamic Reference Parameter
     * DEPRECATED: Sử dụng getReferencedParametersViaJenkinsAPI() thay thế
     * 
     * @param def ParameterDefinition
     * @return List tên các parameter mà parameter này phụ thuộc vào
     * @deprecated Use {@link #getReferencedParametersViaJenkinsAPI(ParameterDefinition)} instead
     */
    @Deprecated
    private List<String> getDependencies(ParameterDefinition def) {
        List<String> dependencies = new ArrayList<>();
        
        try {
            // Bước 1: Thử lấy referenced parameters (cho Active Choices Cascade Parameter)
            try {
                java.lang.reflect.Method getReferencedParametersMethod = def.getClass().getMethod("getReferencedParameters");
                Object result = getReferencedParametersMethod.invoke(def);
                if (result != null) {
                    if (result instanceof String) {
                        // Nếu là String, split bằng dấu phẩy
                        String[] deps = result.toString().split(",");
                        for (String dep : deps) {
                            String trimmed = dep.trim();
                            if (!trimmed.isEmpty()) {
                                dependencies.add(trimmed);
                            }
                        }
                    } else if (result instanceof java.util.Collection) {
                        // Nếu là Collection, lấy từng item
                        for (Object item : (java.util.Collection<?>) result) {
                            if (item != null) {
                                dependencies.add(item.toString());
                            }
                        }
                    } else if (result.getClass().isArray()) {
                        // Nếu là Array, convert sang List
                        Object[] arr = (Object[]) result;
                        for (Object item : arr) {
                            if (item != null) {
                                dependencies.add(item.toString());
                            }
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
            // Bước 2: Thử lấy filter parameters (một số plugin khác dùng tên này)
            if (dependencies.isEmpty()) {
                try {
                    java.lang.reflect.Method getFilterMethod = def.getClass().getMethod("getFilterParameters");
                    Object result = getFilterMethod.invoke(def);
                    if (result != null && result instanceof String) {
                        String[] deps = result.toString().split(",");
                        for (String dep : deps) {
                            String trimmed = dep.trim();
                            if (!trimmed.isEmpty()) {
                                dependencies.add(trimmed);
                            }
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // Method không tồn tại
                }
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy dependencies của parameter: " + def.getName() + " - " + e.getMessage());
        }
        
        return dependencies;
    }

    /**
     * Chuẩn hóa kết quả choices từ nhiều định dạng khác nhau về List<String>
     * Jenkins API có thể trả về nhiều kiểu dữ liệu: List, Map, ListBoxModel, Array, String...
     * Method này xử lý tất cả các trường hợp để đảm bảo tương thích
     * 
     * @param scriptResult Kết quả từ Groovy script (có thể là nhiều kiểu)
     * @return List<String> đã được chuẩn hóa
     */
    private List<String> normalizeChoicesResult(Object scriptResult) {
        if (scriptResult == null) {
            return new ArrayList<>();
        }
        
        // Xử lý theo từng kiểu cụ thể
        if (scriptResult instanceof List) {
            return normalizeFromList((List<?>) scriptResult);
        } else if (scriptResult instanceof Map) {
            return normalizeFromMap((Map<?, ?>) scriptResult);
        } else if (scriptResult instanceof Collection) {
            return normalizeFromCollection((Collection<?>) scriptResult);
        } else if (scriptResult.getClass().isArray()) {
            return normalizeFromArray(scriptResult);
        } else if (scriptResult.getClass().getName().contains("ListBoxModel")) {
            return normalizeFromListBoxModel(scriptResult);
        } else if (scriptResult instanceof String) {
            // Nếu là String đơn, wrap thành List
            List<String> result = new ArrayList<>();
            result.add((String) scriptResult);
            return result;
        } else {
            // Fallback: convert toString()
            List<String> result = new ArrayList<>();
            result.add(scriptResult.toString());
            return result;
        }
    }
    
    /**
     * Chuẩn hóa từ List
     */
    private List<String> normalizeFromList(List<?> list) {
        List<String> choices = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                choices.add(item.toString());
            }
        }
        return choices;
    }
    
    /**
     * Chuẩn hóa từ Map - lấy values, nếu không có thì lấy keys
     */
    private List<String> normalizeFromMap(Map<?, ?> map) {
        List<String> choices = new ArrayList<>();
        if (map.isEmpty()) {
            return choices;
        }
        
        // Ưu tiên lấy values
        for (Object value : map.values()) {
            if (value != null) {
                choices.add(value.toString());
            }
        }
        
        // Nếu values toàn null, thử lấy keys
        if (choices.isEmpty()) {
            for (Object key : map.keySet()) {
                if (key != null) {
                    choices.add(key.toString());
                }
            }
        }
        
        return choices;
    }
    
    /**
     * Chuẩn hóa từ Collection khác (Set, etc.)
     */
    private List<String> normalizeFromCollection(Collection<?> collection) {
        List<String> choices = new ArrayList<>();
        for (Object item : collection) {
            if (item != null) {
                choices.add(item.toString());
            }
        }
        return choices;
    }
    
    /**
     * Chuẩn hóa từ Array
     */
    private List<String> normalizeFromArray(Object arrayResult) {
        List<String> choices = new ArrayList<>();
        int length = java.lang.reflect.Array.getLength(arrayResult);
        for (int i = 0; i < length; i++) {
            Object item = java.lang.reflect.Array.get(arrayResult, i);
            if (item != null) {
                choices.add(item.toString());
            }
        }
        return choices;
    }
    
    /**
     * Chuẩn hóa từ Jenkins ListBoxModel
     * ListBoxModel là UI component của Jenkins chứa các Option
     */
    private List<String> normalizeFromListBoxModel(Object listBoxModel) {
        List<String> choices = new ArrayList<>();
        
        try {
            // ListBoxModel có iterator để duyệt qua các Option
            java.lang.reflect.Method iteratorMethod = listBoxModel.getClass().getMethod("iterator");
            java.util.Iterator<?> iterator = (java.util.Iterator<?>) iteratorMethod.invoke(listBoxModel);
            
            while (iterator.hasNext()) {
                Object option = iterator.next();
                String optionValue = extractOptionValue(option);
                if (optionValue != null) {
                    choices.add(optionValue);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi xử lý ListBoxModel: " + e.getMessage());
        }
        
        return choices;
    }
    
    /**
     * Lấy giá trị từ một Option của ListBoxModel
     * Option có method name() và value()
     */
    private String extractOptionValue(Object option) {
        try {
            // Thử lấy name() trước
            java.lang.reflect.Method getNameMethod = option.getClass().getMethod("name");
            Object name = getNameMethod.invoke(option);
            if (name != null) {
                return name.toString();
            }
        } catch (Exception e) {
            // Không có name(), thử value()
        }
        
        try {
            // Thử lấy value()
            java.lang.reflect.Method getValueMethod = option.getClass().getMethod("value");
            Object value = getValueMethod.invoke(option);
            if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
            // Không có value()
        }
        
        // Fallback: toString()
        return option.toString();
    }

    /**
     * Chuyển đổi ParameterValue về String để hiển thị
     * Xử lý tất cả các loại parameter value của Jenkins
     * 
     * @param pv ParameterValue từ Jenkins
     * @return String representation của value
     */
    private String getParameterValueAsString(ParameterValue pv) {
        try {
            // Xử lý từng loại ParameterValue cụ thể
            if (pv instanceof StringParameterValue) {
                return ((StringParameterValue) pv).getValue();
            }
            if (pv instanceof BooleanParameterValue) {
                return String.valueOf(((BooleanParameterValue) pv).getValue());
            }
            if (pv instanceof TextParameterValue) {
                return ((TextParameterValue) pv).getValue();
            }
            if (pv instanceof PasswordParameterValue) {
                // Không trả về password value vì lý do bảo mật
                return "";
            }
            // Các loại khác: FileParameterValue, RunParameterValue, etc.
            return String.valueOf(pv.getValue());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Class chứa thông tin đầy đủ về tất cả parameters của một job
     * Bao gồm thông tin job và danh sách các parameters đã được render
     */
    public static class RenderedParametersInfo {
        public String jobName;              // Tên job
        public String jobFullName;          // Tên đầy đủ của job (bao gồm folder path)
        public String jobUrl;               // URL của job
        public String buildWithParametersUrl; // URL để trigger build với parameters
        public List<RenderedParameterInfo> parameters = new ArrayList<>(); // Danh sách các parameters
        
        /**
         * Chuyển đổi object thành JSON string
         * @return JSON string representation
         */
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
        
        /**
         * Escape string cho JSON
         */
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
    }

    /**
     * Class chứa thông tin chi tiết của một parameter đã được render
     * Bao gồm tất cả thông tin cần thiết để hiển thị parameter trên UI
     */
    public static class RenderedParameterInfo {
        public String name;                  // Tên parameter
        public String type;                  // Loại parameter (class name)
        public String description;           // Mô tả parameter
        public String currentValue;          // Giá trị hiện tại của parameter
        public String inputType;             // Loại input (text, select, checkbox, etc.)
        public List<String> choices = new ArrayList<>();      // Danh sách các lựa chọn (cho dropdown)
        public List<String> dependencies = new ArrayList<>(); // Danh sách các parameter mà parameter này phụ thuộc vào
        public boolean isDynamic = false;    // True nếu là dynamic parameter (Active Choices)
        
        /**
         * Chuyển đổi object thành JSON string
         * @return JSON string representation
         */
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
        
        /**
         * Escape string cho JSON
         */
        private String json(String s) { 
            return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; 
        }
        
        /**
         * Chuyển đổi List<String> thành JSON array
         */
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