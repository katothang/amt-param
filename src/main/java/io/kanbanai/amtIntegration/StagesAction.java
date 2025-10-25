package io.kanbanai.amtIntegration;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Run;
import io.kanbanai.amtIntegration.model.StageInfo;
import jenkins.model.TransientActionFactory;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kanbanai.amtIntegration.service.WorkflowStageService;
import io.kanbanai.amtIntegration.model.StagesInfo;

/**
 * Jenkins Run Action to add stage confirmation endpoint to each build URL.
 *
 * This action will be injected into all Run objects in Jenkins,
 * allowing stage confirmation status access through URL pattern:
 * {JENKINS_URL}/job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/stages
 *
 * Operation:
 * - Plugin creates /amt-integration/stages endpoint for each build
 * - Endpoint returns JSON with stage confirmation information
 * - Shows which stages are pending input and their status
 *
 * Architecture:
 * - Uses TransientActionFactory to inject action into each run
 * - doStages() handles requests at /amt-integration/stages
 * - Returns JSON response with stage information
 *
 * Flow:
 * 1. TransientActionFactory injects this action into all Run objects
 * 2. Action provides /amt-integration/stages endpoint for each build
 * 3. API endpoint retrieves stage information from WorkflowStageService
 * 4. Returns JSON response with all stage details
 * 5. Handles errors gracefully with appropriate HTTP status codes
 *
 * @author KanbanAI
 * @since 1.0.4
 */
public class StagesAction implements Action {
    
    private static final Logger LOGGER = Logger.getLogger(StagesAction.class.getName());
    
    private final Run<?, ?> run;
    private final WorkflowStageService stageService;
    
    /**
     * Constructor
     * 
     * @param run Jenkins run instance that this action is attached to
     */
    public StagesAction(Run<?, ?> run) {
        this.run = run;
        this.stageService = WorkflowStageService.getInstance();
    }
    
    /**
     * {@inheritDoc}
     * 
     * Returns null to not display action in build sidebar.
     */
    @Override
    public String getIconFileName() {
        return null;
    }
    
    /**
     * {@inheritDoc}
     * 
     * Display name of the action (not displayed because iconFileName = null)
     */
    @Override
    public String getDisplayName() {
        return "AMT Stages";
    }
    
    /**
     * {@inheritDoc}
     * 
     * URL name defines the endpoint path.
     * Endpoint will be accessible at: {BUILD_URL}/amt-integration/
     */
    @Override
    public String getUrlName() {
        return "amt-integration";
    }
    
