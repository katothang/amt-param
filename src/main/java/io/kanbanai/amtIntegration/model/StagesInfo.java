package io.kanbanai.amtIntegration.model;

import io.kanbanai.amtIntegration.util.JsonUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class containing comprehensive information about all stages in a pipeline build.
 *
 * This class represents the API response data for stage confirmation status,
 * including build information and a list of all stages with their input status.
 *
 * Flow:
 * 1. Stores build metadata (job name, build number, URL)
 * 2. Contains list of all stages with input information
 * 3. Tracks overall build status
 * 4. Provides JSON serialization for API responses
 * 5. Includes utility methods for stage management
 *
 * @author KanbanAI
 * @since 1.0.4
 */
public class StagesInfo {
    
    /**
     * Job name
     */
    private String jobName;
    
    /**
     * Job full name (including folder path)
     */
    private String jobFullName;
    
    /**
     * Build number
     */
    private int buildNumber;
    
    /**
     * Build URL
     */
    private String buildUrl;
    
    /**
     * Overall build status (RUNNING, SUCCESS, FAILED, ABORTED, etc.)
     */
    private String buildStatus;
    
    /**
     * Whether the build is still running
     */
    private boolean isRunning;
    
    /**
     * List of stages with input information
     */
    private List<StageInfo> stages;
    
    /**
     * Default constructor
     */
    public StagesInfo() {
        this.stages = new ArrayList<>();
        this.isRunning = false;
    }
    
    // Getters and Setters
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
    
    public String getJobFullName() {
        return jobFullName;
    }
    
    public void setJobFullName(String jobFullName) {
        this.jobFullName = jobFullName;
    }
    
    public int getBuildNumber() {
        return buildNumber;
    }
    
    public void setBuildNumber(int buildNumber) {
        this.buildNumber = buildNumber;
    }
    
    public String getBuildUrl() {
        return buildUrl;
    }
    
    public void setBuildUrl(String buildUrl) {
        this.buildUrl = buildUrl;
    }
    
    public String getBuildStatus() {
        return buildStatus;
    }
    
    public void setBuildStatus(String buildStatus) {
        this.buildStatus = buildStatus;
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void setRunning(boolean running) {
        isRunning = running;
    }
    
    public List<StageInfo> getStages() {
        return stages;
    }
    
    public void setStages(List<StageInfo> stages) {
        this.stages = stages != null ? stages : new ArrayList<>();
    }
    
    /**
     * Adds a stage to the list
     *
     * @param stage Stage to add
     */
    public void addStage(StageInfo stage) {
        if (stage != null) {
            this.stages.add(stage);
        }
    }
    
    /**
     * Gets the number of stages
     *
     * @return Number of stages
     */
    public int getStageCount() {
        return stages != null ? stages.size() : 0;
    }
    
    /**
     * Gets the number of pending stages
     *
     * @return Number of stages with pending status
     */
    public int getPendingStageCount() {
        if (stages == null) {
            return 0;
        }
        
        int count = 0;
        for (StageInfo stage : stages) {
            if (stage.isPending()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Checks whether there are any pending stages
     *
     * @return true if at least one stage is pending, false otherwise
     */
    public boolean hasPendingStages() {
        return getPendingStageCount() > 0;
    }
    
    /**
     * Converts object to JSON string
     *
     * @return JSON string representation of the object
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        // Build information
        sb.append("\"jobName\":").append(JsonUtils.toJsonString(jobName)).append(",");
        sb.append("\"jobFullName\":").append(JsonUtils.toJsonString(jobFullName)).append(",");
        sb.append("\"buildNumber\":").append(JsonUtils.toJsonNumber(buildNumber)).append(",");
        sb.append("\"buildUrl\":").append(JsonUtils.toJsonString(buildUrl)).append(",");
        sb.append("\"buildStatus\":").append(JsonUtils.toJsonString(buildStatus)).append(",");
        sb.append("\"isRunning\":").append(JsonUtils.toJsonBoolean(isRunning)).append(",");
        
        // Stages array
        sb.append("\"stages\":[");
        if (stages != null) {
            for (int i = 0; i < stages.size(); i++) {
                sb.append(stages.get(i).toJson());
                if (i < stages.size() - 1) {
                    sb.append(",");
                }
            }
        }
        sb.append("]");
        
        sb.append("}");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "StagesInfo{" +
                "jobName='" + jobName + '\'' +
                ", buildNumber=" + buildNumber +
                ", buildStatus='" + buildStatus + '\'' +
                ", isRunning=" + isRunning +
                ", stageCount=" + getStageCount() +
                ", pendingStageCount=" + getPendingStageCount() +
                '}';
    }
}

