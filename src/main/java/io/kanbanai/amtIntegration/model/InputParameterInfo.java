package io.kanbanai.amtIntegration.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.kanbanai.amtIntegration.util.JsonMapper;
import io.kanbanai.amtIntegration.util.JsonUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model class containing information about an input parameter in a pipeline stage.
 *
 * This class represents a parameter that needs to be provided when confirming
 * a pipeline stage input, including text inputs, passwords, choice parameters, etc.
 *
 * Flow:
 * 1. Stores parameter identification (name, type)
 * 2. Contains parameter metadata (description, default value)
 * 3. Handles different parameter types (text, password, boolean, choice)
 * 4. Provides JSON serialization for API responses
 *
 * @author KanbanAI
 * @since 1.0.4
 */
public class InputParameterInfo {

    /**
     * Parameter name
     */
    private String name;

    /**
     * Parameter type (e.g., "StringParameterDefinition", "BooleanParameterDefinition", "ChoiceParameterDefinition")
     */
    private String type;

    /**
     * Parameter description
     */
    private String description;

    /**
     * Default value for the parameter
     */
    private String defaultValue;

    /**
     * Input type for UI rendering (text, password, checkbox, select, etc.)
     */
    private String inputType;

    /**
     * List of choices (for choice/select parameters) - kept for backward compatibility
     * Hidden from JSON output, use choicesFormatted instead
     */
    @JsonIgnore
    private List<String> choices;

    /**
     * Map of choices with key-value pairs (for choice/select parameters)
     * Key = value to submit, Value = display text
     * Hidden from JSON output, use choicesFormatted instead
     */
    @JsonIgnore
    private Map<String, String> choicesMap;

    /**
     * Whether this parameter is required
     */
    private boolean required;
    
    /**
     * Default constructor
     */
    public InputParameterInfo() {
        this.choices = new ArrayList<>();
        this.choicesMap = new LinkedHashMap<>();
        this.required = false;
        this.inputType = "text";
    }
    
    /**
     * Constructor with basic information
     *
     * @param name Parameter name
     * @param type Parameter type
     */
    public InputParameterInfo(String name, String type) {
        this();
        this.name = name;
        this.type = type;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public String getInputType() {
        return inputType;
    }
    
    public void setInputType(String inputType) {
        this.inputType = inputType;
    }
    
    public List<String> getChoices() {
        return choices;
    }

    public void setChoices(List<String> choices) {
        this.choices = choices != null ? choices : new ArrayList<>();
        // Also populate choicesMap with key=value for backward compatibility
        if (this.choices != null && !this.choices.isEmpty()) {
            this.choicesMap = new LinkedHashMap<>();
            for (String choice : this.choices) {
                this.choicesMap.put(choice, choice);
            }
        }
    }

    public Map<String, String> getChoicesMap() {
        return choicesMap;
    }

    public void setChoicesMap(Map<String, String> choicesMap) {
        this.choicesMap = choicesMap != null ? choicesMap : new LinkedHashMap<>();
    }

    /**
     * Gets formatted choices as list of key-value objects for JSON serialization.
     * This is the property that will be serialized to JSON.
     *
     * @return list of choice objects with key and value
     */
    @JsonProperty("choices")
    public List<Map<String, String>> getChoicesFormatted() {
        List<Map<String, String>> formatted = new ArrayList<>();

        if (choicesMap != null && !choicesMap.isEmpty()) {
            // Use choicesMap for key-value format
            for (Map.Entry<String, String> entry : choicesMap.entrySet()) {
                Map<String, String> choice = new LinkedHashMap<>();
                choice.put("key", entry.getKey());
                choice.put("value", entry.getValue());
                formatted.add(choice);
            }
        } else if (choices != null && !choices.isEmpty()) {
            // Fallback to simple string array if choicesMap is empty
            for (String choice : choices) {
                Map<String, String> choiceObj = new LinkedHashMap<>();
                choiceObj.put("key", choice);
                choiceObj.put("value", choice);
                formatted.add(choiceObj);
            }
        }

        return formatted;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
    
    /**
     * Converts object to JSON string using Jackson ObjectMapper.
     *
     * @return JSON string representation of the object
     * @deprecated Use JsonMapper.toJson(object) instead for better performance and maintainability
     */
    @Deprecated
    public String toJson() {
        return JsonMapper.toJson(this);
    }
    
    @Override
    public String toString() {
        return "InputParameterInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", inputType='" + inputType + '\'' +
                ", required=" + required +
                ", choicesCount=" + (choices != null ? choices.size() : 0) +
                '}';
    }
    
    /**
     * Builder class for creating InputParameterInfo instances.
     */
    public static class Builder {
        private final InputParameterInfo paramInfo;
        
        /**
         * Creates a new builder with required fields.
         *
         * @param name Parameter name (required)
         * @param type Parameter type (required)
         */
        public Builder(String name, String type) {
            this.paramInfo = new InputParameterInfo();
            this.paramInfo.setName(name);
            this.paramInfo.setType(type);
        }
        
        public Builder description(String description) {
            this.paramInfo.setDescription(description);
            return this;
        }
        
        public Builder defaultValue(String defaultValue) {
            this.paramInfo.setDefaultValue(defaultValue);
            return this;
        }
        
        public Builder inputType(String inputType) {
            this.paramInfo.setInputType(inputType);
            return this;
        }
        
        public Builder choices(List<String> choices) {
            this.paramInfo.setChoices(choices);
            return this;
        }
        
        public Builder required(boolean required) {
            this.paramInfo.setRequired(required);
            return this;
        }
        
        public InputParameterInfo build() {
            return this.paramInfo;
        }
    }
}

