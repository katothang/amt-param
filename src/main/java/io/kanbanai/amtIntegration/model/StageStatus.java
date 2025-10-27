package io.kanbanai.amtIntegration.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration defining status values for pipeline stages.
 *
 * This enum provides type safety for stage status values,
 * replacing the use of String constants throughout the codebase.
 *
 * Each status represents a different state in the pipeline stage lifecycle.
 *
 * Flow:
 * 1. Tracks stage execution state (not started, running, completed)
 * 2. Indicates success or failure outcomes
 * 3. Handles special states like paused for input
 * 4. Provides utility methods for status checking
 *
 * @author KanbanAI
 * @since 1.0.5
 */
public enum StageStatus {
    
    /**
     * Stage has not started yet
     */
    NOT_STARTED("not_started"),
    
    /**
     * Stage is currently running
     */
    RUNNING("running"),
    
    /**
     * Stage completed successfully
     */
    SUCCESS("success"),
    
    /**
     * Stage failed with errors
     */
    FAILED("failed"),
    
    /**
     * Stage was aborted/cancelled
     */
    ABORTED("aborted"),
    
    /**
     * Stage is paused waiting for input
     */
    PAUSED("paused"),
    
    /**
     * Stage is pending user action/approval
     */
    PENDING("pending"),
    
    /**
     * Stage was approved by user
     */
    APPROVED("approved"),
    
    /**
     * Stage completed with warnings (unstable)
     */
    UNSTABLE("unstable"),
    
    /**
     * Stage status is unknown or cannot be determined
     */
    UNKNOWN("unknown");
    
    /**
     * String value of the status (for serialization/deserialization)
     */
    private final String value;

    /**
     * Constructor
     *
     * @param value String value of the status
     */
    StageStatus(String value) {
        this.value = value;
    }

    /**
     * Gets the string value of the status.
     * This annotation ensures Jackson serializes the enum using this value.
     *
     * @return String value
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Finds StageStatus from string value
     *
     * @param value String value to find
     * @return Corresponding StageStatus, or UNKNOWN if not found
     */
    public static StageStatus fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }

        for (StageStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }

        return UNKNOWN; // Default fallback
    }
    
    /**
     * Checks if the stage is in a terminal state (completed, not running anymore)
     *
     * @return true if status is SUCCESS, FAILED, ABORTED, or APPROVED
     */
    public boolean isTerminal() {
        return this == SUCCESS || 
               this == FAILED || 
               this == ABORTED || 
               this == APPROVED;
    }
    
    /**
     * Checks if the stage is in an active state (currently executing or waiting)
     *
     * @return true if status is RUNNING, PAUSED, or PENDING
     */
    public boolean isActive() {
        return this == RUNNING || 
               this == PAUSED || 
               this == PENDING;
    }
    
    /**
     * Checks if the stage requires user interaction
     *
     * @return true if status is PENDING or PAUSED
     */
    public boolean requiresUserAction() {
        return this == PENDING || this == PAUSED;
    }
    
    /**
     * Checks if the stage completed successfully
     *
     * @return true if status is SUCCESS or APPROVED
     */
    public boolean isSuccessful() {
        return this == SUCCESS || this == APPROVED;
    }
    
    /**
     * Checks if the stage failed or was aborted
     *
     * @return true if status is FAILED or ABORTED
     */
    public boolean isFailed() {
        return this == FAILED || this == ABORTED;
    }
    
    @Override
    public String toString() {
        return value;
    }
}

