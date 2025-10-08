package io.kanbanai.paramsview.service;

import hudson.model.ParameterDefinition;
import io.kanbanai.paramsview.model.RenderedParameterInfo;
import io.kanbanai.paramsview.model.ParameterInputType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service chuyên xử lý Active Choices Plugin parameters
 * 
 * Service này được tách riêng để xử lý các tính năng của Active Choices Plugin
 * một cách an toàn, với khả năng graceful fallback khi plugin không khả dụng.
 * 
 * Tất cả các tương tác với Active Choices Plugin classes được thực hiện
 * thông qua reflection để tránh ClassNotFoundException khi plugin không có.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public class ActiveChoicesService {
    
    private static final Logger LOGGER = Logger.getLogger(ActiveChoicesService.class.getName());
    
    // Singleton instance
    private static ActiveChoicesService instance;
    
    // Active Choices class names (để sử dụng với reflection)
    private static final String ABSTRACT_SCRIPTABLE_PARAMETER = "org.biouno.unochoice.AbstractScriptableParameter";
    private static final String CHOICE_PARAMETER = "org.biouno.unochoice.ChoiceParameter";
    private static final String CASCADE_CHOICE_PARAMETER = "org.biouno.unochoice.CascadeChoiceParameter";
    private static final String DYNAMIC_REFERENCE_PARAMETER = "org.biouno.unochoice.DynamicReferenceParameter";
    private static final String SCRIPT_CLASS = "org.biouno.unochoice.model.Script";
    
    /**
     * Private constructor cho Singleton pattern
     */
    private ActiveChoicesService() {
    }
    
    /**
     * Lấy instance duy nhất của service (Singleton pattern)
     * 
     * @return ActiveChoicesService instance
     */
    public static synchronized ActiveChoicesService getInstance() {
        if (instance == null) {
            instance = new ActiveChoicesService();
        }
        return instance;
    }
    
    /**
     * Render Active Choices parameter thành RenderedParameterInfo
     * 
     * Method này sử dụng reflection để tương tác với Active Choices Plugin
     * một cách an toàn, không gây ra ClassNotFoundException khi plugin không có.
     * 
     * @param paramDef ParameterDefinition (phải là Active Choices type)
     * @param paramInfo RenderedParameterInfo để populate thông tin
     * @param currentValues Map các giá trị parameter hiện tại (cho cascade)
     */
    public void renderActiveChoicesParameter(ParameterDefinition paramDef, RenderedParameterInfo paramInfo, Map<String, String> currentValues) {
        if (paramDef == null || paramInfo == null) {
            throw new IllegalArgumentException("ParameterDefinition và RenderedParameterInfo không được null");
        }
        
        String className = paramDef.getClass().getName();
        LOGGER.log(Level.FINE, "Render Active Choices parameter: " + paramDef.getName() + " (class: " + className + ")");
        
        try {
            // Xác định loại Active Choices parameter và set input type
            if (className.contains("ChoiceParameter")) {
                paramInfo.setInputType(ParameterInputType.SELECT);
            } else if (className.contains("CascadeChoiceParameter")) {
                paramInfo.setInputType(ParameterInputType.CASCADE_SELECT);
            } else if (className.contains("DynamicReferenceParameter")) {
                paramInfo.setInputType(ParameterInputType.DYNAMIC_REFERENCE);
            }
            
            // Lấy choices từ script
            List<String> choices = getChoicesFromActiveChoicesParameter(paramDef, currentValues);
            paramInfo.setChoices(choices);
            
            // Lấy dependencies (referenced parameters)
            List<String> dependencies = getDependenciesFromActiveChoicesParameter(paramDef);
            paramInfo.setDependencies(dependencies);
            
            LOGGER.log(Level.FINE, "Successfully rendered Active Choices parameter " + paramDef.getName() + 
                      " with " + choices.size() + " choices and " + dependencies.size() + " dependencies");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi render Active Choices parameter " + paramDef.getName() + ": " + e.getMessage(), e);
            throw new RuntimeException("Không thể render Active Choices parameter: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lấy choices từ Active Choices parameter sử dụng reflection
     * 
     * @param paramDef ParameterDefinition
     * @param currentValues Map các giá trị parameter hiện tại
     * @return List các choices
     */
    private List<String> getChoicesFromActiveChoicesParameter(ParameterDefinition paramDef, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        try {
            // Kiểm tra xem parameter có phải là AbstractScriptableParameter không
            if (isInstanceOf(paramDef, ABSTRACT_SCRIPTABLE_PARAMETER)) {
                choices = getChoicesFromScript(paramDef, currentValues);
            }
            
            // Nếu không lấy được từ script, thử các method khác
            if (choices.isEmpty()) {
                choices = getChoicesFromFallbackMethods(paramDef, currentValues);
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi lấy choices từ Active Choices parameter: " + e.getMessage());
        }
        
        return choices;
    }
    
    /**
     * Lấy choices từ script của AbstractScriptableParameter
     */
    private List<String> getChoicesFromScript(ParameterDefinition paramDef, Map<String, String> currentValues) {
        try {
            // Lấy Script object từ parameter
            Object script = invokeMethod(paramDef, "getScript");
            if (script == null) {
                return new ArrayList<>();
            }
            
            // Gọi script.eval() với currentValues
            Object scriptResult = invokeMethod(script, "eval", Map.class, currentValues);
            
            // Normalize kết quả về List<String>
            return normalizeChoicesResult(scriptResult);
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Không thể lấy choices từ script: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Lấy choices từ các fallback methods khác
     */
    private List<String> getChoicesFromFallbackMethods(ParameterDefinition paramDef, Map<String, String> currentValues) {
        List<String> choices = new ArrayList<>();
        
        // Thử method getChoices(Map)
        try {
            Object result = invokeMethod(paramDef, "getChoices", Map.class, currentValues);
            choices = normalizeChoicesResult(result);
            if (!choices.isEmpty()) {
                return choices;
            }
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }
        
        // Thử method getChoices()
        try {
            Object result = invokeMethod(paramDef, "getChoices");
            choices = normalizeChoicesResult(result);
            if (!choices.isEmpty()) {
                return choices;
            }
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }
        
        // Thử method getChoicesForUI()
        try {
            Object result = invokeMethod(paramDef, "getChoicesForUI");
            choices = normalizeChoicesResult(result);
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }
        
        return choices;
    }
    
    /**
     * Lấy dependencies từ Active Choices parameter
     */
    private List<String> getDependenciesFromActiveChoicesParameter(ParameterDefinition paramDef) {
        List<String> dependencies = new ArrayList<>();
        
        try {
            // Thử lấy referenced parameters
            Object referencedParams = invokeMethod(paramDef, "getReferencedParameters");
            if (referencedParams instanceof String) {
                String[] params = ((String) referencedParams).split(",");
                for (String param : params) {
                    String trimmed = param.trim();
                    if (!trimmed.isEmpty()) {
                        dependencies.add(trimmed);
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Không thể lấy dependencies từ Active Choices parameter: " + e.getMessage());
        }
        
        return dependencies;
    }
    
    /**
     * Chuẩn hóa kết quả choices từ nhiều định dạng khác nhau về List<String>
     */
    private List<String> normalizeChoicesResult(Object scriptResult) {
        if (scriptResult == null) {
            return new ArrayList<>();
        }
        
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
            List<String> result = new ArrayList<>();
            result.add((String) scriptResult);
            return result;
        } else {
            List<String> result = new ArrayList<>();
            result.add(scriptResult.toString());
            return result;
        }
    }
    
    /**
     * Helper methods để normalize từ các kiểu dữ liệu khác nhau
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
    
    private List<String> normalizeFromMap(Map<?, ?> map) {
        List<String> choices = new ArrayList<>();
        
        // Ưu tiên lấy values
        for (Object value : map.values()) {
            if (value != null) {
                choices.add(value.toString());
            }
        }
        
        // Nếu values toàn null, lấy keys
        if (choices.isEmpty()) {
            for (Object key : map.keySet()) {
                if (key != null) {
                    choices.add(key.toString());
                }
            }
        }
        
        return choices;
    }
    
    private List<String> normalizeFromCollection(Collection<?> collection) {
        List<String> choices = new ArrayList<>();
        for (Object item : collection) {
            if (item != null) {
                choices.add(item.toString());
            }
        }
        return choices;
    }
    
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
    
    private List<String> normalizeFromListBoxModel(Object listBoxModel) {
        List<String> choices = new ArrayList<>();
        
        try {
            Object iterator = invokeMethod(listBoxModel, "iterator");
            if (iterator instanceof java.util.Iterator) {
                java.util.Iterator<?> iter = (java.util.Iterator<?>) iterator;
                while (iter.hasNext()) {
                    Object option = iter.next();
                    String optionValue = extractOptionValue(option);
                    if (optionValue != null) {
                        choices.add(optionValue);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi xử lý ListBoxModel: " + e.getMessage());
        }
        
        return choices;
    }
    
    private String extractOptionValue(Object option) {
        try {
            // Thử lấy name() trước
            Object name = invokeMethod(option, "name");
            if (name != null) {
                return name.toString();
            }
        } catch (Exception e) {
            // Không có name()
        }
        
        try {
            // Thử lấy value()
            Object value = invokeMethod(option, "value");
            if (value != null) {
                return value.toString();
            }
        } catch (Exception e) {
            // Không có value()
        }
        
        return option.toString();
    }
    
    /**
     * Utility methods cho reflection
     */
    private boolean isInstanceOf(Object obj, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return clazz.isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    private Object invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        java.lang.reflect.Method method = obj.getClass().getMethod(methodName, paramTypes);
        return method.invoke(obj, args);
    }
    
    private Object invokeMethod(Object obj, String methodName, Class<?> paramType, Object arg) throws Exception {
        return invokeMethod(obj, methodName, new Class<?>[]{paramType}, arg);
    }
    
    private Object invokeMethod(Object obj, String methodName) throws Exception {
        return invokeMethod(obj, methodName, new Class<?>[0]);
    }
}
