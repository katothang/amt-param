package io.kanbanai.amtIntegration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import io.kanbanai.amtIntegration.util.JsonMapper;
import io.kanbanai.amtIntegration.util.JsonUtils;
import io.kanbanai.amtIntegration.util.ValidationUtils;

/**
 * Model class containing detailed information of a rendered parameter.
 *
 * This class represents a specific parameter in a Jenkins job,
 * including all necessary information to display the parameter in the UI
 * such as name, type, current value, available choices, and dependencies.
 *
 * Instead of using generic Object or Map, this class provides
 * type safety and better IDE support.
 *
 * Flow:
 * 1. Stores all parameter metadata (name, type, description)
 * 2. Holds current parameter value and available choices
 * 3. Tracks parameter dependencies for cascade functionality
 * 4. Provides JSON serialization for API responses
 * 5. Includes validation and utility methods for parameter handling
 *
 * @author KanbanAI
 * @since 1.0.2
 */
public class RenderedParameterInfo {
    
    /**
     * Name of the parameter (unique within a job)
     */
    private String name;

    /**
     * Parameter type (class name of ParameterDefinition)
     * Examples: "StringParameterDefinition", "ChoiceParameterDefinition", "ChoiceParameter"
     */
    private String type;

    /**
     * Description of the parameter (can be null)
     */
    private String description;

    /**
     * Current value of the parameter
     * Can be default value or value passed from request
     */
    private String currentValue;

    /**
     * Appropriate UI input type for this parameter
     * Possible values: "text", "textarea", "select", "checkbox", "password",
     * "cascade_select", "dynamic_reference"
     */
    private ParameterInputType inputType;

    /**
     * List of available choices (for dropdown, radio, checkbox)
     * Empty if parameter has no fixed choices
     * Each choice is a Map with "key" and "value" entries
     * For simple choices, key and value are the same
     */
    private List<Map<String, String>> choices;

    /**
     * List of parameter names that this parameter depends on
     * Used for Cascade Choice Parameter or Dynamic Reference Parameter
     */
    private List<String> dependencies;

    /**
     * Raw Groovy script content for Active Choices parameters
     * Contains the original script code before evaluation
     */
    private String rawScript;

    /**
     * Indicates whether the raw script runs in sandbox mode
     */
    private Boolean rawScriptSandbox;
    
    /**
     * True if this parameter is dynamic (Active Choices Plugin)
     * Dynamic parameters can change choices based on values of other parameters
     */
    private boolean isDynamic;

    /**
     * True if this parameter is required (must have a value)
     */
    private boolean isRequired;

    /**
     * Error message if there's an issue when rendering the parameter
     */
    private String errorMessage;

    /**
     * HTML data or special content for DynamicReferenceParameter
     * Only used for parameters with inputType DYNAMIC_REFERENCE
     */
    private String data;

    /**
     * Choice type for Active Choices parameters
     * Examples: "PT_SINGLE_SELECT", "PT_CHECKBOX", "ET_FORMATTED_HTML"
     * Only used for Active Choices Plugin parameters
     */
    private String choiceType;

    /**
     * Default constructor
     */
    public RenderedParameterInfo() {
        this.choices = new ArrayList<>();
        this.dependencies = new ArrayList<>();
        this.inputType = ParameterInputType.TEXT;
        this.isDynamic = false;
        this.isRequired = false;
    }

    /**
     * Constructor with basic information
     *
     * @param name Parameter name
     * @param type Parameter type
     * @param description Parameter description
     */
    public RenderedParameterInfo(String name, String type, String description) {
        this();
        this.name = name;
        this.type = type;
        this.description = description;
    }
    
