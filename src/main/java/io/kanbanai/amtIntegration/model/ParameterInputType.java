package io.kanbanai.amtIntegration.model;

/**
 * Enumeration defining UI input types for Jenkins parameters.
 *
 * This enum provides type safety for determining appropriate input types
 * for each parameter type, replacing the use of String constants.
 *
 * Each input type corresponds to a different UI component type
 * on the frontend for displaying and collecting user input.
 *
 * Flow:
 * 1. Maps Jenkins parameter types to appropriate UI input controls
 * 2. Supports both built-in Jenkins parameters and Active Choices parameters
 * 3. Provides utility methods for input type validation and categorization
 * 4. Enables consistent UI rendering across different parameter types
 *
 * @author KanbanAI
 * @since 1.0.2
 */
public enum ParameterInputType {
    
    /**
     * Simple text input - for StringParameterDefinition
     * Displays as: <input type="text">
     */
    TEXT("text"),

    /**
     * Textarea for multi-line text - for TextParameterDefinition
     * Displays as: <textarea>
     */
    TEXTAREA("textarea"),

    /**
     * Dropdown select - for ChoiceParameterDefinition and ChoiceParameter
     * Displays as: <select><option>...</option></select>
     */
    SELECT("select"),

    /**
     * Checkbox for boolean values - for BooleanParameterDefinition
     * Displays as: <input type="checkbox">
     */
    CHECKBOX("checkbox"),

    /**
     * Password input - for PasswordParameterDefinition
     * Displays as: <input type="password">
     */
    PASSWORD("password"),
    
    /**
     * Cascade select - for CascadeChoiceParameter (Active Choices)
     * Dropdown that can change choices based on values of other parameters
     * Displays as: <select> with JavaScript to handle cascade logic
     */
    CASCADE_SELECT("cascade_select"),

    /**
     * Dynamic reference - for DynamicReferenceParameter (Active Choices)
     * Can display as text, HTML, or other UI components
     * depending on script configuration
     */
    DYNAMIC_REFERENCE("dynamic_reference"),

    /**
     * File upload - for FileParameterDefinition
     * Displays as: <input type="file">
     */
    FILE("file"),

    /**
     * Hidden input - for parameters that don't require user input
     * Displays as: <input type="hidden">
     */
    HIDDEN("hidden"),

    /**
     * Multi-select - for parameters that allow multiple value selection
     * Displays as: <select multiple> or checkbox group
     */
    MULTI_SELECT("multi_select"),

    /**
     * Radio buttons - alternative to select when there are few choices
     * Displays as: <input type="radio"> group
     */
    RADIO("radio");
    
    /**
     * String value of the input type (for serialization/deserialization)
     */
    private final String value;

    /**
     * Constructor
     *
     * @param value String value of the input type
     */
    ParameterInputType(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the input type
     *
     * @return String value
     */
    public String getValue() {
        return value;
    }

    /**
     * Finds ParameterInputType from string value
     *
     * @param value String value to find
     * @return Corresponding ParameterInputType, or TEXT if not found
     */
    public static ParameterInputType fromValue(String value) {
        if (value == null) {
            return TEXT;
        }

        for (ParameterInputType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        return TEXT; // Default fallback
    }
    
    /**
     * Checks whether the input type supports choices
     *
     * @return true if input type can have choices (SELECT, CASCADE_SELECT, MULTI_SELECT, RADIO)
     */
    public boolean supportsChoices() {
        return this == SELECT ||
               this == CASCADE_SELECT ||
               this == MULTI_SELECT ||
               this == RADIO;
    }

    /**
     * Checks whether the input type is dynamic
     * Dynamic input types can change behavior based on values of other parameters
     *
     * @return true if it's a dynamic input type (CASCADE_SELECT, DYNAMIC_REFERENCE)
     */
    public boolean isDynamic() {
        return this == CASCADE_SELECT || this == DYNAMIC_REFERENCE;
    }

    /**
     * Checks whether the input type requires special handling
     *
     * @return true if special handling is needed (PASSWORD, FILE, HIDDEN)
     */
    public boolean requiresSpecialHandling() {
        return this == PASSWORD || this == FILE || this == HIDDEN;
    }

    /**
     * Gets appropriate input type based on Jenkins ParameterDefinition class name
     *
     * @param parameterClassName Class name of the ParameterDefinition
     * @return Appropriate ParameterInputType
     */
    public static ParameterInputType fromParameterClassName(String parameterClassName) {
        if (parameterClassName == null) {
            return TEXT;
        }
        
        // Jenkins built-in parameter types
        if (parameterClassName.contains("StringParameterDefinition")) {
            return TEXT;
        } else if (parameterClassName.contains("TextParameterDefinition")) {
            return TEXTAREA;
        } else if (parameterClassName.contains("BooleanParameterDefinition")) {
            return CHECKBOX;
        } else if (parameterClassName.contains("ChoiceParameterDefinition")) {
            return SELECT;
        } else if (parameterClassName.contains("PasswordParameterDefinition")) {
            return PASSWORD;
        } else if (parameterClassName.contains("FileParameterDefinition")) {
            return FILE;
        }
        
        // Active Choices Plugin parameter types
        else if (parameterClassName.contains("ChoiceParameter")) {
            return SELECT;
        } else if (parameterClassName.contains("CascadeChoiceParameter")) {
            return CASCADE_SELECT;
        } else if (parameterClassName.contains("DynamicReferenceParameter")) {
            return DYNAMIC_REFERENCE;
        }
        
        // Default fallback
        return TEXT;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
