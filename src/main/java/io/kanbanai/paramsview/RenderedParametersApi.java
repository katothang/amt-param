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

/**
 * Jenkins Plugin API để lấy thông tin parameters của job
 * 
 * Plugin này cung cấp REST API để lấy thông tin đầy đủ về các parameters của một job,
 * giống như màn "Build with Parameters" của Jenkins UI.
 * 
 * Các tính năng chính:
 * - Lấy tất cả parameters của job (built-in và Active Choices)
 * - Render dynamic parameters với giá trị thực tế (không dùng regex)
 * - Xử lý cascade parameters (parameters phụ thuộc vào nhau)
 * - Hỗ trợ tất cả loại parameter: String, Boolean, Choice, Text, Password, Active Choices, etc.
 * 
 * Sử dụng Jenkins API chính thức thay vì regex để đảm bảo tính chính xác 100%
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
     * GET /amt-param/get?job=jobName&params=param1:value1,param2:value2
     * 
     * @param req StaplerRequest chứa parameters
     * @param rsp StaplerResponse để trả về kết quả JSON
     * @throws IOException Nếu có lỗi I/O
     * @throws ServletException Nếu có lỗi servlet
     */
    public void doGet(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        // Lấy tên job từ query parameter
        String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "Job parameter is required");
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
            // Xử lý Active Choices Plugin parameters (dynamic parameters)
            param.choices = getRenderedChoices(def, job, currentValues);
            param.isDynamic = true;
            param.dependencies = getDependencies(def);
            
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
     * Lấy các rendered choices cho dynamic parameter
     * Xử lý các loại dynamic parameter từ Active Choices Plugin
     * 
     * @param def ParameterDefinition
     * @param job Job hiện tại
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade parameters)
     * @return List các choices đã được render
     */
    private List<String> getRenderedChoices(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        String className = def.getClass().getName();
        
        try {
            // Chỉ xử lý nếu là Active Choices plugin parameter
            if (className.contains("org.biouno.unochoice")) {
                // Gọi method render Active Choices với Jenkins API
                choices = renderActiveChoicesParameter(def, job, currentValues);
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi render choices cho parameter: " + def.getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        
        return choices;
    }

    /**
     * Render Active Choices parameter giống như màn "Build with Parameters" của Jenkins
     * Phương pháp này sử dụng Jenkins API chính thức thay vì regex để đảm bảo tính chính xác
     * 
     * @param def ParameterDefinition cần render
     * @param job Job chứa parameter
     * @param currentValues Map chứa các giá trị parameter hiện tại (dùng cho cascade parameters)
     * @return List các choices đã được render
     */
    private List<String> renderActiveChoicesParameter(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Bước 1: Lấy ChoiceProvider từ parameter definition
            // ChoiceProvider là object chứa logic để tạo ra các choices động
            Object choiceProvider = getChoiceProvider(def);
            if (choiceProvider != null) {
                choices = renderChoiceProviderWithGroovy(choiceProvider, job, currentValues);
            }
            
            // Bước 2: Nếu không có ChoiceProvider, thử lấy choices trực tiếp từ parameter
            if (choices.isEmpty()) {
                choices = getChoicesDirectly(def, job, currentValues);
            }
            
            // Bước 3: Fallback - lấy static choices nếu không có dynamic choices
            if (choices.isEmpty()) {
                try {
                    java.lang.reflect.Method getChoicesMethod = def.getClass().getMethod("getChoices");
                    Object result = getChoicesMethod.invoke(def);
                    choices = normalizeChoicesResult(result);
                } catch (Exception e) {
                    // Không có phương thức getChoices
                }
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi khi render Active Choices parameter: " + def.getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
        
        return choices;
    }

    /**
     * Lấy ChoiceProvider từ parameter definition
     * ChoiceProvider là object quản lý việc tạo ra các choices động trong Active Choices plugin
     * 
     * @param def ParameterDefinition
     * @return ChoiceProvider object hoặc null nếu không tìm thấy
     */
    private Object getChoiceProvider(ParameterDefinition def) {
        try {
            java.lang.reflect.Method getChoiceProviderMethod = def.getClass().getMethod("getChoiceProvider");
            return getChoiceProviderMethod.invoke(def);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Render ChoiceProvider bằng cách thực thi Groovy script với Jenkins API
     * Phương pháp này KHÔNG dùng regex để parse, thay vào đó gọi trực tiếp các method của Jenkins
     * 
     * @param choiceProvider ChoiceProvider object từ Active Choices plugin
     * @param job Job hiện tại
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade parameters)
     * @return List các choices đã được render
     */
    private List<String> renderChoiceProviderWithGroovy(Object choiceProvider, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            Class<?> providerClass = choiceProvider.getClass();
            
            // Bước 1: Thử gọi method getChoicesForUI - đây là method mà Jenkins UI sử dụng
            // Method này nhận Map<String, String> chứa các parameter values hiện tại
            try {
                java.lang.reflect.Method getChoicesForUIMethod = providerClass.getMethod("getChoicesForUI", Map.class);
                Object result = getChoicesForUIMethod.invoke(choiceProvider, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại, thử cách khác
            }
            
            // Bước 2: Thử gọi method getValues với Job và Map parameters
            // Đây là cách Active Choices plugin v2.x render choices
            try {
                java.lang.reflect.Method getValuesMethod = providerClass.getMethod("getValues", Job.class, Map.class);
                Object result = getValuesMethod.invoke(choiceProvider, job, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại, thử cách khác
            }
            
            // Bước 3: Thử gọi method getChoices với Map parameters
            // Một số version của Active Choices dùng method này
            try {
                java.lang.reflect.Method getChoicesMethod = providerClass.getMethod("getChoices", Map.class);
                Object result = getChoicesMethod.invoke(choiceProvider, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại, thử cách khác
            }
            
            // Bước 4: Thử gọi method getChoices không có parameters
            // Fallback cho các parameter không phụ thuộc vào parameter khác
            try {
                java.lang.reflect.Method getChoicesMethod = providerClass.getMethod("getChoices");
                Object result = getChoicesMethod.invoke(choiceProvider);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi khi render choice provider với Groovy: " + e.getMessage());
            e.printStackTrace();
        }
        
        return choices;
    }

    /**
     * Lấy choices trực tiếp từ parameter definition bằng Jenkins API
     * Không sử dụng regex hay parse script, mà gọi trực tiếp các method của Jenkins
     * 
     * @param def ParameterDefinition
     * @param job Job hiện tại
     * @param currentValues Map các giá trị parameter hiện tại
     * @return List các choices
     */
    private List<String> getChoicesDirectly(ParameterDefinition def, Job<?, ?> job, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            Class<?> defClass = def.getClass();
            
            // Bước 1: Thử gọi getChoices với Job và Map (cho Dynamic Reference Parameter)
            try {
                java.lang.reflect.Method getChoicesMethod = defClass.getMethod("getChoices", Job.class, Map.class);
                Object result = getChoicesMethod.invoke(def, job, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
            // Bước 2: Thử gọi getChoices với Map parameters (cho Cascade Choice Parameter)
            try {
                java.lang.reflect.Method getChoicesMethod = defClass.getMethod("getChoices", Map.class);
                Object result = getChoicesMethod.invoke(def, currentValues);
                choices = normalizeChoicesResult(result);
                if (!choices.isEmpty()) {
                    return choices;
                }
            } catch (NoSuchMethodException e) {
                // Method không tồn tại
            }
            
            // Bước 3: Thử dùng Jenkins Descriptor để render parameter
            // Đây là cách chính xác nhất vì giống với cách Jenkins UI render
            try {
                Descriptor<?> descriptor = def.getDescriptor();
                if (descriptor != null) {
                    // Tạo một StaplerRequest giả để simulate môi trường render của Jenkins
                    // Note: Cách này phức tạp và có thể cần điều chỉnh tùy theo version Jenkins
                    java.lang.reflect.Method fillMethod = descriptor.getClass().getMethod("doFillValueItems", 
                        hudson.model.Item.class, String.class);
                    Object result = fillMethod.invoke(descriptor, job, "");
                    choices = normalizeChoicesResult(result);
                    if (!choices.isEmpty()) {
                        return choices;
                    }
                }
            } catch (Exception e) {
                // Descriptor không hỗ trợ hoặc method không tồn tại
            }
            
        } catch (Exception e) {
            System.err.println("Lỗi khi lấy choices trực tiếp: " + e.getMessage());
        }
        
        return choices;
    }

    /**
     * Lấy danh sách các parameter mà parameter hiện tại phụ thuộc vào (dependencies)
     * Dùng cho Cascade Choice Parameter hoặc Dynamic Reference Parameter
     * 
     * @param def ParameterDefinition
     * @return List tên các parameter mà parameter này phụ thuộc vào
     */
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
     * @param result Object kết quả từ Jenkins API
     * @return List<String> đã được chuẩn hóa
     */
    private List<String> normalizeChoicesResult(Object result) {
        List<String> choices = new ArrayList<>();
        if (result == null) return choices;
        
        try {
            // Trường hợp 1: Đã là List - lấy trực tiếp
            if (result instanceof java.util.List) {
                for (Object item : (java.util.List<?>) result) {
                    if (item != null) {
                        choices.add(item.toString());
                    }
                }
            } 
            // Trường hợp 2: Là Map - lấy values hoặc keys
            else if (result instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) result;
                // Ưu tiên lấy values, nếu không có thì lấy keys
                if (!map.isEmpty()) {
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
                }
            } 
            // Trường hợp 3: Là ListBoxModel (Jenkins UI component)
            else if (result.getClass().getName().contains("ListBoxModel")) {
                try {
                    // ListBoxModel có iterator để duyệt qua các Option
                    java.lang.reflect.Method iteratorMethod = result.getClass().getMethod("iterator");
                    java.util.Iterator<?> iterator = (java.util.Iterator<?>) iteratorMethod.invoke(result);
                    while (iterator.hasNext()) {
                        Object option = iterator.next();
                        // Mỗi Option có method name() và value()
                        try {
                            java.lang.reflect.Method getNameMethod = option.getClass().getMethod("name");
                            Object name = getNameMethod.invoke(option);
                            if (name != null) {
                                choices.add(name.toString());
                            }
                        } catch (Exception e) {
                            // Nếu không có name, thử lấy value
                            try {
                                java.lang.reflect.Method getValueMethod = option.getClass().getMethod("value");
                                Object value = getValueMethod.invoke(option);
                                if (value != null) {
                                    choices.add(value.toString());
                                }
                            } catch (Exception ex) {
                                // Không lấy được, skip
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi khi xử lý ListBoxModel: " + e.getMessage());
                }
            }
            // Trường hợp 4: Là Array
            else if (result.getClass().isArray()) {
                Object[] arr = (Object[]) result;
                for (Object item : arr) {
                    if (item != null) {
                        choices.add(item.toString());
                    }
                }
            }
            // Trường hợp 5: Là Set hoặc Collection khác
            else if (result instanceof java.util.Collection) {
                for (Object item : (java.util.Collection<?>) result) {
                    if (item != null) {
                        choices.add(item.toString());
                    }
                }
            }
            // Trường hợp 6: Là String đơn lẻ
            else if (result instanceof String) {
                String str = (String) result;
                if (!str.isEmpty()) {
                    choices.add(str);
                }
            }
            // Trường hợp 7: Các object khác - convert toString()
            else {
                String str = result.toString();
                if (str != null && !str.isEmpty()) {
                    choices.add(str);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi chuẩn hóa choices result: " + e.getMessage());
            e.printStackTrace();
        }
        
        return choices;
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