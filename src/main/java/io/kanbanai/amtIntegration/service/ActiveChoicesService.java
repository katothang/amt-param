package io.kanbanai.amtIntegration.service;

import hudson.model.ParameterDefinition;
import io.kanbanai.amtIntegration.model.RenderedParameterInfo;
import io.kanbanai.amtIntegration.model.ParameterInputType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Service specialized in handling Active Choices Plugin parameters.
 *
 * This service is separated to handle Active Choices Plugin features
 * safely, with graceful fallback capability when the plugin is not available.
 *
 * All interactions with Active Choices Plugin classes are performed
 * through reflection to avoid ClassNotFoundException when the plugin is absent.
 *
 * Flow:
 * 1. Detects Active Choices parameter types using reflection
 * 2. Extracts choices and dependencies from Active Choices parameters
 * 3. Handles cascade parameters with dynamic choice generation
 * 4. Provides fallback behavior when plugin classes are not available
 * 5. Maintains type safety while working with unknown plugin classes
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
            return;
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

            // Lấy dependencies (referenced parameters)
            List<String> dependencies = getDependenciesFromActiveChoicesParameter(paramDef);
            paramInfo.setDependencies(dependencies);

            // Lấy choice type từ Active Choices parameter
            String choiceType = getChoiceTypeFromActiveChoicesParameter(paramDef);
            paramInfo.setChoiceType(choiceType);

            // Lấy raw script từ Active Choices parameter
            Map<String, Object> scriptInfo = getRawScriptFromActiveChoicesParameter(paramDef);
            if (scriptInfo != null) {
                paramInfo.setRawScript((String) scriptInfo.get("script"));
                paramInfo.setRawScriptSandbox((Boolean) scriptInfo.get("sandbox"));
            }

            // Xử lý khác nhau cho DynamicReferenceParameter vs các loại khác
            if (className.contains("DynamicReferenceParameter")) {
                // Đối với DynamicReferenceParameter, lưu HTML content vào field 'data'
                List<String> htmlContent = getChoicesFromActiveChoicesParameter(paramDef, currentValues);
                if (!htmlContent.isEmpty()) {
                    // Ghép tất cả HTML content thành một string
                    StringBuilder dataBuilder = new StringBuilder();
                    for (String content : htmlContent) {
                        dataBuilder.append(content);
                    }
                    paramInfo.setData(dataBuilder.toString());
                }
                // Không set choices cho DynamicReferenceParameter
                LOGGER.log(Level.FINE, "Successfully rendered DynamicReferenceParameter " + paramDef.getName() +
                          " with HTML data and " + dependencies.size() + " dependencies");
            } else {
                // Đối với ChoiceParameter và CascadeChoiceParameter, lưu vào field 'choices'
                Object choicesResult = getChoicesObjectFromActiveChoicesParameter(paramDef, currentValues);
                populateChoicesFromResult(paramInfo, choicesResult);
                LOGGER.log(Level.FINE, "Successfully rendered Active Choices parameter " + paramDef.getName() +
                          " with choices and " + dependencies.size() + " dependencies");
            }
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi render Active Choices parameter " + paramDef.getName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Lấy choices object từ Active Choices parameter sử dụng reflection
     * Trả về raw object (có thể là List, Map, etc.) để preserve key-value pairs
     *
     * @param paramDef ParameterDefinition
     * @param currentValues Map các giá trị parameter hiện tại
     * @return Object chứa choices (List, Map, etc.)
     */
    private Object getChoicesObjectFromActiveChoicesParameter(ParameterDefinition paramDef, Map<String, String> currentValues) {
        try {
            // Kiểm tra xem parameter có phải là AbstractScriptableParameter không
            if (isInstanceOf(paramDef, ABSTRACT_SCRIPTABLE_PARAMETER)) {
                Object result = getChoicesObjectFromScript(paramDef, currentValues);
                if (result != null) {
                    return result;
                }
            }

            // Nếu không lấy được từ script, thử các method khác
            return getChoicesObjectFromFallbackMethods(paramDef, currentValues);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Lỗi khi lấy choices từ Active Choices parameter: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Lấy choices từ Active Choices parameter sử dụng reflection (backward compatibility)
     *
     * @param paramDef ParameterDefinition
     * @param currentValues Map các giá trị parameter hiện tại
     * @return List các choices
     */
    private List<String> getChoicesFromActiveChoicesParameter(ParameterDefinition paramDef, Map<String, String> currentValues) {
        Object choicesObject = getChoicesObjectFromActiveChoicesParameter(paramDef, currentValues);
        return normalizeChoicesResult(choicesObject);
    }
    
    /**
     * Lấy choices object từ script của AbstractScriptableParameter
     */
    private Object getChoicesObjectFromScript(ParameterDefinition paramDef, Map<String, String> currentValues) {
        try {
            // Lấy Script object từ parameter
            Object script = invokeMethod(paramDef, "getScript");
            if (script == null) {
                return null;
            }

            // Gọi script.eval() với currentValues
            return invokeMethod(script, "eval", Map.class, currentValues);

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Không thể lấy choices từ script: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Lấy choices object từ các fallback methods khác
     */
    private Object getChoicesObjectFromFallbackMethods(ParameterDefinition paramDef, Map<String, String> currentValues) {
        // Thử method getChoices(Map)
        try {
            Object result = invokeMethod(paramDef, "getChoices", Map.class, currentValues);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }

        // Thử method getChoices()
        try {
            Object result = invokeMethod(paramDef, "getChoices");
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }

        // Thử method getChoicesForUI()
        try {
            Object result = invokeMethod(paramDef, "getChoicesForUI");
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            // Method không tồn tại hoặc lỗi
        }

        return new ArrayList<>();
    }
    
    /**
     * Populate choices vào RenderedParameterInfo từ result object
     * Xử lý đúng Map để preserve key-value pairs
     */
    private void populateChoicesFromResult(RenderedParameterInfo paramInfo, Object result) {
        if (result == null) {
            return;
        }

        // Nếu là Map, xử lý để lấy key-value pairs
        if (result instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) result;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    // Trong Active Choices: key = value của HTML option, value = display text
                    paramInfo.addChoice(entry.getKey().toString(), entry.getValue().toString());
                }
            }
        }
        // Nếu là List hoặc Collection
        else if (result instanceof List) {
            List<?> list = (List<?>) result;
            for (Object item : list) {
                if (item != null) {
                    // Nếu item là Map.Entry, xử lý như key-value
                    if (item instanceof Map.Entry) {
                        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                        if (entry.getKey() != null && entry.getValue() != null) {
                            paramInfo.addChoice(entry.getKey().toString(), entry.getValue().toString());
                        }
                    } else {
                        // Nếu là string thông thường, key = value
                        paramInfo.addChoice(item.toString());
                    }
                }
            }
        }
        // Nếu là Collection khác
        else if (result instanceof Collection) {
            Collection<?> collection = (Collection<?>) result;
            for (Object item : collection) {
                if (item != null) {
                    paramInfo.addChoice(item.toString());
                }
            }
        }
        // Nếu là Array
        else if (result.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object item = java.lang.reflect.Array.get(result, i);
                if (item != null) {
                    paramInfo.addChoice(item.toString());
                }
            }
        }
        // Nếu là ListBoxModel (Jenkins)
        else if (result.getClass().getName().contains("ListBoxModel")) {
            try {
                // ListBoxModel có method iterator()
                Object iterator = invokeMethod(result, "iterator");
                if (iterator instanceof java.util.Iterator) {
                    java.util.Iterator<?> iter = (java.util.Iterator<?>) iterator;
                    while (iter.hasNext()) {
                        Object option = iter.next();
                        if (option != null) {
                            // ListBoxModel.Option có name (display) và value
                            try {
                                String value = (String) invokeMethod(option, "value");
                                String name = (String) invokeMethod(option, "name");
                                if (value != null && name != null) {
                                    paramInfo.addChoice(value, name);
                                } else if (value != null) {
                                    paramInfo.addChoice(value);
                                }
                            } catch (Exception e) {
                                paramInfo.addChoice(option.toString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Không thể parse ListBoxModel: " + e.getMessage());
                // Fallback: convert to string
                paramInfo.addChoice(result.toString());
            }
        }
        // Fallback: convert to string
        else {
            paramInfo.addChoice(result.toString());
        }
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
     * Lấy choice type từ Active Choices parameter
     */
    private String getChoiceTypeFromActiveChoicesParameter(ParameterDefinition paramDef) {
        try {
            // Thử lấy choice type từ method getChoiceType()
            Object choiceType = invokeMethod(paramDef, "getChoiceType");
            if (choiceType != null) {
                return choiceType.toString();
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Không thể lấy choice type từ Active Choices parameter: " + e.getMessage());
        }

        return null;
    }

    /**
     * Lấy raw groovy script từ Active Choices parameter
     *
     * @param paramDef ParameterDefinition
     * @return Map chứa "script" (String) và "sandbox" (Boolean), hoặc null nếu không lấy được
     */
    private Map<String, Object> getRawScriptFromActiveChoicesParameter(ParameterDefinition paramDef) {
        try {
            LOGGER.log(Level.INFO, "Attempting to get raw script from parameter: " + paramDef.getName());

            // Lấy Script object từ parameter (GroovyScript)
            // GroovyScript.getScript() returns SecureGroovyScript object
            Object groovyScript = invokeMethod(paramDef, "getScript");
            if (groovyScript == null) {
                LOGGER.log(Level.WARNING, "getScript() returned null for parameter: " + paramDef.getName());
                return null;
            }

            LOGGER.log(Level.INFO, "GroovyScript object class: " + groovyScript.getClass().getName());

            // groovyScript is actually a GroovyScript object
            // We need to call getScript() on it to get SecureGroovyScript
            Object secureScript = invokeMethod(groovyScript, "getScript");
            if (secureScript == null) {
                LOGGER.log(Level.WARNING, "groovyScript.getScript() returned null for parameter: " + paramDef.getName());
                return null;
            }

            LOGGER.log(Level.INFO, "SecureScript object class: " + secureScript.getClass().getName());
            Map<String, Object> result = new HashMap<>();

            // Lấy script content từ SecureGroovyScript
            try {
                Object scriptContent = invokeMethod(secureScript, "getScript");
                if (scriptContent != null) {
                    result.put("script", scriptContent.toString());
                    LOGGER.log(Level.INFO, "Successfully extracted script content, length: " + scriptContent.toString().length());
                } else {
                    LOGGER.log(Level.WARNING, "getScript() on SecureScript returned null");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error getting script content: " + e.getMessage(), e);
            }

            // Lấy sandbox flag từ SecureGroovyScript
            try {
                Object sandbox = invokeMethod(secureScript, "isSandbox");
                if (sandbox instanceof Boolean) {
                    result.put("sandbox", (Boolean) sandbox);
                    LOGGER.log(Level.INFO, "Successfully extracted sandbox flag: " + sandbox);
                } else {
                    LOGGER.log(Level.WARNING, "isSandbox() returned non-Boolean: " + (sandbox != null ? sandbox.getClass().getName() : "null"));
                }
            } catch (Exception e) {
                // Không có sandbox method hoặc lỗi
                LOGGER.log(Level.WARNING, "Error getting sandbox flag: " + e.getMessage(), e);
                result.put("sandbox", null);
            }

            if (!result.isEmpty()) {
                LOGGER.log(Level.INFO, "Successfully extracted raw script info for parameter: " + paramDef.getName());
                return result;
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể lấy raw script từ Active Choices parameter: " + e.getMessage(), e);
        }

        LOGGER.log(Level.WARNING, "Failed to extract raw script for parameter: " + paramDef.getName());
        return null;
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
