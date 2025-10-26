package io.kanbanai.amtIntegration.api;

import hudson.Extension;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.GET;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.kohsuke.stapler.verb.POST;
import java.io.BufferedReader;

import io.kanbanai.amtIntegration.service.ParameterRenderingService;
import io.kanbanai.amtIntegration.service.PluginAvailabilityService;
import io.kanbanai.amtIntegration.service.WorkflowStageService;
import io.kanbanai.amtIntegration.model.RenderedParametersInfo;
import io.kanbanai.amtIntegration.model.StagesInfo;
import io.kanbanai.amtIntegration.model.StageInfo;
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
    private final WorkflowStageService stageService;

    /**
     * Constructor initializes service dependencies.
     */
    public ParametersApiController() {
        this.parameterService = ParameterRenderingService.getInstance();
        this.pluginService = PluginAvailabilityService.getInstance();
        this.stageService = WorkflowStageService.getInstance();
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
     * URL: {JENKINS_URL}/amt-integration/get?job=JOB_URL&params=param1:value1,param2:value2
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

            // Get and validate job parameter (can be job URL or job name)
            String jobParam = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobParam == null || jobParam.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL or name using ?job=JOB_URL or ?job=JOB_NAME");
                return;
            }

            // Parse job name from URL if it's a full URL, otherwise use as-is
            String jobName = jobParam.trim();
            if (jobParam.contains("/job/")) {
                // It's a full Jenkins URL, extract job name
                jobName = extractJobNameFromUrl(jobParam);
                if (jobName == null || jobName.trim().isEmpty()) {
                    sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                        "Invalid job URL format",
                        "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                    return;
                }
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

    // ========== STAGE API METHODS ==========

    /**
     * Handles GET requests to retrieve stage information for a build.
     *
     * URL: {JENKINS_URL}/amt-integration/stages?job=JOB_URL&build=BUILD_NUMBER
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @GET
    public void doStages(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing GET request for stages API");

        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter (job URL)
            String jobUrl = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse job name from URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // Get and validate build parameter
            String buildNumberStr = req.getParameter("build");
            if (buildNumberStr == null || buildNumberStr.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: build",
                    "Please provide the build number using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse build number
            int buildNumber;
            try {
                buildNumber = Integer.parseInt(buildNumberStr.trim());
            } catch (NumberFormatException e) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid build number: " + buildNumberStr,
                    "Build number must be a valid integer");
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

            // Find the build
            Run<?, ?> run = job.getBuildByNumber(buildNumber);
            if (run == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Build not found: " + jobName + " #" + buildNumber,
                    "Please check the build number and ensure it exists");
                return;
            }

            // Get stages information from service
            StagesInfo stagesInfo = stageService.getStagesInfo(run);

            // Return JSON response
            sendStagesSuccessResponse(rsp, stagesInfo);

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doStages: " + e.getMessage(), e);

            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR,
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    /**
     * Handles GET requests to retrieve all stages information with logs for a build.
     *
     * URL: {JENKINS_URL}/amt-integration/allstages?job=JOB_URL&build=BUILD_NUMBER
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @GET
    public void doAllstages(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing GET request for allstages API");

        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter (job URL)
            String jobUrl = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse job name from URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // Get and validate build parameter
            String buildNumberStr = req.getParameter("build");
            if (buildNumberStr == null || buildNumberStr.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: build",
                    "Please provide the build number using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse build number
            int buildNumber;
            try {
                buildNumber = Integer.parseInt(buildNumberStr.trim());
            } catch (NumberFormatException e) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid build number: " + buildNumberStr,
                    "Build number must be a valid integer");
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

            // Find the build
            Run<?, ?> run = job.getBuildByNumber(buildNumber);
            if (run == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Build not found: " + jobName + " #" + buildNumber,
                    "Please check the build number and ensure it exists");
                return;
            }

            // Get all stages with logs from service
            StagesInfo stagesInfo = stageService.getAllStagesWithLogs(run);

            // Return JSON response
            sendStagesSuccessResponse(rsp, stagesInfo);

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doAllstages: " + e.getMessage(), e);

            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR,
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    /**
     * Handles POST requests to submit input for a build.
     *
     * URL: {JENKINS_URL}/amt-integration/submit?job=JOB_URL&build=BUILD_NUMBER
     *
     * Request body (JSON):
     * {
     *   "inputId": "59ceebb6391a40b46b62905f024cf0d2",
     *   "parameters": {
     *     "TEXT_PARAM": "my value",
     *     "BOOL_PARAM": true,
     *     "CHOICE_PARAM": "option1"
     *   }
     * }
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @POST
    public void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing POST request for submit API");

        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter (job URL)
            String jobUrl = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse job name from URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // Get and validate build parameter
            String buildNumberStr = req.getParameter("build");
            if (buildNumberStr == null || buildNumberStr.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: build",
                    "Please provide the build number using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse build number
            int buildNumber;
            try {
                buildNumber = Integer.parseInt(buildNumberStr.trim());
            } catch (NumberFormatException e) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid build number: " + buildNumberStr,
                    "Build number must be a valid integer");
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

            // Find the build
            Run<?, ?> run = job.getBuildByNumber(buildNumber);
            if (run == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Build not found: " + jobName + " #" + buildNumber,
                    "Please check the build number and ensure it exists");
                return;
            }

            // Parse JSON body
            Map<String, Object> requestData = parseJsonBody(req);
            LOGGER.log(Level.INFO, "Parsed request data: " + requestData);

            // Get inputId
            String inputId = (String) requestData.get("inputId");
            if (inputId == null || inputId.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required field: inputId",
                    "Please provide the inputId in the request body");
                return;
            }

            LOGGER.log(Level.INFO, "Input ID: " + inputId);

            // Get parameters
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) requestData.get("parameters");
            if (parameters == null) {
                parameters = new HashMap<>();
            }

            LOGGER.log(Level.INFO, "Parameters: " + parameters);

            // Submit input using service
            boolean success = stageService.submitInput(run, inputId, parameters);

            if (success) {
                sendSubmitSuccessResponse(rsp, inputId);
            } else {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Input not found",
                    "Input with ID " + inputId + " not found or already processed");
            }

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doSubmit: " + e.getMessage(), e);

            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR,
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    /**
     * Handles POST requests to abort input for a build.
     *
     * URL: {JENKINS_URL}/amt-integration/abort?job=JOB_URL&build=BUILD_NUMBER
     *
     * Request body (JSON):
     * {
     *   "inputId": "59ceebb6391a40b46b62905f024cf0d2"
     * }
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @POST
    public void doAbort(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing POST request for abort API");

        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter (job URL)
            String jobUrl = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse job name from URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // Get and validate build parameter
            String buildNumberStr = req.getParameter("build");
            if (buildNumberStr == null || buildNumberStr.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: build",
                    "Please provide the build number using ?job=JOB_URL&build=BUILD_NUMBER");
                return;
            }

            // Parse build number
            int buildNumber;
            try {
                buildNumber = Integer.parseInt(buildNumberStr.trim());
            } catch (NumberFormatException e) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid build number: " + buildNumberStr,
                    "Build number must be a valid integer");
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

            // Find the build
            Run<?, ?> run = job.getBuildByNumber(buildNumber);
            if (run == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Build not found: " + jobName + " #" + buildNumber,
                    "Please check the build number and ensure it exists");
                return;
            }

            // Parse JSON body
            Map<String, Object> requestData = parseJsonBody(req);

            // Get inputId
            String inputId = (String) requestData.get("inputId");
            if (inputId == null || inputId.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required field: inputId",
                    "Please provide the inputId in the request body");
                return;
            }

            // Abort input using service
            boolean success = stageService.abortInput(run, inputId);

            if (success) {
                sendAbortSuccessResponse(rsp, inputId);
            } else {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Input not found",
                    "Input with ID " + inputId + " not found or already processed");
            }

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doAbort: " + e.getMessage(), e);

            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR,
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    /**
     * Handles GET requests to retrieve log for a specific stage.
     *
     * URL: {JENKINS_URL}/amt-integration/stagelog?job=JOB_URL&build=BUILD_NUMBER&stageId=STAGE_ID
     *
     * @param req HTTP request
     * @param rsp HTTP response
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @GET
    public void doStagelog(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        LOGGER.log(Level.INFO, "Processing GET request for stagelog API");

        try {
            // Set response headers
            rsp.setContentType(ApiConstants.CONTENT_TYPE_JSON);
            rsp.setCharacterEncoding(ApiConstants.CHARACTER_ENCODING_UTF8);

            // Get and validate job parameter (job URL)
            String jobUrl = req.getParameter(ApiConstants.PARAM_JOB);
            if (jobUrl == null || jobUrl.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: job",
                    "Please provide the job URL using ?job=JOB_URL&build=BUILD_NUMBER&stageId=STAGE_ID");
                return;
            }

            // Parse job name from URL
            String jobName = extractJobNameFromUrl(jobUrl);
            if (jobName == null || jobName.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid job URL format",
                    "Expected format: https://jenkins.example.com/job/jobName/ or https://jenkins.example.com/job/folder/job/jobName/");
                return;
            }

            // Get and validate build parameter
            String buildNumberStr = req.getParameter("build");
            if (buildNumberStr == null || buildNumberStr.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: build",
                    "Please provide the build number using ?job=JOB_URL&build=BUILD_NUMBER&stageId=STAGE_ID");
                return;
            }

            // Parse build number
            int buildNumber;
            try {
                buildNumber = Integer.parseInt(buildNumberStr.trim());
            } catch (NumberFormatException e) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Invalid build number: " + buildNumberStr,
                    "Build number must be a valid integer");
                return;
            }

            // Get and validate stageId parameter
            String stageId = req.getParameter("stageId");
            if (stageId == null || stageId.trim().isEmpty()) {
                sendErrorResponse(rsp, ApiConstants.HTTP_BAD_REQUEST,
                    "Missing required parameter: stageId",
                    "Please provide the stageId query parameter");
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

            // Find the build
            Run<?, ?> run = job.getBuildByNumber(buildNumber);
            if (run == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Build not found: " + jobName + " #" + buildNumber,
                    "Please check the build number and ensure it exists");
                return;
            }

            // Get all stages to find the requested one
            StagesInfo stagesInfo = stageService.getAllStagesWithLogs(run);

            // Find the stage with matching ID
            StageInfo targetStage = null;
            for (StageInfo stage : stagesInfo.getStages()) {
                if (stageId.equals(stage.getId())) {
                    targetStage = stage;
                    break;
                }
            }

            if (targetStage == null) {
                sendErrorResponse(rsp, ApiConstants.HTTP_NOT_FOUND,
                    "Stage not found",
                    "Stage with ID " + stageId + " not found");
                return;
            }

            // Return JSON response with stage log
            sendStageLogResponse(rsp, targetStage);

        } catch (Exception e) {
            // Log error and return 500
            LOGGER.log(Level.SEVERE, "Unexpected error in doStagelog: " + e.getMessage(), e);

            sendErrorResponse(rsp, ApiConstants.HTTP_INTERNAL_SERVER_ERROR,
                MessageConstants.ERROR_INTERNAL_SERVER,
                String.format(MessageConstants.ERROR_UNEXPECTED, e.getMessage()));
        }
    }

    // ========== HELPER METHODS FOR STAGE APIs ==========

    /**
     * Extract job name from Jenkins URL.
     *
     * Supports formats:
     * - https://jenkins.example.com/job/jobName/ -> jobName
     * - https://jenkins.example.com/job/folder/job/jobName/ -> folder/jobName
     * - https://jenkins.example.com/job/folder1/job/folder2/job/jobName/ -> folder1/folder2/jobName
     *
     * @param jobUrl Jenkins job URL
     * @return Job name or null if URL is invalid
     */
    private String extractJobNameFromUrl(String jobUrl) {
        if (jobUrl == null || jobUrl.trim().isEmpty()) {
            return null;
        }

        try {
            // Normalize URL - remove trailing slash
            String url = jobUrl.trim();
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            // Parse job name from URL pattern: .../job/name/job/name/...
            String[] parts = url.split("/job/");
            if (parts.length < 2) {
                return null;
            }

            // Join all parts after /job/ with /
            StringBuilder jobName = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    jobName.append("/");
                }
                jobName.append(parts[i]);
            }

            return jobName.toString();

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing job URL: " + jobUrl + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Sends a successful JSON response with stages information.
     *
     * @param rsp HTTP response
     * @param stagesInfo the stages information to send
     * @throws IOException if I/O error occurs
     */
    private void sendStagesSuccessResponse(StaplerResponse rsp, StagesInfo stagesInfo) throws IOException {
        rsp.setStatus(ApiConstants.HTTP_OK);

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"data\":").append(stagesInfo.toJson());
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response for submit action.
     *
     * @param rsp HTTP response
     * @param inputId Input ID that was submitted
     * @throws IOException if I/O error occurs
     */
    private void sendSubmitSuccessResponse(StaplerResponse rsp, String inputId) throws IOException {
        rsp.setStatus(ApiConstants.HTTP_OK);

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"message\":\"Input submitted successfully\",");
        json.append("\"inputId\":").append(jsonString(inputId));
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response for abort action.
     *
     * @param rsp HTTP response
     * @param inputId Input ID that was aborted
     * @throws IOException if I/O error occurs
     */
    private void sendAbortSuccessResponse(StaplerResponse rsp, String inputId) throws IOException {
        rsp.setStatus(ApiConstants.HTTP_OK);

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"message\":\"Input aborted successfully\",");
        json.append("\"inputId\":").append(jsonString(inputId));
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response with stage log information.
     *
     * @param rsp HTTP response
     * @param stageInfo Stage information with logs
     * @throws IOException if I/O error occurs
     */
    private void sendStageLogResponse(StaplerResponse rsp, StageInfo stageInfo) throws IOException {
        rsp.setStatus(ApiConstants.HTTP_OK);

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"data\":").append(stageInfo.toJson());
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Parse JSON body from request.
     *
     * @param req HTTP request
     * @return Map of parsed JSON data
     * @throws IOException if I/O error occurs
     */
    private Map<String, Object> parseJsonBody(StaplerRequest req) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try {
            BufferedReader reader = req.getReader();
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            String jsonBody = body.toString().trim();
            LOGGER.log(Level.INFO, "Raw JSON body: " + jsonBody);

            if (jsonBody.isEmpty() || !jsonBody.startsWith("{")) {
                return result;
            }

            // Remove outer braces
            jsonBody = jsonBody.substring(1, jsonBody.length() - 1);

            // Split by comma, but not inside nested objects or arrays
            String[] pairs = jsonBody.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)(?![^{]*})");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
                    String value = keyValue[1].trim();

                    // Check if value is a nested object
                    if (value.startsWith("{") && value.endsWith("}")) {
                        // Parse nested object
                        Map<String, Object> nestedMap = new HashMap<>();
                        String nestedBody = value.substring(1, value.length() - 1);
                        String[] nestedPairs = nestedBody.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                        for (String nestedPair : nestedPairs) {
                            String[] nestedKeyValue = nestedPair.split(":", 2);
                            if (nestedKeyValue.length == 2) {
                                String nestedKey = nestedKeyValue[0].trim().replaceAll("^\"|\"$", "");
                                String nestedValue = nestedKeyValue[1].trim();

                                // Check for boolean values BEFORE removing quotes
                                if ("true".equalsIgnoreCase(nestedValue)) {
                                    nestedMap.put(nestedKey, true);
                                } else if ("false".equalsIgnoreCase(nestedValue)) {
                                    nestedMap.put(nestedKey, false);
                                } else {
                                    // Remove quotes for string values
                                    nestedValue = nestedValue.replaceAll("^\"|\"$", "");
                                    nestedMap.put(nestedKey, nestedValue);
                                }
                            }
                        }
                        result.put(key, nestedMap);
                    } else {
                        // Simple value
                        value = value.replaceAll("^\"|\"$", "");
                        result.put(key, value);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing JSON body: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * Escapes a string for JSON.
     *
     * @param str String to escape
     * @return Escaped JSON string
     */
    private String jsonString(String str) {
        if (str == null) {
            return "null";
        }
        return "\"" + str.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t") + "\"";
    }
}
