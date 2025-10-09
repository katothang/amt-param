package io.kanbanai.amtIntegration.config;

/**
 * Constants for Jenkins plugins and their class names.
 * 
 * This class centralizes all plugin-related constants, especially for
 * Active Choices plugin integration and reflection-based operations.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class PluginConstants {
    
    // Prevent instantiation
    private PluginConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // ========== Plugin Names ==========
    
    /**
     * Active Choices plugin short name
     */
    public static final String ACTIVE_CHOICES_PLUGIN_NAME = "uno-choice";
    
    /**
     * Active Choices plugin display name
     */
    public static final String ACTIVE_CHOICES_PLUGIN_DISPLAY_NAME = "Active Choices";
    
    // ========== Active Choices Class Names ==========
    
    /**
     * Base class for all Active Choices parameters
     */
    public static final String ABSTRACT_SCRIPTABLE_PARAMETER = "org.biouno.unochoice.AbstractScriptableParameter";
    
    /**
     * Choice parameter class name
     */
    public static final String CHOICE_PARAMETER = "org.biouno.unochoice.ChoiceParameter";
    
    /**
     * Cascade choice parameter class name
     */
    public static final String CASCADE_CHOICE_PARAMETER = "org.biouno.unochoice.CascadeChoiceParameter";
    
    /**
     * Dynamic reference parameter class name
     */
    public static final String DYNAMIC_REFERENCE_PARAMETER = "org.biouno.unochoice.DynamicReferenceParameter";
    
    /**
     * Script model class name
     */
    public static final String SCRIPT_CLASS = "org.biouno.unochoice.model.Script";
    
    // ========== Built-in Parameter Class Names ==========
    
    /**
     * String parameter definition class name
     */
    public static final String STRING_PARAMETER_DEFINITION = "StringParameterDefinition";
    
    /**
     * Boolean parameter definition class name
     */
    public static final String BOOLEAN_PARAMETER_DEFINITION = "BooleanParameterDefinition";
    
    /**
     * Choice parameter definition class name
     */
    public static final String CHOICE_PARAMETER_DEFINITION = "ChoiceParameterDefinition";
    
    /**
     * Text parameter definition class name
     */
    public static final String TEXT_PARAMETER_DEFINITION = "TextParameterDefinition";
    
    /**
     * Password parameter definition class name
     */
    public static final String PASSWORD_PARAMETER_DEFINITION = "PasswordParameterDefinition";
    
    /**
     * File parameter definition class name
     */
    public static final String FILE_PARAMETER_DEFINITION = "FileParameterDefinition";
    
    /**
     * Run parameter definition class name
     */
    public static final String RUN_PARAMETER_DEFINITION = "RunParameterDefinition";
    
    // ========== Parameter Type Identifiers ==========
    
    /**
     * Identifier for Active Choices parameters in class names
     */
    public static final String ACTIVE_CHOICES_PACKAGE_IDENTIFIER = "org.biouno.unochoice";
    
    /**
     * Identifier for Choice parameters in class names
     */
    public static final String CHOICE_PARAMETER_IDENTIFIER = "ChoiceParameter";
    
    /**
     * Identifier for Cascade Choice parameters in class names
     */
    public static final String CASCADE_CHOICE_PARAMETER_IDENTIFIER = "CascadeChoiceParameter";
    
    /**
     * Identifier for Dynamic Reference parameters in class names
     */
    public static final String DYNAMIC_REFERENCE_PARAMETER_IDENTIFIER = "DynamicReferenceParameter";
    
    // ========== Method Names for Reflection ==========
    
    /**
     * Method name to get choices from Active Choices parameters
     */
    public static final String METHOD_GET_CHOICES = "getChoices";
    
    /**
     * Method name to get script from Active Choices parameters
     */
    public static final String METHOD_GET_SCRIPT = "getScript";
    
    /**
     * Method name to get referenced parameters
     */
    public static final String METHOD_GET_REFERENCED_PARAMETERS = "getReferencedParameters";
    
    /**
     * Method name to evaluate script
     */
    public static final String METHOD_EVAL = "eval";
    
    /**
     * Method name to get script content
     */
    public static final String METHOD_GET_SCRIPT_TEXT = "getScript";
    
    // ========== Default Values ==========
    
    /**
     * Default fallback value for unknown plugin version
     */
    public static final String UNKNOWN_VERSION = "unknown";
    
    /**
     * Default empty array pattern for cleaning data
     */
    public static final String EMPTY_ARRAY_PATTERN = "\\[\\]";
    
    /**
     * Default parameter separator for parsing
     */
    public static final String PARAMETER_SEPARATOR = ",";
    
    /**
     * Default key-value separator for parsing
     */
    public static final String KEY_VALUE_SEPARATOR = ":";
}
