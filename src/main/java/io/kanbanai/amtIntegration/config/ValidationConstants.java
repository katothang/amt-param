package io.kanbanai.amtIntegration.config;

/**
 * Constants for validation rules, limits, and patterns.
 * 
 * This class centralizes validation-related constants to ensure
 * consistent validation behavior across the application.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class ValidationConstants {
    
    // Prevent instantiation
    private ValidationConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // ========== String Length Limits ==========
    
    /**
     * Maximum length for parameter names
     */
    public static final int MAX_PARAMETER_NAME_LENGTH = 255;
    
    /**
     * Maximum length for parameter descriptions
     */
    public static final int MAX_PARAMETER_DESCRIPTION_LENGTH = 1000;
    
    /**
     * Maximum length for parameter values
     */
    public static final int MAX_PARAMETER_VALUE_LENGTH = 4000;
    
    /**
     * Maximum length for error messages
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    
    /**
     * Maximum number of choices per parameter
     */
    public static final int MAX_CHOICES_COUNT = 1000;
    
    /**
     * Maximum number of dependencies per parameter
     */
    public static final int MAX_DEPENDENCIES_COUNT = 50;
    
    // ========== Validation Patterns ==========
    
    /**
     * Pattern for valid parameter names (Unicode letters, digits, underscore, hyphen)
     * Supports international characters including Vietnamese, Chinese, etc.
     */
    public static final String PARAMETER_NAME_PATTERN = "^[\\p{L}\\p{N}_-]+$";
    
    /**
     * Pattern for valid job names
     */
    public static final String JOB_NAME_PATTERN = "^[a-zA-Z0-9_/-]+$";
    
    /**
     * Pattern for JSON array format
     */
    public static final String JSON_ARRAY_PATTERN = "^\\[.*\\]$";
    
    /**
     * Pattern for empty array brackets
     */
    public static final String EMPTY_BRACKETS_PATTERN = "^\\[\\]*$";
    
    /**
     * Pattern for parameter value parsing (key:value format)
     */
    public static final String PARAMETER_VALUE_PATTERN = "^([^:]+):(.*)$";
    
    // ========== Default Values ==========
    
    /**
     * Default input type when none specified
     */
    public static final String DEFAULT_INPUT_TYPE = "text";
    
    /**
     * Default parameter type when unknown
     */
    public static final String DEFAULT_PARAMETER_TYPE = "StringParameterDefinition";
    
    /**
     * Default empty string value
     */
    public static final String DEFAULT_EMPTY_VALUE = "";
    
    /**
     * Default null representation in JSON
     */
    public static final String DEFAULT_NULL_JSON = "null";
    
    // ========== JSON Validation ==========
    
    /**
     * Characters that need escaping in JSON strings
     */
    public static final String[] JSON_ESCAPE_CHARS = {
        "\\", "\"", "\n", "\r", "\t", "\b", "\f"
    };
    
    /**
     * JSON escape replacements corresponding to JSON_ESCAPE_CHARS
     */
    public static final String[] JSON_ESCAPE_REPLACEMENTS = {
        "\\\\", "\\\"", "\\n", "\\r", "\\t", "\\b", "\\f"
    };
    
    // ========== Timeout Values ==========
    
    /**
     * Default timeout for plugin availability checks (milliseconds)
     */
    public static final long PLUGIN_CHECK_TIMEOUT_MS = 5000;
    
    /**
     * Default timeout for parameter rendering (milliseconds)
     */
    public static final long PARAMETER_RENDER_TIMEOUT_MS = 30000;
    
    /**
     * Default timeout for script evaluation (milliseconds)
     */
    public static final long SCRIPT_EVAL_TIMEOUT_MS = 10000;
    
    // ========== Cache Settings ==========
    
    /**
     * Default cache expiration time for plugin availability (milliseconds)
     */
    public static final long PLUGIN_AVAILABILITY_CACHE_EXPIRY_MS = 300000; // 5 minutes
    
    /**
     * Maximum cache size for parameter definitions
     */
    public static final int MAX_PARAMETER_CACHE_SIZE = 1000;
    
    // ========== Retry Settings ==========
    
    /**
     * Maximum number of retries for failed operations
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;
    
    /**
     * Delay between retry attempts (milliseconds)
     */
    public static final long RETRY_DELAY_MS = 1000;
    
    // ========== Security Settings ==========
    
    /**
     * Maximum request size for parameter parsing (bytes)
     */
    public static final int MAX_REQUEST_SIZE_BYTES = 1048576; // 1MB
    
    /**
     * Maximum number of parameters per job
     */
    public static final int MAX_PARAMETERS_PER_JOB = 100;
    
    /**
     * Sensitive parameter types that should not expose values
     */
    public static final String[] SENSITIVE_PARAMETER_TYPES = {
        "PasswordParameterDefinition",
        "CredentialsParameterDefinition"
    };
}
