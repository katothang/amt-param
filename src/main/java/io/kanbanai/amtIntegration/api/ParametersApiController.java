package io.kanbanai.amtIntegration.api;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import io.kanbanai.amtIntegration.service.ParameterRenderingService;
import io.kanbanai.amtIntegration.service.PluginAvailabilityService;
import io.kanbanai.amtIntegration.model.RenderedParametersInfo;
import io.kanbanai.amtIntegration.config.ApiConstants;
import io.kanbanai.amtIntegration.config.MessageConstants;
import io.kanbanai.amtIntegration.util.ParameterParsingUtils;
import io.kanbanai.amtIntegration.util.JsonUtils;
import io.kanbanai.amtIntegration.util.ValidationUtils;

/**
 * REST API Controller for retrieving Jenkins job parameter information.
 *
 * This controller provides REST API endpoints to retrieve comprehensive information
 * about Jenkins job parameters, similar to the "Build with Parameters" screen in Jenkins UI.
 *
 * Architecture (v1.0.2+):
 * - Controller handles HTTP request/response and validation only
 * - Business logic is delegated to Service classes
 * - Supports graceful fallback when Active Choices plugin is unavailable
 * - Uses typed models instead of generic Objects
 *
 * Key Features:
 * - Retrieves all job parameters (built-in and Active Choices)
 * - Renders dynamic parameters with actual values
 * - Handles cascade parameters (parameters dependent on each other)
 * - Supports all parameter types: String, Boolean, Choice, Text, Password, Active Choices, etc.
 * - Checks plugin availability before usage
 *
 * Flow:
 * 1. Receives HTTP request at /amt-integration/ endpoint
 * 2. Validates request parameters and permissions
 * 3. Delegates parameter rendering to ParameterRenderingService
 * 4. Returns JSON response with parameter information
 * 5. Handles errors gracefully with appropriate HTTP status codes
 *
 * @author KanbanAI
 * @since 1.0.2
 */
@Extension
public class ParametersApiController implements RootAction {

    private static final Logger LOGGER = Logger.getLogger(ParametersApiController.class.getName());

    // Dependencies - using Singleton pattern for lightweight DI
    private final ParameterRenderingService parameterService;
    private final PluginAvailabilityService pluginService;

    /**
     * Constructor initializes service dependencies.
     */
    public ParametersApiController() {
        this.parameterService = ParameterRenderingService.getInstance();
        this.pluginService = PluginAvailabilityService.getInstance();
    }

    /**
     * {@inheritDoc}
     *
     * Icon name for the plugin in Jenkins UI.
     * Returns null to hide from main navigation.
     *
     * @return null to hide from navigation
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Display name of the plugin, used for logging and debugging.
     *
     * @return Display name of the plugin
     */
    @Override
    public String getDisplayName() {
        return MessageConstants.PLUGIN_DISPLAY_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * URL name defines the endpoint path for the plugin.
     * API will be accessible at: {JENKINS_URL}/amt-integration/
     * 
     * Note: From version 1.0.3+, prefer using the new URL pattern:
     * {JENKINS_URL}/job/{JOB_NAME}/amt-integration/api?params=param1:value1,param2:value2
     * through ParametersJobAction
     *
     * @return URL path segment for the plugin
     */
    @Override
    public String getUrlName() {
        return ApiConstants.API_URL_NAME;
    }

    /**
     * Handles GET requests to retrieve job parameters.
     * 
     * URL: {JENKINS_URL}/amt-integration/get?job=JOB_NAME&params=param1:value1,param2:value2
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    public void doGet(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing GET request for parameters API");
        
        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter
            String jobName = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST, 
                    "Missing required parameter: job", 
                    "Please provide the job name using ?job=JOB_NAME");
                return;
            }

            // Find the job
            Job<?, ?> job = findJobByName(jobName.trim());
            if (job == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND, 
                    String.format(MessageConstants.ERROR_JOB_NOT_FOUND, jobName),
                    "Please check the job name and ensure it exists");
                return;
            }

            // Check permissions
            if (!checkJobReadPermission(job)) {
                sendErrorResponse(rsp, ApiConstants.HTTP_FORBIDDEN, 
                    String.format(MessageConstants.ERROR_ACCESS_DENIED, job.getName()),
                    MessageConstants.ERROR_NO_READ_PERMISSION);
                return;
            }

            // Parse parameter values from query string
            String paramsStr = req.getParameter(ApiConstants.PARAM_PARAMS);
            Map<String, String> currentValues = ParameterParsingUtils.parseParameterValues(paramsStr);

            // Delegate to service layer to render parameters
            RenderedParametersInfo parametersInfo = parameterService.renderJobParameters(job, currentValues);

            // Return JSON response
            sendSuccessResponse(rsp, parametersInfo);

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doGet: " + e.getMessage(), e);
            
            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR, 
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    /**
     * Finds a job by name, supporting both simple names and full paths.
     * 
     * @param jobName the job name to find
     * @return the job if found, null otherwise
     */
    private Job<?, ?> findJobByName(String jobName) {
        if (!ValidationUtils.isValidJobName(jobName)) {
            return null;
        }
        
        Jenkins jenkins = Jenkins.get();
        return jenkins.getItemByFullName(jobName, Job.class);
    }

    /**
     * Checks if the current user has read permission for the job.
     * 
     * @param job the job to check
     * @return true if user has read permission, false otherwise
     */
    private boolean checkJobReadPermission(Job<?, ?> job) {
        try {
            return job.hasPermission(Job.READ);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking job permissions: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Sends a successful JSON response.
     * 
     * @param rsp HTTP response
     * @param parametersInfo the parameters information to send
     * @throws IOException if I/O error occurs
     */
    private void sendSuccessResponse(StaplerResponse rsp, RenderedParametersInfo parametersInfo) throws IOException {
        rsp.setStatus(ApiConstants.HTTP_OK);
        rsp.getWriter().write(parametersInfo.toJson());
    }

    /**
     * Sends an error JSON response.
     * 
     * @param rsp HTTP response
     * @param statusCode HTTP status code
     * @param message error message
     * @param details error details
     * @throws IOException if I/O error occurs
     */
    private void sendErrorResponse(StaplerResponse rsp, int statusCode, String message, String details) throws IOException {
        rsp.setStatus(statusCode);
        
        String errorJson = JsonUtils.createJsonObject(
            ApiConstants.JSON_FIELD_ERROR, "true",
            ApiConstants.JSON_FIELD_MESSAGE, ValidationUtils.sanitizeForLogging(message),
            ApiConstants.JSON_FIELD_DETAILS, ValidationUtils.sanitizeForLogging(details)
        );
        
        rsp.getWriter().write(errorJson);
    }
}