    // Getters and Setters
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        // No validation - accept any parameter name from Jenkins config
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
        if (description != null && !ValidationUtils.isValidParameterDescription(description)) {
            throw new IllegalArgumentException("Parameter description too long");
        }
        this.description = description;
    }
    
    public String getCurrentValue() {
        return currentValue;
    }
    
    public void setCurrentValue(String currentValue) {
        this.currentValue = currentValue;
    }
    
    public ParameterInputType getInputType() {
        return inputType;
    }
    
    public void setInputType(ParameterInputType inputType) {
        this.inputType = inputType != null ? inputType : ParameterInputType.TEXT;
    }
    
    public List<Map<String, String>> getChoices() {
        return choices;
    }

    public void setChoices(List<Map<String, String>> choices) {
        this.choices = choices != null ? choices : new ArrayList<>();
    }

    /**
     * Sets choices from a list of strings (for backward compatibility)
     * Converts each string to a Map with key=value=string
     *
     * @param stringChoices List of choice strings
     */
    public void setChoicesFromStrings(List<String> stringChoices) {
        this.choices = new ArrayList<>();
        if (stringChoices != null) {
            for (String choice : stringChoices) {
                if (choice != null) {
                    Map<String, String> choiceMap = new HashMap<>();
                    choiceMap.put("key", choice);
                    choiceMap.put("value", choice);
                    this.choices.add(choiceMap);
                }
            }
        }
    }

    /**
     * Adds a choice to the list (for backward compatibility)
     * Creates a Map with key=value=choice
     *
     * @param choice Choice to add
     */
    public void addChoice(String choice) {
        if (choice != null) {
            Map<String, String> choiceMap = new HashMap<>();
            choiceMap.put("key", choice);
            choiceMap.put("value", choice);
            this.choices.add(choiceMap);
        }
    }

    /**
     * Adds a choice with separate key and value
     *
     * @param key Choice key
     * @param value Choice value
     */
    public void addChoice(String key, String value) {
        if (key != null && value != null) {
            Map<String, String> choiceMap = new HashMap<>();
            choiceMap.put("key", key);
            choiceMap.put("value", value);
            this.choices.add(choiceMap);
        }
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    /**
     * Adds a dependency to the list
     *
     * @param dependency Parameter dependency name
     */
    public void addDependency(String dependency) {
        if (dependency != null && !dependency.trim().isEmpty()) {
            String trimmed = dependency.trim();
            this.dependencies.add(trimmed);
        }
    }
    
    public boolean isDynamic() {
        return isDynamic;
    }
    
    public void setDynamic(boolean dynamic) {
        isDynamic = dynamic;
    }
    
    public boolean isRequired() {
        return isRequired;
    }
    
    public void setRequired(boolean required) {
        isRequired = required;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        if (errorMessage != null && !ValidationUtils.isValidErrorMessage(errorMessage)) {
            throw new IllegalArgumentException("Error message too long");
        }
        this.errorMessage = errorMessage;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getChoiceType() {
        return choiceType;
    }

    public void setChoiceType(String choiceType) {
        this.choiceType = choiceType;
    }

    public String getRawScript() {
        return rawScript;
    }

    public void setRawScript(String rawScript) {
        this.rawScript = rawScript;
    }

    public Boolean getRawScriptSandbox() {
        return rawScriptSandbox;
    }

    public void setRawScriptSandbox(Boolean rawScriptSandbox) {
        this.rawScriptSandbox = rawScriptSandbox;
    }

    /**
     * Checks whether the parameter has choices
     *
     * @return true if has at least 1 choice, false otherwise
     */
    public boolean hasChoices() {
        return choices != null && !choices.isEmpty();
    }

    /**
     * Checks whether the parameter has dependencies
     *
     * @return true if has at least 1 dependency, false otherwise
     */
    public boolean hasDependencies() {
        return dependencies != null && !dependencies.isEmpty();
    }

    /**
     * Checks whether the parameter has an error
     *
     * @return true if has error message, false otherwise
     */
    public boolean hasError() {
        return errorMessage != null && !errorMessage.trim().isEmpty();
    }

    /**
     * Checks whether the parameter has data
     *
     * @return true if has data content, false otherwise
     */
    public boolean hasData() {
        return data != null && !data.trim().isEmpty();
    }

    /**
     * Checks whether the parameter has a choice type
     *
     * @return true if has choice type, false otherwise
     */
    public boolean hasChoiceType() {
        return choiceType != null && !choiceType.trim().isEmpty();
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
        return "RenderedParameterInfo{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", inputType=" + inputType +
                ", isDynamic=" + isDynamic +
                ", choicesCount=" + (choices != null ? choices.size() : 0) +
                ", dependenciesCount=" + (dependencies != null ? dependencies.size() : 0) +
                ", hasData=" + (data != null && !data.trim().isEmpty()) +
                ", choiceType='" + choiceType + '\'' +
                '}';
    }

    /**
     * Builder class for creating RenderedParameterInfo instances.
     *
     * Provides a fluent API for constructing parameter info objects
     * with validation and default values.
     */
    public static class Builder {
        private final RenderedParameterInfo parameterInfo;

        /**
         * Creates a new builder with required fields.
         *
         * @param name Parameter name (required)
         * @param type Parameter type (required)
         */
        public Builder(String name, String type) {
            this.parameterInfo = new RenderedParameterInfo();
            this.parameterInfo.setName(name);
            this.parameterInfo.setType(type);
        }

        /**
         * Sets the parameter description.
         *
         * @param description Parameter description
         * @return this builder
         */
        public Builder description(String description) {
            this.parameterInfo.setDescription(description);
            return this;
        }

        /**
         * Sets the current parameter value.
         *
         * @param currentValue Current parameter value
         * @return this builder
         */
        public Builder currentValue(String currentValue) {
            this.parameterInfo.setCurrentValue(currentValue);
            return this;
        }

        /**
         * Sets the input type.
         *
         * @param inputType Parameter input type
         * @return this builder
         */
        public Builder inputType(ParameterInputType inputType) {
            this.parameterInfo.setInputType(inputType);
            return this;
        }

        /**
         * Sets the choices list.
         *
         * @param choices List of available choices
         * @return this builder
         */
        public Builder choices(List<String> choices) {
            this.parameterInfo.setChoicesFromStrings(choices);
            return this;
        }

        /**
         * Sets the dependencies list.
         *
         * @param dependencies List of parameter dependencies
         * @return this builder
         */
        public Builder dependencies(List<String> dependencies) {
            this.parameterInfo.setDependencies(dependencies);
            return this;
        }

        /**
         * Sets whether the parameter is dynamic.
         *
         * @param isDynamic true if parameter is dynamic
         * @return this builder
         */
        public Builder dynamic(boolean isDynamic) {
            this.parameterInfo.setDynamic(isDynamic);
            return this;
        }

        /**
         * Sets whether the parameter is required.
         *
         * @param isRequired true if parameter is required
         * @return this builder
         */
        public Builder required(boolean isRequired) {
            this.parameterInfo.setRequired(isRequired);
            return this;
        }

        /**
         * Sets the error message.
         *
         * @param errorMessage Error message
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) {
            this.parameterInfo.setErrorMessage(errorMessage);
            return this;
        }

        /**
         * Sets the data field.
         *
         * @param data Parameter data
         * @return this builder
         */
        public Builder data(String data) {
            this.parameterInfo.setData(data);
            return this;
        }

        /**
         * Sets the choice type.
         *
         * @param choiceType Choice type for Active Choices parameters
         * @return this builder
         */
        public Builder choiceType(String choiceType) {
            this.parameterInfo.setChoiceType(choiceType);
            return this;
        }

        /**
         * Builds the RenderedParameterInfo instance.
         *
         * @return configured RenderedParameterInfo
         */
        public RenderedParameterInfo build() {
            return this.parameterInfo;
        }
    }
}