    /**
     * Handles GET request at /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/stages
     *
     * URL pattern: /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/stages
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException if I/O error occurs
     */
    @GET
    public void doStages(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.log(Level.INFO, "Processing stages request for build " +
                  run.getParent().getFullName() + " #" + run.getNumber());

        try {
            // Check permissions
            if (!checkBuildReadPermission()) {
                sendErrorResponse(rsp, 403, "Access denied to build",
                    "You don't have READ permission for this build");
                return;
            }

            // Get stages information from service
            StagesInfo stagesInfo = stageService.getStagesInfo(run);

            // Return JSON response
            sendSuccessResponse(rsp, stagesInfo);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doStages: " + e.getMessage(), e);
            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }

    /**
     * Handles POST request to submit input at /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/submit
     *
     * URL pattern: POST /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/submit
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
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException if I/O error occurs
     */
    @POST
    public void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.log(Level.INFO, "Processing submit request for build " +
                  run.getParent().getFullName() + " #" + run.getNumber());

        try {
            // Check permissions
            if (!checkBuildReadPermission()) {
                sendErrorResponse(rsp, 403, "Access denied to build",
                    "You don't have READ permission for this build");
                return;
            }

            // Parse JSON body
            Map<String, Object> requestData = parseJsonBody(req);
            LOGGER.log(Level.INFO, "Parsed request data: " + requestData);

            // Get inputId
            String inputId = (String) requestData.get("inputId");
            if (inputId == null || inputId.trim().isEmpty()) {
                sendErrorResponse(rsp, 400, "Missing required field: inputId",
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
            LOGGER.log(Level.INFO, "Parameters class: " + (parameters.getClass().getName()));
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                LOGGER.log(Level.INFO, "  - " + entry.getKey() + " = " + entry.getValue() +
                    " (type: " + (entry.getValue() != null ? entry.getValue().getClass().getName() : "null") + ")");
            }

            // Submit input using service
            boolean success = stageService.submitInput(run, inputId, parameters);

            if (success) {
                sendSubmitSuccessResponse(rsp, inputId);
            } else {
                sendErrorResponse(rsp, 404, "Input not found",
                    "Input with ID " + inputId + " not found or already processed");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doSubmit: " + e.getMessage(), e);
            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }

    /**
     * Handles POST request to abort input at /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/abort
     *
     * URL pattern: POST /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/abort
     *
     * Request body (JSON):
     * {
     *   "inputId": "59ceebb6391a40b46b62905f024cf0d2"
     * }
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException if I/O error occurs
     */
    @POST
    public void doAbort(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.log(Level.INFO, "Processing abort request for build " +
                  run.getParent().getFullName() + " #" + run.getNumber());

        try {
            // Check permissions
            if (!checkBuildReadPermission()) {
                sendErrorResponse(rsp, 403, "Access denied to build",
                    "You don't have READ permission for this build");
                return;
            }

            // Parse JSON body
            Map<String, Object> requestData = parseJsonBody(req);

            // Get inputId
            String inputId = (String) requestData.get("inputId");
            if (inputId == null || inputId.trim().isEmpty()) {
                sendErrorResponse(rsp, 400, "Missing required field: inputId",
                    "Please provide the inputId in the request body");
                return;
            }

            // Abort input using service
            boolean success = stageService.abortInput(run, inputId);

            if (success) {
                sendAbortSuccessResponse(rsp, inputId);
            } else {
                sendErrorResponse(rsp, 404, "Input not found",
                    "Input with ID " + inputId + " not found or already processed");
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doAbort: " + e.getMessage(), e);
            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }

    /**
     * Handles GET request at /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/allstages
     *
     * Returns all stages including those not yet executed, with their logs
     *
     * URL pattern: /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/allstages
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException if I/O error occurs
     */
    @GET
    public void doAllstages(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.log(Level.INFO, "Processing allstages request for build " +
                  run.getParent().getFullName() + " #" + run.getNumber());

        try {
            // Check permissions
            if (!checkBuildReadPermission()) {
                sendErrorResponse(rsp, 403, "Access denied to build",
                    "You don't have READ permission for this build");
                return;
            }

            // Get all stages information with logs from service
            StagesInfo stagesInfo = stageService.getAllStagesWithLogs(run);

            // Return JSON response
            sendSuccessResponse(rsp, stagesInfo);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doAllstages: " + e.getMessage(), e);
            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }

    /**
     * Handles GET request at /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/stagelog
     *
     * Returns log for a specific stage
     *
     * URL pattern: /job/{JOB_NAME}/{BUILD_NUMBER}/amt-integration/stagelog?stageId={stageId}
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException if I/O error occurs
     */
    @GET
    public void doStagelog(StaplerRequest req, StaplerResponse rsp) throws IOException {
        LOGGER.log(Level.INFO, "Processing stagelog request for build " +
                  run.getParent().getFullName() + " #" + run.getNumber());

        try {
            // Check permissions
            if (!checkBuildReadPermission()) {
                sendErrorResponse(rsp, 403, "Access denied to build",
                    "You don't have READ permission for this build");
                return;
            }

            // Get stageId parameter
            String stageId = req.getParameter("stageId");
            if (stageId == null || stageId.trim().isEmpty()) {
                sendErrorResponse(rsp, 400, "Missing required parameter: stageId",
                    "Please provide the stageId query parameter");
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
                sendErrorResponse(rsp, 404, "Stage not found",
                    "Stage with ID " + stageId + " not found");
                return;
            }

            // Return JSON response with stage log
            sendStageLogResponse(rsp, targetStage);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in doStagelog: " + e.getMessage(), e);
            sendErrorResponse(rsp, 500, "Internal server error",
                "An unexpected error occurred while processing the request: " + e.getMessage());
        }
    }
    
    /**
     * Checks if current user has READ permission for the build
     *
     * @return true if user has permission, false otherwise
     */
    private boolean checkBuildReadPermission() {
        try {
            // Check if user has permission to access the parent job
            run.getParent().checkPermission(hudson.model.Item.READ);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Permission check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parses JSON body from request
     *
     * @param req StaplerRequest
     * @return Map of parsed JSON data
     * @throws IOException if parsing fails
     */
    private Map<String, Object> parseJsonBody(StaplerRequest req) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            java.io.BufferedReader reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            String body = sb.toString().trim();
            if (body.isEmpty()) {
                return result;
            }

            // Simple JSON parsing (for basic cases)
            // Remove outer braces
            if (body.startsWith("{") && body.endsWith("}")) {
                body = body.substring(1, body.length() - 1);
            }

            // Parse key-value pairs
            String[] pairs = body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
                    String value = keyValue[1].trim();

                    // Handle nested objects (parameters)
                    if (value.startsWith("{") && value.endsWith("}")) {
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
     * Sends success response with stages information
     *
     * @param rsp StaplerResponse
     * @param stagesInfo Stages information
     * @throws IOException if I/O error occurs
     */
    private void sendSuccessResponse(StaplerResponse rsp, StagesInfo stagesInfo) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"data\":").append(stagesInfo.toJson());
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response for submit action
     *
     * @param rsp StaplerResponse
     * @param inputId Input ID that was submitted
     * @throws IOException if I/O error occurs
     */
    private void sendSubmitSuccessResponse(StaplerResponse rsp, String inputId) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"message\":\"Input submitted successfully\",");
        json.append("\"inputId\":").append(jsonString(inputId));
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response for abort action
     *
     * @param rsp StaplerResponse
     * @param inputId Input ID that was aborted
     * @throws IOException if I/O error occurs
     */
    private void sendAbortSuccessResponse(StaplerResponse rsp, String inputId) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"message\":\"Input aborted successfully\",");
        json.append("\"inputId\":").append(jsonString(inputId));
        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    /**
     * Sends success response with stage log information
     *
     * @param rsp StaplerResponse
     * @param stageInfo Stage information with logs
     * @throws IOException if I/O error occurs
     */
    private void sendStageLogResponse(StaplerResponse rsp, StageInfo stageInfo) throws IOException {
        rsp.setStatus(200);
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":true,");
        json.append("\"data\":").append(stageInfo.toJson());
        json.append("}");

        rsp.getWriter().write(json.toString());
    }
    
    /**
     * Sends error response
     * 
     * @param rsp StaplerResponse
     * @param statusCode HTTP status code
     * @param error Error message
     * @param details Error details
     * @throws IOException if I/O error occurs
     */
    private void sendErrorResponse(StaplerResponse rsp, int statusCode, String error, String details) throws IOException {
        rsp.setStatus(statusCode);
        rsp.setContentType("application/json;charset=UTF-8");
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"success\":false,");
        json.append("\"error\":").append(jsonString(error)).append(",");
        json.append("\"details\":").append(jsonString(details));
        json.append("}");
        
        rsp.getWriter().write(json.toString());
    }
    
    /**
     * Escapes string for JSON
     * 
     * @param str String to escape
     * @return JSON-safe string
     */
    private String jsonString(String str) {
        if (str == null) {
            return "null";
        }
        
        String escaped = str.replace("\\", "\\\\")
                           .replace("\"", "\\\"")
                           .replace("\n", "\\n")
                           .replace("\r", "\\r")
                           .replace("\t", "\\t")
                           .replace("\b", "\\b")
                           .replace("\f", "\\f");
        
        return "\"" + escaped + "\"";
    }
    
    /**
     * Factory to inject action into all Run objects
     */
    @Extension
    public static class Factory extends TransientActionFactory<Run> {
        
        @Override
        public Class<Run> type() {
            return Run.class;
        }
        
        @Nonnull
        @Override
        public Collection<? extends Action> createFor(@Nonnull Run target) {
            // Inject action for all runs
            // The service will check if it's a WorkflowRun internally
            return Collections.singleton(new StagesAction(target));
        }
    }
}

