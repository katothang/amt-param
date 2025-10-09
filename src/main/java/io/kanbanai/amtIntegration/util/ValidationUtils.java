package io.kanbanai.amtIntegration.util;

import io.kanbanai.amtIntegration.config.ValidationConstants;
import io.kanbanai.amtIntegration.config.MessageConstants;

import hudson.model.Job;
import hudson.model.ParameterDefinition;

import java.util.List;

/**
 * Utility class for validation operations across the application.
 * 
 * This utility provides centralized validation logic for parameters,
 * jobs, and other domain objects to ensure data integrity and security.
 * 
 * Flow:
 * 1. Validates parameter definitions and values
 * 2. Checks job permissions and accessibility
 * 3. Validates string lengths and formats
 * 4. Provides security checks for sensitive data
 * 
 * @author KanbanAI
 * @since 1.0.2
 */
public final class ValidationUtils {
    
    // Prevent instantiation
    private ValidationUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Validates that a job is not null and accessible.
     * 
     * @param job the job to validate
     * @throws IllegalArgumentException if job is null
     */
    public static void validateJob(Job<?, ?> job) {
        if (job == null) {
            throw new IllegalArgumentException(MessageConstants.ERROR_INVALID_JOB_PARAMETER);
        }
    }
    
    /**
     * Validates that a parameter definition is not null and has required fields.
     * 
     * @param paramDef the parameter definition to validate
     * @throws IllegalArgumentException if parameter definition is invalid
     */
    public static void validateParameterDefinition(ParameterDefinition paramDef) {
        if (paramDef == null) {
            throw new IllegalArgumentException(MessageConstants.ERROR_INVALID_PARAMETER_DEFINITION);
        }
        
        if (paramDef.getName() == null || paramDef.getName().trim().isEmpty()) {
            throw new IllegalArgumentException(MessageConstants.VALIDATION_PARAMETER_NAME_REQUIRED);
        }
        
        if (!isValidParameterName(paramDef.getName())) {
            throw new IllegalArgumentException(
                String.format(MessageConstants.VALIDATION_INVALID_FORMAT, "parameter name")
            );
        }
    }
    
    /**
     * Validates parameter name according to naming conventions.
     * 
     * @param parameterName the parameter name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidParameterName(String parameterName) {
        if (parameterName == null || parameterName.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = parameterName.trim();
        
        // Check length
        if (trimmed.length() > ValidationConstants.MAX_PARAMETER_NAME_LENGTH) {
            return false;
        }
        
        // Check pattern
        return trimmed.matches(ValidationConstants.PARAMETER_NAME_PATTERN);
    }
    
    /**
     * Validates parameter description length and content.
     * 
     * @param description the description to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidParameterDescription(String description) {
        if (description == null) {
            return true; // null descriptions are acceptable
        }
        
        return description.length() <= ValidationConstants.MAX_PARAMETER_DESCRIPTION_LENGTH;
    }
    
    /**
     * Validates parameter value length and content.
     * 
     * @param value the value to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidParameterValue(String value) {
        if (value == null) {
            return true; // null values are acceptable
        }
        
        return value.length() <= ValidationConstants.MAX_PARAMETER_VALUE_LENGTH;
    }
    
    /**
     * Validates error message length.
     * 
     * @param errorMessage the error message to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return true; // null error messages are acceptable
        }
        
        return errorMessage.length() <= ValidationConstants.MAX_ERROR_MESSAGE_LENGTH;
    }
    
    /**
     * Validates that a list of choices is within acceptable limits.
     * 
     * @param choices the choices list to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidChoicesList(List<String> choices) {
        if (choices == null) {
            return true; // null choices are acceptable
        }
        
        if (choices.size() > ValidationConstants.MAX_CHOICES_COUNT) {
            return false;
        }
        
        // Validate each choice
        for (String choice : choices) {
            if (!isValidParameterValue(choice)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validates that a list of dependencies is within acceptable limits.
     * 
     * @param dependencies the dependencies list to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidDependenciesList(List<String> dependencies) {
        if (dependencies == null) {
            return true; // null dependencies are acceptable
        }
        
        if (dependencies.size() > ValidationConstants.MAX_DEPENDENCIES_COUNT) {
            return false;
        }
        
        // Validate each dependency name
        for (String dependency : dependencies) {
            if (!isValidParameterName(dependency)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if a parameter type is sensitive and should not expose values.
     * 
     * @param parameterType the parameter type to check
     * @return true if sensitive, false otherwise
     */
    public static boolean isSensitiveParameterType(String parameterType) {
        if (parameterType == null) {
            return false;
        }
        
        for (String sensitiveType : ValidationConstants.SENSITIVE_PARAMETER_TYPES) {
            if (parameterType.contains(sensitiveType)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validates job name format.
     * 
     * @param jobName the job name to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidJobName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            return false;
        }
        
        return jobName.matches(ValidationConstants.JOB_NAME_PATTERN);
    }
    
    /**
     * Sanitizes a string for safe usage in logs and error messages.
     * 
     * @param input the input string to sanitize
     * @return sanitized string
     */
    public static String sanitizeForLogging(String input) {
        if (input == null) {
            return "null";
        }
        
        // Remove or replace potentially dangerous characters
        return input.replaceAll("[\r\n\t]", " ")
                   .replaceAll("[\\p{Cntrl}]", "")
                   .trim();
    }
    
    /**
     * Validates that a string is not null or empty.
     * 
     * @param value the string to validate
     * @param fieldName the name of the field for error messages
     * @throws IllegalArgumentException if string is null or empty
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                String.format(MessageConstants.VALIDATION_REQUIRED_FIELD, fieldName)
            );
        }
    }
    
    /**
     * Validates that an object is not null.
     * 
     * @param object the object to validate
     * @param fieldName the name of the field for error messages
     * @throws IllegalArgumentException if object is null
     */
    public static void requireNonNull(Object object, String fieldName) {
        if (object == null) {
            throw new IllegalArgumentException(
                String.format(MessageConstants.VALIDATION_REQUIRED_FIELD, fieldName)
            );
        }
    }
    
    /**
     * Validates request size to prevent DoS attacks.
     * 
     * @param requestSize the size of the request in bytes
     * @return true if within limits, false otherwise
     */
    public static boolean isValidRequestSize(int requestSize) {
        return requestSize <= ValidationConstants.MAX_REQUEST_SIZE_BYTES;
    }
    
    /**
     * Validates that the number of parameters is within acceptable limits.
     * 
     * @param parameterCount the number of parameters
     * @return true if within limits, false otherwise
     */
    public static boolean isValidParameterCount(int parameterCount) {
        return parameterCount >= 0 && parameterCount <= ValidationConstants.MAX_PARAMETERS_PER_JOB;
    }
}
