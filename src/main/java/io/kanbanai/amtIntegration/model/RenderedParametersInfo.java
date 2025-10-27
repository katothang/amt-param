package io.kanbanai.amtIntegration.model;

import java.util.ArrayList;
import java.util.List;

import io.kanbanai.amtIntegration.util.JsonMapper;
import io.kanbanai.amtIntegration.util.JsonUtils;
import io.kanbanai.amtIntegration.util.ValidationUtils;
import io.kanbanai.amtIntegration.config.ApiConstants;

/**
 * Model class containing comprehensive information about all parameters of a Jenkins job.
 *
 * This class represents the API response data, including job information
 * and a list of all parameters that have been rendered with complete information.
 *
 * Instead of using generic Object or Map, this class provides
 * type safety and better IDE support.
 *
 * Flow:
 * 1. Stores job metadata (name, URL, build URL)
 * 2. Contains list of all rendered parameters
 * 3. Tracks plugin availability information
 * 4. Provides JSON serialization for API responses
 * 5. Includes utility methods for parameter management
 *
 * @author KanbanAI
 * @since 1.0.2
 */
public class RenderedParametersInfo {
    
    /**
     * Short name of the job (does not include folder path)
     */
    private String jobName;

    /**
     * Full name of the job (includes folder path if any)
     * Example: "folder1/folder2/jobName"
     */
    private String jobFullName;

    /**
     * Relative URL of the job in Jenkins
     * Example: "job/folder1/job/jobName/"
     */
    private String jobUrl;

    /**
     * URL to trigger build with parameters
     * Example: "job/folder1/job/jobName/buildWithParameters"
     */
    private String buildWithParametersUrl;

    /**
     * List of all rendered job parameters
     */
    private List<RenderedParameterInfo> parameters;

    /**
     * Information about Active Choices plugin availability
     */
    private boolean activeChoicesPluginAvailable;

    /**
     * Version of Active Choices plugin (if available)
     */
    private String activeChoicesPluginVersion;
    
    /**
     * Default constructor
     */
    public RenderedParametersInfo() {
        this.parameters = new ArrayList<>();
    }

    /**
     * Constructor with basic job information
     *
     * @param jobName Job name
     * @param jobFullName Full job name
     * @param jobUrl Job URL
     */
    public RenderedParametersInfo(String jobName, String jobFullName, String jobUrl) {
        this();
        this.jobName = jobName;
        this.jobFullName = jobFullName;
        this.jobUrl = jobUrl;
        this.buildWithParametersUrl = jobUrl + ApiConstants.BUILD_WITH_PARAMETERS_ENDPOINT;
    }
    
    // Getters and Setters
    
    public String getJobName() {
        return jobName;
    }
    
    public void setJobName(String jobName) {
        ValidationUtils.requireNonEmpty(jobName, "jobName");
        this.jobName = jobName;
    }
    
    public String getJobFullName() {
        return jobFullName;
    }
    
    public void setJobFullName(String jobFullName) {
        ValidationUtils.requireNonEmpty(jobFullName, "jobFullName");
        this.jobFullName = jobFullName;
    }
    
    public String getJobUrl() {
        return jobUrl;
    }
    
    public void setJobUrl(String jobUrl) {
        ValidationUtils.requireNonEmpty(jobUrl, "jobUrl");
        this.jobUrl = jobUrl;
        // Automatically update buildWithParametersUrl when jobUrl changes
        if (jobUrl != null) {
            this.buildWithParametersUrl = jobUrl + ApiConstants.BUILD_WITH_PARAMETERS_ENDPOINT;
        }
    }
    
    public String getBuildWithParametersUrl() {
        return buildWithParametersUrl;
    }
    
    public void setBuildWithParametersUrl(String buildWithParametersUrl) {
        this.buildWithParametersUrl = buildWithParametersUrl;
    }
    
    public List<RenderedParameterInfo> getParameters() {
        return parameters;
    }
    
    public void setParameters(List<RenderedParameterInfo> parameters) {
        if (parameters != null && !ValidationUtils.isValidParameterCount(parameters.size())) {
            throw new IllegalArgumentException("Too many parameters");
        }
        this.parameters = parameters != null ? parameters : new ArrayList<>();
    }

    /**
     * Adds a parameter to the list
     *
     * @param parameter Parameter to add
     */
    public void addParameter(RenderedParameterInfo parameter) {
        if (parameter != null) {
            if (!ValidationUtils.isValidParameterCount(this.parameters.size() + 1)) {
                throw new IllegalArgumentException("Too many parameters");
            }
            this.parameters.add(parameter);
        }
    }
    
    public boolean isActiveChoicesPluginAvailable() {
        return activeChoicesPluginAvailable;
    }
    
    public void setActiveChoicesPluginAvailable(boolean activeChoicesPluginAvailable) {
        this.activeChoicesPluginAvailable = activeChoicesPluginAvailable;
    }
    
    public String getActiveChoicesPluginVersion() {
        return activeChoicesPluginVersion;
    }
    
    public void setActiveChoicesPluginVersion(String activeChoicesPluginVersion) {
        this.activeChoicesPluginVersion = activeChoicesPluginVersion;
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

    /**
     * Gets the number of parameters
     *
     * @return Number of parameters in the job
     */
    public int getParameterCount() {
        return parameters != null ? parameters.size() : 0;
    }

    /**
     * Checks whether the job has parameters
     *
     * @return true if job has at least 1 parameter, false otherwise
     */
    public boolean hasParameters() {
        return getParameterCount() > 0;
    }

    /**
     * Finds parameter by name
     *
     * @param parameterName Parameter name to find
     * @return RenderedParameterInfo if found, null otherwise
     */
    public RenderedParameterInfo findParameterByName(String parameterName) {
        if (parameterName == null || parameters == null) {
            return null;
        }

        return parameters.stream()
                .filter(param -> parameterName.equals(param.getName()))
                .findFirst()
                .orElse(null);
    }
    
    @Override
    public String toString() {
        return "RenderedParametersInfo{" +
                "jobName='" + jobName + '\'' +
                ", jobFullName='" + jobFullName + '\'' +
                ", parameterCount=" + getParameterCount() +
                ", activeChoicesPluginAvailable=" + activeChoicesPluginAvailable +
                '}';
    }
}
