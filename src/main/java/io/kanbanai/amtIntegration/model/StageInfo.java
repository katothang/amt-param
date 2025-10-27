package io.kanbanai.amtIntegration.model;

import io.kanbanai.amtIntegration.util.JsonMapper;
import io.kanbanai.amtIntegration.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class containing information about a pipeline stage that requires input/confirmation.
 *
 * This class represents a stage in a Jenkins Pipeline that has an input step,
 * including information about whether the input has been submitted or is still pending.
 *
 * Flow:
 * 1. Stores stage identification (id, name)
 * 2. Tracks input status (pending, approved, aborted)
 * 3. Contains input details (message, submitter, parameters)
 * 4. Provides JSON serialization for API responses
 *
 * @author KanbanAI
 * @since 1.0.4
 */
public class StageInfo {
    
    /**
     * Unique identifier for the stage/input
     */
    private String id;

    /**
     * Input ID (if this stage has an input step)
     */
    private String inputId;

    /**
     * Name of the stage
     */
    private String name;

    /**
     * Status of the stage
     */
    private StageStatus status;
    
    /**
     * Message displayed for the input prompt
     */
    private String message;
    
    /**
     * User or group who can submit the input
     */
    private String submitter;
    
    /**
     * URL to submit the input
     */
    private String submitUrl;
    
    /**
     * URL to abort the input
     */
    private String abortUrl;

    /**
     * Text for the proceed button (e.g., "Proceed", "Approve")
     */
    private String proceedText;

    /**
     * URL to proceed/submit the input (wfapi format)
     */
    private String proceedUrl;

    /**
     * Whether this stage has been executed
     */
    private boolean executed;

    /**
     * List of input parameters required for this stage
     */
    private List<InputParameterInfo> parameters;

    /**
     * Log text for this stage
     */
    private String logs;

    /**
     * Start time of the stage in milliseconds
     */
    private Long startTimeMillis;

    /**
     * Duration of the stage in milliseconds
     */
    private Long durationMillis;

    /**
     * Default constructor
     */
    public StageInfo() {
        this.status = StageStatus.NOT_STARTED;
        this.executed = false;
        this.parameters = new ArrayList<>();
        this.logs = "";
    }

    /**
     * Constructor with basic information
     *
     * @param id Stage/input ID
     * @param name Stage name
     * @param status Stage status
     */
    public StageInfo(String id, String name, StageStatus status) {
        this();
        this.id = id;
        this.name = name;
        this.status = status;
    }

    /**
     * Constructor with basic information (backward compatibility)
     *
     * @param id Stage/input ID
     * @param name Stage name
     * @param status Stage status as string
     * @deprecated Use constructor with StageStatus enum instead
     */
    @Deprecated
    public StageInfo(String id, String name, String status) {
        this(id, name, StageStatus.fromValue(status));
    }
    
    // Getters and Setters
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getInputId() {
        return inputId;
    }

    public void setInputId(String inputId) {
        this.inputId = inputId;
    }

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public StageStatus getStatus() {
        return status;
    }

    /**
     * Gets the status as string value (for backward compatibility)
     *
     * @return Status string value
     */
    public String getStatusValue() {
        return status != null ? status.getValue() : null;
    }

    public void setStatus(StageStatus status) {
        this.status = status;
    }

    /**
     * Sets the status from string value (for backward compatibility)
     *
     * @param status Status string value
     * @deprecated Use setStatus(StageStatus) instead
     */
    @Deprecated
    public void setStatus(String status) {
        this.status = StageStatus.fromValue(status);
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getSubmitter() {
        return submitter;
    }
    
    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }
    
    public String getSubmitUrl() {
        return submitUrl;
    }
    
    public void setSubmitUrl(String submitUrl) {
        this.submitUrl = submitUrl;
    }
    
    public String getAbortUrl() {
        return abortUrl;
    }
    
    public void setAbortUrl(String abortUrl) {
        this.abortUrl = abortUrl;
    }

    public String getProceedText() {
        return proceedText;
    }

    public void setProceedText(String proceedText) {
        this.proceedText = proceedText;
    }

    public String getProceedUrl() {
        return proceedUrl;
    }

