package io.kanbanai.amtIntegration.config;

/**
 * Constants for API endpoints, URL patterns, and HTTP-related configurations.
 * 
 * This class centralizes all API-related constants to avoid hardcoding values
 * throughout the codebase and make maintenance easier.
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class ApiConstants {
    
    // Prevent instantiation
    private ApiConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // ========== API Endpoints ==========
    
    /**
     * Base URL name for the plugin API endpoints
     */
    public static final String API_URL_NAME = "amt-integration";

    /**
     * Plugin display name
     */
    public static final String PLUGIN_DISPLAY_NAME = "AMT Parameters API";
    
    /**
     * API endpoint for retrieving job parameters
     */
    public static final String API_ENDPOINT = "api";
    
    /**
     * Jenkins build with parameters endpoint suffix
     */
    public static final String BUILD_WITH_PARAMETERS_ENDPOINT = "buildWithParameters";
    
    // ========== HTTP Status Codes ==========
    
    /**
     * HTTP status code for successful requests
     */
    public static final int HTTP_OK = 200;
    
    /**
     * HTTP status code for bad request
     */
    public static final int HTTP_BAD_REQUEST = 400;
    
    /**
     * HTTP status code for forbidden access
     */
    public static final int HTTP_FORBIDDEN = 403;
    
    /**
     * HTTP status code for not found
     */
    public static final int HTTP_NOT_FOUND = 404;
    
    /**
     * HTTP status code for internal server error
     */
    public static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    
    // ========== Content Types ==========
    
    /**
     * JSON content type for HTTP responses
     */
    public static final String CONTENT_TYPE_JSON = "application/json";
    
    /**
     * Character encoding for HTTP responses
     */
    public static final String CHARACTER_ENCODING_UTF8 = "UTF-8";
    
    // ========== Request Parameters ==========
    
    /**
     * Query parameter name for job name
     */
    public static final String PARAM_JOB = "job";
    
    /**
     * Query parameter name for parameter values
     */
    public static final String PARAM_PARAMS = "params";
    
    // ========== URL Patterns ==========
    
    /**
     * URL pattern for job endpoints
     */
    public static final String JOB_URL_PATTERN = "/job/%s/";
    
    /**
     * URL pattern for API usage instructions
     */
    public static final String API_USAGE_PATTERN = "Use /job/%s/amt-integration/api?params=param1:value1,param2:value2";
    
    /**
     * URL pattern for parameters endpoint
     */
    public static final String PARAMETERS_ENDPOINT_PATTERN = "%sapi?params=param1:value1,param2:value2";
    
    // ========== JSON Field Names ==========
    
    /**
     * JSON field name for success status
     */
    public static final String JSON_FIELD_SUCCESS = "success";
    
    /**
     * JSON field name for error status
     */
    public static final String JSON_FIELD_ERROR = "error";
    
    /**
     * JSON field name for message
     */
    public static final String JSON_FIELD_MESSAGE = "message";
    
    /**
     * JSON field name for details
     */
    public static final String JSON_FIELD_DETAILS = "details";
    
    /**
     * JSON field name for job name
     */
    public static final String JSON_FIELD_JOB_NAME = "jobName";
    
    /**
     * JSON field name for endpoints
     */
    public static final String JSON_FIELD_ENDPOINTS = "endpoints";
    
    /**
     * JSON field name for parameters
     */
    public static final String JSON_FIELD_PARAMETERS = "parameters";
    
    /**
     * JSON field name for usage information
     */
    public static final String JSON_FIELD_USAGE = "usage";
}
