package io.kanbanai.amtIntegration.model;

import java.util.ArrayList;
import java.util.List;

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
     */
    private List<String> choices;

    /**
     * List of parameter names that this parameter depends on
     * Used for Cascade Choice Parameter or Dynamic Reference Parameter
     */
    private List<String> dependencies;
    
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
        ValidationUtils.requireNonEmpty(name, "name");
        if (!ValidationUtils.isValidParameterName(name)) {
            throw new IllegalArgumentException("Invalid parameter name format: " + name);
        }
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
        if (currentValue != null && !ValidationUtils.isValidParameterValue(currentValue)) {
            throw new IllegalArgumentException("Parameter value too long");
        }
        this.currentValue = currentValue;
    }
    
    public ParameterInputType getInputType() {
        return inputType;
    }
    
    public void setInputType(ParameterInputType inputType) {
        this.inputType = inputType != null ? inputType : ParameterInputType.TEXT;
    }
    
    public List<String> getChoices() {
        return choices;
    }
    
    public void setChoices(List<String> choices) {
        if (choices != null && !ValidationUtils.isValidChoicesList(choices)) {
            throw new IllegalArgumentException("Invalid choices list");
        }
        this.choices = choices != null ? choices : new ArrayList<>();
    }
    
    /**
     * Adds a choice to the list
     *
     * @param choice Choice to add
     */
    public void addChoice(String choice) {
        if (choice != null) {
            this.choices.add(choice);
        }
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        if (dependencies != null && !ValidationUtils.isValidDependenciesList(dependencies)) {
            throw new IllegalArgumentException("Invalid dependencies list");
        }
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
            if (!ValidationUtils.isValidParameterName(trimmed)) {
                throw new IllegalArgumentException("Invalid dependency name: " + trimmed);
            }
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
     * Converts object to JSON string
     *
     * @return JSON string representation of the object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // Basic information
        sb.append("\"name\":").append(JsonUtils.toJsonString(name)).append(",");
        sb.append("\"type\":").append(JsonUtils.toJsonString(type)).append(",");
        sb.append("\"description\":").append(JsonUtils.toJsonString(description)).append(",");

        // CurrentValue - serialize as array if contains multiple values (comma-separated)
        sb.append("\"currentValue\":").append(JsonUtils.serializeParameterValue(currentValue)).append(",");

        // Input type
        sb.append("\"inputType\":").append(JsonUtils.toJsonString(inputType != null ? inputType.getValue() : null)).append(",");

        // Flags
        sb.append("\"isDynamic\":").append(JsonUtils.toJsonBoolean(isDynamic)).append(",");
        sb.append("\"isRequired\":").append(JsonUtils.toJsonBoolean(isRequired)).append(",");

        // Error message
        sb.append("\"errorMessage\":").append(JsonUtils.toJsonString(errorMessage)).append(",");

        // Arrays
        sb.append("\"dependencies\":").append(JsonUtils.toJsonArray(dependencies)).append(",");
        sb.append("\"choices\":").append(JsonUtils.toJsonArray(choices)).append(",");

        // Data field for DynamicReferenceParameter
        // Clean data if it only contains "[]" or "[][]"
        String cleanedData = JsonUtils.cleanDataField(data);
        sb.append("\"data\":").append(JsonUtils.toJsonString(cleanedData));

        sb.append("}");
        return sb.toString();
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
            this.parameterInfo.setChoices(choices);
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
         * Builds the RenderedParameterInfo instance.
         *
         * @return configured RenderedParameterInfo
         */
        public RenderedParameterInfo build() {
            return this.parameterInfo;
        }
    }
}