    public void setProceedUrl(String proceedUrl) {
        this.proceedUrl = proceedUrl;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public List<InputParameterInfo> getParameters() {
        return parameters;
    }

    public void setParameters(List<InputParameterInfo> parameters) {
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    /**
     * Adds a parameter to the list
     *
     * @param parameter Parameter to add
     */
    public void addParameter(InputParameterInfo parameter) {
        if (parameter != null) {
            this.parameters.add(parameter);
        }
    }

    public String getLogs() {
        return logs;
    }

    public void setLogs(String logs) {
        this.logs = logs != null ? logs : "";
    }

    public Long getStartTimeMillis() {
        return startTimeMillis;
    }

    public void setStartTimeMillis(Long startTimeMillis) {
        this.startTimeMillis = startTimeMillis;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    /**
     * Checks whether the stage is pending input
     *
     * @return true if status is PENDING, false otherwise
     */
    public boolean isPending() {
        return status == StageStatus.PENDING;
    }

    /**
     * Checks whether the stage has been approved
     *
     * @return true if status is APPROVED, false otherwise
     */
    public boolean isApproved() {
        return status == StageStatus.APPROVED;
    }

    /**
     * Checks whether the stage has been aborted
     *
     * @return true if status is ABORTED, false otherwise
     */
    public boolean isAborted() {
        return status == StageStatus.ABORTED;
    }

    /**
     * Checks whether the stage is running
     *
     * @return true if status is RUNNING, false otherwise
     */
    public boolean isRunning() {
        return status == StageStatus.RUNNING;
    }

    /**
     * Checks whether the stage completed successfully
     *
     * @return true if status is SUCCESS or APPROVED, false otherwise
     */
    public boolean isSuccessful() {
        return status != null && status.isSuccessful();
    }

    /**
     * Checks whether the stage failed
     *
     * @return true if status is FAILED or ABORTED, false otherwise
     */
    public boolean isFailed() {
        return status != null && status.isFailed();
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
        return "StageInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", executed=" + executed +
                ", isPending=" + isPending() +
                '}';
    }
    
    /**
     * Builder class for creating StageInfo instances.
     */
    public static class Builder {
        private final StageInfo stageInfo;
        
        /**
         * Creates a new builder with required fields.
         *
         * @param id Stage ID (required)
         * @param name Stage name (required)
         */
        public Builder(String id, String name) {
            this.stageInfo = new StageInfo();
            this.stageInfo.setId(id);
            this.stageInfo.setName(name);
        }
        
        public Builder status(StageStatus status) {
            this.stageInfo.setStatus(status);
            return this;
        }

        /**
         * Sets status from string value (backward compatibility)
         *
         * @param status Status string value
         * @return Builder instance
         * @deprecated Use status(StageStatus) instead
         */
        @Deprecated
        public Builder status(String status) {
            this.stageInfo.setStatus(StageStatus.fromValue(status));
            return this;
        }
        
        public Builder message(String message) {
            this.stageInfo.setMessage(message);
            return this;
        }
        
        public Builder submitter(String submitter) {
            this.stageInfo.setSubmitter(submitter);
            return this;
        }
        
        public Builder submitUrl(String submitUrl) {
            this.stageInfo.setSubmitUrl(submitUrl);
            return this;
        }
        
        public Builder abortUrl(String abortUrl) {
            this.stageInfo.setAbortUrl(abortUrl);
            return this;
        }

        public Builder proceedText(String proceedText) {
            this.stageInfo.setProceedText(proceedText);
            return this;
        }

        public Builder proceedUrl(String proceedUrl) {
            this.stageInfo.setProceedUrl(proceedUrl);
            return this;
        }

        public Builder executed(boolean executed) {
            this.stageInfo.setExecuted(executed);
            return this;
        }

        public Builder parameters(List<InputParameterInfo> parameters) {
            this.stageInfo.setParameters(parameters);
            return this;
        }

        public Builder addParameter(InputParameterInfo parameter) {
            this.stageInfo.addParameter(parameter);
            return this;
        }

        public Builder logs(String logs) {
            this.stageInfo.setLogs(logs);
            return this;
        }

        public Builder startTimeMillis(Long startTimeMillis) {
            this.stageInfo.setStartTimeMillis(startTimeMillis);
            return this;
        }

        public Builder durationMillis(Long durationMillis) {
            this.stageInfo.setDurationMillis(durationMillis);
            return this;
        }

        public StageInfo build() {
            return this.stageInfo;
        }
    }
}

