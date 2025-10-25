package io.kanbanai.amtIntegration.model;

import io.kanbanai.amtIntegration.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;

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
     * List of choices (for choice/select parameters)
     */
    private List<String> choices;
    
    /**
     * Whether this parameter is required
     */
    private boolean required;
    
    /**
     * Default constructor
     */
    public InputParameterInfo() {
        this.choices = new ArrayList<>();
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
    }
    
    public boolean isRequired() {
        return required;
    }
    
    public void setRequired(boolean required) {
        this.required = required;
    }
    
    /**
     * Converts object to JSON string
     *
     * @return JSON string representation of the object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        sb.append("\"name\":").append(JsonUtils.toJsonString(name)).append(",");
        sb.append("\"type\":").append(JsonUtils.toJsonString(type)).append(",");
        sb.append("\"description\":").append(JsonUtils.toJsonString(description)).append(",");
        sb.append("\"defaultValue\":").append(JsonUtils.toJsonString(defaultValue)).append(",");
        sb.append("\"inputType\":").append(JsonUtils.toJsonString(inputType)).append(",");
        sb.append("\"required\":").append(JsonUtils.toJsonBoolean(required)).append(",");
        
        // Choices array
        sb.append("\"choices\":[");
        if (choices != null && !choices.isEmpty()) {
            for (int i = 0; i < choices.size(); i++) {
                sb.append(JsonUtils.toJsonString(choices.get(i)));
                if (i < choices.size() - 1) {
                    sb.append(",");
                }
            }
        }
        sb.append("]");
        
        sb.append("}");
        return sb.toString();
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

