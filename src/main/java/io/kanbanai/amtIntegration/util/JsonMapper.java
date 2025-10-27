package io.kanbanai.amtIntegration.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for JSON serialization and deserialization using Jackson ObjectMapper.
 * 
 * This utility provides a centralized way to convert Java objects to/from JSON
 * using Jackson ObjectMapper, which is included in Jenkins core.
 * 
 * Features:
 * - Thread-safe singleton ObjectMapper instance
 * - Configured to ignore null values
 * - Configured to ignore unknown properties during deserialization
 * - Pretty printing disabled for compact JSON output
 * - Proper error handling and logging
 * 
 * @author KanbanAI
 * @since 1.0.3
 */
public final class JsonMapper {
    
    private static final Logger LOGGER = Logger.getLogger(JsonMapper.class.getName());
    
    /**
     * Singleton ObjectMapper instance configured for Jenkins plugin use
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    // Prevent instantiation
    private JsonMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Creates and configures the ObjectMapper instance.
     * 
     * @return configured ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Don't include null values in JSON output
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        // Ignore unknown properties during deserialization
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Disable pretty printing for compact output
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        
        // Don't fail on empty beans
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        return mapper;
    }
    
    /**
     * Gets the singleton ObjectMapper instance.
     * 
     * @return configured ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }
    
    /**
     * Converts a Java object to JSON string.
     * 
     * @param object the object to serialize
     * @return JSON string representation, or null if serialization fails
     */
    public static String toJson(Object object) {
        if (object == null) {
            return "null";
        }
        
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize object to JSON: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converts a Java object to pretty-printed JSON string.
     * 
     * @param object the object to serialize
     * @return pretty-printed JSON string, or null if serialization fails
     */
    public static String toPrettyJson(Object object) {
        if (object == null) {
            return "null";
        }
        
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize object to pretty JSON: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converts JSON string to a Java object of specified type.
     * 
     * @param <T> the type of the object
     * @param json the JSON string to deserialize
     * @param valueType the class of the object type
     * @return deserialized object, or null if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> valueType) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, valueType);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize JSON to " + valueType.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converts JSON string to a Map.
     * 
     * @param json the JSON string to deserialize
     * @return Map representation of JSON, or null if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize JSON to Map: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converts a Java object to a Map.
     * 
     * @param object the object to convert
     * @return Map representation of the object, or null if conversion fails
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            return OBJECT_MAPPER.convertValue(object, Map.class);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to convert object to Map: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates a success response wrapper.
     * 
     * @param data the data to wrap
     * @return JSON string with success wrapper
     */
    public static String createSuccessResponse(Object data) {
        try {
            Map<String, Object> response = Map.of(
                "success", true,
                "data", data
            );
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to create success response: " + e.getMessage(), e);
            return "{\"success\":false,\"error\":\"Failed to serialize response\"}";
        }
    }
    
    /**
     * Creates an error response wrapper.
     * 
     * @param error the error message
     * @param details additional error details
     * @param statusCode HTTP status code
     * @return JSON string with error wrapper
     */
    public static String createErrorResponse(String error, String details, int statusCode) {
        try {
            Map<String, Object> response = Map.of(
                "success", false,
                "error", error != null ? error : "Unknown error",
                "details", details != null ? details : "",
                "statusCode", statusCode
            );
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Failed to create error response: " + e.getMessage(), e);
            return "{\"success\":false,\"error\":\"Internal server error\",\"statusCode\":500}";
        }
    }
    
    /**
     * Validates if a string is valid JSON.
     * 
     * @param json the string to validate
     * @return true if valid JSON, false otherwise
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

