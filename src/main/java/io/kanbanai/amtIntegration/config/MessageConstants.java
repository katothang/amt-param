package io.kanbanai.amtIntegration.config;

/**
 * Constants for error messages, log messages, and user-facing text.
 * 
 * This class centralizes all message strings to support internationalization
 * and consistent messaging throughout the application.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class MessageConstants {
    
    // Prevent instantiation
    private MessageConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // ========== Success Messages ==========
    
    /**
     * Success message for API responses
     */
    public static final String SUCCESS_API_MESSAGE = "AMT Parameters API";
    
    /**
     * Success message for parameter rendering
     */
    public static final String SUCCESS_PARAMETERS_RENDERED = "Parameters rendered successfully";
    
    // ========== Error Messages ==========
    
    /**
     * Error message for job not found
     */
    public static final String ERROR_JOB_NOT_FOUND = "Job not found: %s";
    
    /**
     * Error message for access denied
     */
    public static final String ERROR_ACCESS_DENIED = "Access denied to job: %s";
    
    /**
     * Error message for permission denied details
     */
    public static final String ERROR_NO_READ_PERMISSION = "You don't have READ permission for this job";
    
    /**
     * Error message for internal server error
     */
    public static final String ERROR_INTERNAL_SERVER = "Internal server error";
    
    /**
     * Error message for unexpected errors
     */
    public static final String ERROR_UNEXPECTED = "An unexpected error occurred while processing the request: %s";
    
    /**
     * Error message for invalid job parameter
     */
    public static final String ERROR_INVALID_JOB_PARAMETER = "Job parameter cannot be null";
    
    /**
     * Error message for invalid parameter definition
     */
    public static final String ERROR_INVALID_PARAMETER_DEFINITION = "ParameterDefinition cannot be null";
    
    /**
     * Error message for parameter rendering failure
     */
    public static final String ERROR_PARAMETER_RENDERING_FAILED = "Failed to render parameters for job: %s";
    
    /**
     * Error message for Active Choices plugin unavailable
     */
    public static final String ERROR_ACTIVE_CHOICES_UNAVAILABLE = "Active Choices plugin is not available - parameter displayed as text input";

    /**
     * Error message for critical parameter rendering failure
     */
    public static final String ERROR_CRITICAL_PARAMETER_RENDERING = "Critical error when rendering parameters for job %s: %s";

    /**
     * Error message when cannot render parameters
     */
    public static final String ERROR_CANNOT_RENDER_PARAMETERS = "Cannot render parameters for job %s";

    // ========== Warning Messages ==========
    
    /**
     * Warning message for unknown parameter type
     */
    public static final String WARNING_UNKNOWN_PARAMETER_TYPE = "Unknown parameter type: %s - treating as text input";
    
    /**
     * Warning message for parameter rendering error
     */
    public static final String WARNING_PARAMETER_RENDER_ERROR = "Error rendering parameter %s: %s";
    
    /**
     * Warning message for JSON parsing error
     */
    public static final String WARNING_JSON_PARSE_ERROR = "Error parsing JSON body: %s";
    
    /**
     * Warning message for default value retrieval error
     */
    public static final String WARNING_DEFAULT_VALUE_ERROR = "Cannot retrieve default value for parameter %s: %s";
    
    // ========== Info Messages ==========
    
    /**
     * Info message for starting parameter rendering
     */
    public static final String INFO_STARTING_PARAMETER_RENDERING = "Starting parameter rendering for job: %s";
    
    /**
     * Info message for parameters found
     */
    public static final String INFO_PARAMETERS_FOUND = "Found %d parameters in job %s";
    
    /**
     * Info message for parameter rendering completion
     */
    public static final String INFO_PARAMETER_RENDERING_COMPLETE = "Completed rendering %d parameters for job %s";
    
    /**
     * Info message for Active Choices plugin unavailable fallback
     */
    public static final String INFO_ACTIVE_CHOICES_FALLBACK = "Active Choices plugin not available for parameter %s - using fallback";
    
    /**
     * Info message for successful parameter rendering
     */
    public static final String INFO_PARAMETER_RENDERED_SUCCESS = "Successfully rendered %s parameter %s with %d choices and %d dependencies";
    
    // ========== Debug Messages ==========
    
    /**
     * Debug message for parameter value conversion error
     */
    public static final String DEBUG_PARAMETER_VALUE_CONVERSION_ERROR = "Error converting ParameterValue to String: %s";
    
    /**
     * Debug message for Active Choices class loading
     */
    public static final String DEBUG_ACTIVE_CHOICES_CLASS_LOADING = "Attempting to load Active Choices classes";
    
    /**
     * Debug message for plugin availability check
     */
    public static final String DEBUG_PLUGIN_AVAILABILITY_CHECK = "Checking plugin availability: %s";

    /**
     * Debug message for parameter rendering
     */
    public static final String DEBUG_RENDERING_PARAMETER = "Rendering parameter: %s (type: %s)";

    // ========== Validation Messages ==========
    
    /**
     * Validation message for required field
     */
    public static final String VALIDATION_REQUIRED_FIELD = "Field %s is required";
    
    /**
     * Validation message for invalid format
     */
    public static final String VALIDATION_INVALID_FORMAT = "Invalid format for field %s";
    
    /**
     * Validation message for parameter name
     */
    public static final String VALIDATION_PARAMETER_NAME_REQUIRED = "Parameter name is required";
    
    // ========== Display Names ==========
    
    /**
     * Display name for the plugin
     */
    public static final String PLUGIN_DISPLAY_NAME = "AMT Parameters API";
    
    /**
     * Display name for unknown parameter type
     */
    public static final String UNKNOWN_PARAMETER_TYPE = "Unknown";
    
    /**
     * Display name for empty choices
     */
    public static final String NO_CHOICES_AVAILABLE = "No choices available";
    
    /**
     * Display name for empty dependencies
     */
    public static final String NO_DEPENDENCIES = "No dependencies";
}
