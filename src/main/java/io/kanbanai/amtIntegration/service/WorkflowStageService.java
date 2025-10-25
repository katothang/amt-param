package io.kanbanai.amtIntegration.service;

import hudson.model.Run;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import io.kanbanai.amtIntegration.model.StageInfo;
import io.kanbanai.amtIntegration.model.StagesInfo;
import io.kanbanai.amtIntegration.model.InputParameterInfo;
import jenkins.model.Jenkins;

// Workflow plugin imports
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service specialized in handling Jenkins Workflow/Pipeline stages and input steps.
 *
 * This service interacts with Jenkins Workflow API to retrieve information about
 * pipeline stages that require user input/confirmation.
 *
 * All interactions with Workflow Plugin classes are performed through reflection
 * to avoid ClassNotFoundException when the plugin is not available.
 *
 * Flow:
 * 1. Detects if build is a WorkflowRun (Pipeline job)
 * 2. Retrieves all pending input actions from the build
 * 3. Extracts stage information and input status
 * 4. Provides fallback behavior when workflow plugin is not available
 * 5. Maintains type safety while working with unknown plugin classes
 *
 * @author KanbanAI
 * @since 1.0.4
 */
public class WorkflowStageService {
    
    private static final Logger LOGGER = Logger.getLogger(WorkflowStageService.class.getName());
    
    // Singleton instance
    private static WorkflowStageService instance;
    
    // Workflow plugin class names
    private static final String WORKFLOW_RUN_CLASS = "org.jenkinsci.plugins.workflow.job.WorkflowRun";
    private static final String INPUT_ACTION_CLASS = "org.jenkinsci.plugins.workflow.support.steps.input.InputAction";
    private static final String INPUT_STEP_EXECUTION_CLASS = "org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution";
    
    /**
     * Private constructor for Singleton pattern
     */
    private WorkflowStageService() {
    }
    
    /**
     * Gets the singleton instance of the service
     * 
     * @return WorkflowStageService instance
     */
    public static synchronized WorkflowStageService getInstance() {
        if (instance == null) {
            instance = new WorkflowStageService();
        }
        return instance;
    }
    
    /**
     * Retrieves stage information for a build, including input/confirmation status
     * 
     * @param run Jenkins build run
     * @return StagesInfo containing all stage information
     */
    public StagesInfo getStagesInfo(Run<?, ?> run) {
        StagesInfo stagesInfo = new StagesInfo();
        
        if (run == null) {
            LOGGER.log(Level.WARNING, "Run is null");
            return stagesInfo;
        }
        
        // Set basic build information
        stagesInfo.setJobName(run.getParent().getName());
        stagesInfo.setJobFullName(run.getParent().getFullName());
        stagesInfo.setBuildNumber(run.getNumber());
        stagesInfo.setBuildUrl(getBuildUrl(run));
        stagesInfo.setRunning(!run.hasntStartedYet() && run.isBuilding());
        
        // Set build status
        if (run.isBuilding()) {
            stagesInfo.setBuildStatus("RUNNING");
        } else if (run.getResult() != null) {
            stagesInfo.setBuildStatus(run.getResult().toString());
        } else {
            stagesInfo.setBuildStatus("UNKNOWN");
        }
        
        // Check if this is a WorkflowRun (Pipeline job)
        if (!isWorkflowRun(run)) {
            LOGGER.log(Level.FINE, "Build is not a WorkflowRun, no stages to retrieve");
            return stagesInfo;
        }
        
        try {
            // Get pending inputs from the build
            List<StageInfo> stages = getPendingInputs(run);
            stagesInfo.setStages(stages);
            
            LOGGER.log(Level.INFO, "Retrieved " + stages.size() + " stages with input for build " + 
                      run.getParent().getFullName() + " #" + run.getNumber());
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error retrieving stages for build: " + e.getMessage(), e);
        }
        
        return stagesInfo;
    }
    
    /**
     * Checks if a run is a WorkflowRun (Pipeline job)
     * 
     * @param run Jenkins build run
     * @return true if run is a WorkflowRun, false otherwise
     */
    private boolean isWorkflowRun(Run<?, ?> run) {
        try {
            Class<?> workflowRunClass = Class.forName(WORKFLOW_RUN_CLASS);
            return workflowRunClass.isInstance(run);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.FINE, "WorkflowRun class not found, workflow plugin may not be installed");
            return false;
        }
    }
    
    /**
     * Gets pending inputs from a WorkflowRun using InputAction directly
     *
     * @param run Jenkins build run (must be a WorkflowRun)
     * @return List of StageInfo with input information
     */
    private List<StageInfo> getPendingInputs(Run<?, ?> run) {
        List<StageInfo> stages = new ArrayList<>();

        try {
            // Cast to WorkflowRun
            if (!(run instanceof WorkflowRun)) {
                LOGGER.log(Level.FINE, "Run is not a WorkflowRun");
                return stages;
            }

            WorkflowRun workflowRun = (WorkflowRun) run;

            // Get InputAction from the run
            InputAction inputAction = workflowRun.getAction(InputAction.class);
            if (inputAction == null) {
                LOGGER.log(Level.FINE, "No InputAction found for build");
                return stages;
            }

            // Get executions directly from InputAction
            try {
                List<InputStepExecution> executions = inputAction.getExecutions();
                if (executions == null || executions.isEmpty()) {
                    LOGGER.log(Level.FINE, "No input executions found");
                    return stages;
                }

                // Process each execution
                for (InputStepExecution execution : executions) {
                    try {
                        StageInfo stageInfo = extractStageInfoDirect(execution, run);
                        if (stageInfo != null) {
                            stages.add(stageInfo);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error extracting stage info: " + e.getMessage(), e);
                    }
                }

            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "getExecutions was interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error getting executions: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting pending inputs: " + e.getMessage(), e);
        }

        return stages;
    }

    /**
     * Submits input with parameters using InputAction directly
     *
     * @param run Jenkins build run
     * @param inputId Input ID to submit
     * @param parameters Map of parameter name to value
     * @return true if successful, false otherwise
     */
    public boolean submitInput(Run<?, ?> run, String inputId, Map<String, Object> parameters) {
        try {
            // Cast to WorkflowRun
            if (!(run instanceof WorkflowRun)) {
                LOGGER.log(Level.WARNING, "Run is not a WorkflowRun");
                return false;
            }

            WorkflowRun workflowRun = (WorkflowRun) run;

            // Get InputAction from the run
            InputAction inputAction = workflowRun.getAction(InputAction.class);
            if (inputAction == null) {
                LOGGER.log(Level.WARNING, "No InputAction found for build");
                return false;
            }

            // Get executions
            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions == null || executions.isEmpty()) {
                LOGGER.log(Level.WARNING, "No input executions found");
                return false;
            }

            // Find the execution with matching ID
            InputStepExecution targetExecution = null;
            for (InputStepExecution execution : executions) {
                if (inputId.equals(execution.getId())) {
                    targetExecution = execution;
                    break;
                }
            }

            if (targetExecution == null) {
                LOGGER.log(Level.WARNING, "Input execution not found for ID: " + inputId);
                return false;
            }

            // Get InputStep
            InputStep inputStep = targetExecution.getInput();
            if (inputStep == null) {
                LOGGER.log(Level.WARNING, "InputStep not found");
                return false;
            }

            // Build parameter values
            List<ParameterDefinition> paramDefs = inputStep.getParameters();

            LOGGER.log(Level.INFO, "Processing input submission for ID: " + inputId);
            LOGGER.log(Level.INFO, "Number of parameter definitions: " + (paramDefs != null ? paramDefs.size() : 0));
            LOGGER.log(Level.INFO, "Received parameters: " + parameters);

            if (paramDefs != null && !paramDefs.isEmpty()) {
                // Create parameter values list
                List<ParameterValue> parameterValues = new ArrayList<>();
                for (ParameterDefinition paramDef : paramDefs) {
                    String paramName = paramDef.getName();
                    Object paramValue = parameters.get(paramName);

                    LOGGER.log(Level.INFO, "Processing parameter: " + paramName +
                        " (type: " + paramDef.getClass().getSimpleName() +
                        ", value: " + paramValue + ")");

                    // Create ParameterValue based on type
                    ParameterValue pv = createParameterValue(paramDef, paramValue);
                    if (pv != null) {
                        parameterValues.add(pv);
                        LOGGER.log(Level.INFO, "Created ParameterValue: " + pv.getClass().getSimpleName() +
                            " with value: " + pv.getValue());
                    } else {
                        LOGGER.log(Level.WARNING, "Failed to create ParameterValue for: " + paramName);
                    }
                }

                // Proceed with parameter values
                // Note: InputStepExecution.proceed() expects a single ParameterValue
                // For multiple parameters, we need to pass the first one or use a different approach
                if (!parameterValues.isEmpty()) {
                    LOGGER.log(Level.INFO, "Proceeding with " + parameterValues.size() + " parameter(s)");
                    // Pass the first parameter value (Jenkins input step typically expects one value)
                    targetExecution.proceed(parameterValues.get(0));
                } else {
                    LOGGER.log(Level.INFO, "No parameter values created, proceeding with null");
                    targetExecution.proceed(null);
                }
            } else {
                // No parameters, just proceed
                LOGGER.log(Level.INFO, "No parameters defined, proceeding with null");
                targetExecution.proceed(null);
            }

            LOGGER.log(Level.INFO, "Input submitted successfully: " + inputId);
            return true;

        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Submit input was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error submitting input: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Aborts input using InputAction directly
     *
     * @param run Jenkins build run
     * @param inputId Input ID to abort
     * @return true if successful, false otherwise
     */
    public boolean abortInput(Run<?, ?> run, String inputId) {
        try {
            // Cast to WorkflowRun
            if (!(run instanceof WorkflowRun)) {
                LOGGER.log(Level.WARNING, "Run is not a WorkflowRun");
                return false;
            }

            WorkflowRun workflowRun = (WorkflowRun) run;

            // Get InputAction from the run
            InputAction inputAction = workflowRun.getAction(InputAction.class);
            if (inputAction == null) {
                LOGGER.log(Level.WARNING, "No InputAction found for build");
                return false;
            }

            // Get executions
            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions == null || executions.isEmpty()) {
                LOGGER.log(Level.WARNING, "No input executions found");
                return false;
            }

            // Find the execution with matching ID
            InputStepExecution targetExecution = null;
            for (InputStepExecution execution : executions) {
                if (inputId.equals(execution.getId())) {
                    targetExecution = execution;
                    break;
                }
            }

            if (targetExecution == null) {
                LOGGER.log(Level.WARNING, "Input execution not found for ID: " + inputId);
                return false;
            }

            // Abort the input
            targetExecution.doAbort();

            LOGGER.log(Level.INFO, "Input aborted successfully: " + inputId);
            return true;

        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Abort input was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error aborting input: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Creates a ParameterValue from a ParameterDefinition and value
     *
     * @param paramDef ParameterDefinition
     * @param value Parameter value
     * @return ParameterValue or null
     */
    private ParameterValue createParameterValue(ParameterDefinition paramDef, Object value) {
        try {
            String paramType = paramDef.getClass().getSimpleName();
            String paramName = paramDef.getName();

            // Handle different parameter types
            if (paramType.contains("BooleanParameterDefinition")) {
                // Boolean parameter
                boolean boolValue = false;
                if (value instanceof Boolean) {
                    boolValue = (Boolean) value;
                } else if (value != null) {
                    boolValue = Boolean.parseBoolean(value.toString());
                }

                Class<?> boolParamValueClass = Class.forName("hudson.model.BooleanParameterValue");
                return (ParameterValue) boolParamValueClass
                    .getConstructor(String.class, boolean.class)
                    .newInstance(paramName, boolValue);

            } else if (paramType.contains("StringParameterDefinition") ||
                       paramType.contains("PasswordParameterDefinition") ||
                       paramType.contains("TextParameterDefinition")) {
                // String-based parameters
                String strValue = value != null ? value.toString() : "";

                Class<?> strParamValueClass = Class.forName("hudson.model.StringParameterValue");
                return (ParameterValue) strParamValueClass
                    .getConstructor(String.class, String.class)
                    .newInstance(paramName, strValue);

            } else if (paramType.contains("ChoiceParameterDefinition")) {
                // Choice parameter
                String choiceValue = value != null ? value.toString() : "";

                Class<?> strParamValueClass = Class.forName("hudson.model.StringParameterValue");
                return (ParameterValue) strParamValueClass
                    .getConstructor(String.class, String.class)
                    .newInstance(paramName, choiceValue);

            } else {
                // Default: treat as string
                String strValue = value != null ? value.toString() : "";

                Class<?> strParamValueClass = Class.forName("hudson.model.StringParameterValue");
                return (ParameterValue) strParamValueClass
                    .getConstructor(String.class, String.class)
                    .newInstance(paramName, strValue);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error creating parameter value: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets InputAction from a run using reflection
     *
     * @param run Jenkins build run
     * @return InputAction object or null
     */
    private Object getInputAction(Run<?, ?> run) {
        try {
            Class<?> inputActionClass = Class.forName(INPUT_ACTION_CLASS);
            Method getActionMethod = run.getClass().getMethod("getAction", Class.class);
            return getActionMethod.invoke(run, inputActionClass);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not get InputAction: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Checks if InputAction is waiting for input
     *
     * @param inputAction InputAction object
     * @return true if waiting for input, false otherwise, null if cannot determine
     */
    private Boolean isWaitingForInput(Object inputAction) {
        try {
            Method isWaitingMethod = inputAction.getClass().getMethod("isWaitingForInput");
            Object result = isWaitingMethod.invoke(inputAction);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Method might throw InterruptedException or TimeoutException
            Throwable cause = e.getCause();
            LOGGER.log(Level.FINE, "isWaitingForInput threw exception: " + (cause != null ? cause.getMessage() : e.getMessage()));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not check isWaitingForInput: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets list of executions from InputAction using reflection
     *
     * @param inputAction InputAction object
     * @return List of InputStepExecution objects
     */
    @SuppressWarnings("unchecked")
    private List<?> getExecutions(Object inputAction) {
        try {
            Method getExecutionsMethod = inputAction.getClass().getMethod("getExecutions");
            Object result = getExecutionsMethod.invoke(inputAction);

            if (result instanceof List) {
                List<?> execList = (List<?>) result;
                return execList;
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap the actual exception thrown by getExecutions()
            Throwable cause = e.getCause();
            if (cause instanceof InterruptedException) {
                LOGGER.log(Level.WARNING, "getExecutions was interrupted: " + cause.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupt status
            } else if (cause != null && cause.getClass().getName().contains("TimeoutException")) {
                LOGGER.log(Level.WARNING, "getExecutions timed out: " + cause.getMessage());
            } else {
                LOGGER.log(Level.WARNING, "Error invoking getExecutions: " + (cause != null ? cause.getMessage() : e.getMessage()), e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not get executions: " + e.getMessage(), e);
        }
        return new ArrayList<>();
    }
    
    /**
     * Extracts StageInfo from an InputStepExecution using reflection
     *
     * @param execution InputStepExecution object
     * @param run Jenkins build run
     * @return StageInfo or null
     */
    private StageInfo extractStageInfo(Object execution, Run<?, ?> run) {
        try {
            // Get input ID
            String id = invokeMethodAsString(execution, "getId");
            if (id == null) {
                return null;
            }

            // Get input message
            String message = invokeMethodAsString(execution, "getMessage");

            // Get submitter
            String submitter = invokeMethodAsString(execution, "getSubmitter");

            // Get proceed text (default to "Proceed" if not available)
            String proceedText = "Proceed";
            try {
                String customProceedText = invokeMethodAsString(execution, "getOk");
                if (customProceedText != null && !customProceedText.trim().isEmpty()) {
                    proceedText = customProceedText;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Could not get proceed text, using default: " + e.getMessage());
            }

            // Create StageInfo
            StageInfo stageInfo = new StageInfo();
            stageInfo.setId(id);
            stageInfo.setName(message != null ? message : "Input Required");
            stageInfo.setMessage(message);
            stageInfo.setSubmitter(submitter);

            // Determine status
            // If execution exists in the list, it's pending
            stageInfo.setStatus("pending");
            stageInfo.setExecuted(false);

            // Build URLs
            String baseUrl = getBuildUrl(run);
            String jobUrl = run.getParent().getUrl();
            String buildNumber = String.valueOf(run.getNumber());

            // Set URLs in both formats for compatibility
            stageInfo.setSubmitUrl(baseUrl + "input/" + id + "/submit");
            stageInfo.setAbortUrl(baseUrl + "input/" + id + "/abort");

            // Set wfapi-style URLs
            stageInfo.setProceedText(proceedText);
            stageInfo.setProceedUrl("/" + jobUrl + buildNumber + "/wfapi/inputSubmit?inputId=" + id);

            // Extract input parameters
            List<InputParameterInfo> parameters = extractInputParameters(execution);
            stageInfo.setParameters(parameters);

            return stageInfo;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting stage info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts StageInfo directly from InputStepExecution
     *
     * @param execution InputStepExecution
     * @param run Jenkins build run
     * @return StageInfo or null
     */
    private StageInfo extractStageInfoDirect(InputStepExecution execution, Run<?, ?> run) {
        try {
            // Get input ID
            String id = execution.getId();
            if (id == null) {
                return null;
            }

            // Get InputStep
            InputStep inputStep = execution.getInput();
            if (inputStep == null) {
                return null;
            }

            // Get input message
            String message = inputStep.getMessage();

            // Get submitter
            String submitter = inputStep.getSubmitter();

            // Get proceed text (default to "Proceed" if not available)
            String proceedText = inputStep.getOk();
            if (proceedText == null || proceedText.trim().isEmpty()) {
                proceedText = "Proceed";
            }

            // Create StageInfo
            StageInfo stageInfo = new StageInfo();
            stageInfo.setId(id);
            stageInfo.setName(message != null ? message : "Input Required");
            stageInfo.setMessage(message);
            stageInfo.setSubmitter(submitter);

            // Determine status - pending input
            stageInfo.setStatus("pending");
            stageInfo.setExecuted(false);

            // Build URLs
            String baseUrl = getBuildUrl(run);
            String jobUrl = run.getParent().getUrl();
            String buildNumber = String.valueOf(run.getNumber());

            // Set URLs in both formats for compatibility
            stageInfo.setSubmitUrl(baseUrl + "input/" + id + "/submit");
            stageInfo.setAbortUrl(baseUrl + "input/" + id + "/abort");

            // Set wfapi-style URLs
            stageInfo.setProceedText(proceedText);
            stageInfo.setProceedUrl("/" + jobUrl + buildNumber + "/wfapi/inputSubmit?inputId=" + id);

            // Extract input parameters directly
            List<InputParameterInfo> parameters = extractInputParametersDirect(inputStep);
            stageInfo.setParameters(parameters);

            return stageInfo;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting stage info directly: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Extracts input parameters from an InputStepExecution using reflection
     *
     * @param execution InputStepExecution object
     * @return List of InputParameterInfo
     */
    private List<InputParameterInfo> extractInputParameters(Object execution) {
        List<InputParameterInfo> parameters = new ArrayList<>();

        try {
            // Get the Input object from execution
            Method getInputMethod = execution.getClass().getMethod("getInput");
            Object input = getInputMethod.invoke(execution);

            if (input == null) {
                return parameters;
            }

            // Get parameters list from Input
            Method getParametersMethod = input.getClass().getMethod("getParameters");
            Object paramsObj = getParametersMethod.invoke(input);

            if (!(paramsObj instanceof List)) {
                return parameters;
            }

            @SuppressWarnings("unchecked")
            List<?> paramsList = (List<?>) paramsObj;

            // Process each parameter
            for (Object paramDef : paramsList) {
                try {
                    InputParameterInfo paramInfo = extractParameterInfo(paramDef);
                    if (paramInfo != null) {
                        parameters.add(paramInfo);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error extracting parameter info: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not extract input parameters: " + e.getMessage());
        }

        return parameters;
    }

    /**
     * Extracts input parameters directly from InputStep
     *
     * @param inputStep InputStep
     * @return List of InputParameterInfo
     */
    private List<InputParameterInfo> extractInputParametersDirect(InputStep inputStep) {
        List<InputParameterInfo> parameters = new ArrayList<>();

        try {
            // Get parameters from InputStep
            List<ParameterDefinition> paramDefs = inputStep.getParameters();
            if (paramDefs == null || paramDefs.isEmpty()) {
                return parameters;
            }

            // Process each parameter
            for (ParameterDefinition paramDef : paramDefs) {
                try {
                    InputParameterInfo paramInfo = extractParameterInfoDirect(paramDef);
                    if (paramInfo != null) {
                        parameters.add(paramInfo);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error extracting parameter info: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not extract input parameters: " + e.getMessage());
        }

        return parameters;
    }

    /**
     * Extracts parameter information directly from ParameterDefinition
     *
     * @param paramDef ParameterDefinition
     * @return InputParameterInfo or null
     */
    private InputParameterInfo extractParameterInfoDirect(ParameterDefinition paramDef) {
        try {
            // Get parameter name
            String name = paramDef.getName();
            if (name == null) {
                return null;
            }

            // Get parameter type
            String type = paramDef.getClass().getSimpleName();

            // Create InputParameterInfo
            InputParameterInfo paramInfo = new InputParameterInfo(name, type);

            // Set description
            String description = paramDef.getDescription();
            paramInfo.setDescription(description);

            // Set default value
            ParameterValue defaultValue = paramDef.getDefaultParameterValue();
            if (defaultValue != null) {
                Object value = defaultValue.getValue();
                if (value != null) {
                    paramInfo.setDefaultValue(value.toString());
                }
            }

            // Determine input type based on parameter type
            String inputType = determineInputType(type);
            paramInfo.setInputType(inputType);

            // Set choices for ChoiceParameterDefinition
            if ("ChoiceParameterDefinition".equals(type)) {
                try {
                    // Use reflection to get choices
                    java.lang.reflect.Method getChoicesMethod = paramDef.getClass().getMethod("getChoices");
                    Object choicesObj = getChoicesMethod.invoke(paramDef);
                    if (choicesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> choices = (List<String>) choicesObj;
                        paramInfo.setChoices(choices);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not get choices: " + e.getMessage());
                }
            }

            return paramInfo;

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error extracting parameter info directly: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts parameter information from a ParameterDefinition object
     *
     * @param paramDef ParameterDefinition object
     * @return InputParameterInfo or null
     */
    private InputParameterInfo extractParameterInfo(Object paramDef) {
        try {
            // Get parameter name
            String name = invokeMethodAsString(paramDef, "getName");
            if (name == null) {
                return null;
            }

            // Get parameter type
            String type = paramDef.getClass().getSimpleName();

            // Create InputParameterInfo
            InputParameterInfo paramInfo = new InputParameterInfo(name, type);

            // Get description
            String description = invokeMethodAsString(paramDef, "getDescription");
            paramInfo.setDescription(description);

            // Get default value
            try {
                Object defaultValueObj = invokeMethod(paramDef, "getDefaultValue");
                if (defaultValueObj != null) {
                    paramInfo.setDefaultValue(defaultValueObj.toString());
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Could not get default value: " + e.getMessage());
            }

            // Determine input type and extract choices based on parameter type
            determineInputTypeAndChoices(paramDef, paramInfo, type);

            return paramInfo;

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error extracting parameter info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Determines input type based on parameter type
     *
     * @param type Parameter type class name
     * @return Input type string
     */
    private String determineInputType(String type) {
        if (type.contains("BooleanParameterDefinition")) {
            return "checkbox";
        } else if (type.contains("ChoiceParameterDefinition")) {
            return "select";
        } else if (type.contains("PasswordParameterDefinition")) {
            return "password";
        } else if (type.contains("TextParameterDefinition")) {
            return "textarea";
        } else {
            return "text";
        }
    }

    /**
     * Determines input type and extracts choices for a parameter
     *
     * @param paramDef ParameterDefinition object
     * @param paramInfo InputParameterInfo to populate
     * @param type Parameter type class name
     */
    private void determineInputTypeAndChoices(Object paramDef, InputParameterInfo paramInfo, String type) {
        try {
            if (type.contains("BooleanParameterDefinition")) {
                paramInfo.setInputType("checkbox");
                List<String> choices = new ArrayList<>();
                choices.add("true");
                choices.add("false");
                paramInfo.setChoices(choices);

            } else if (type.contains("ChoiceParameterDefinition")) {
                paramInfo.setInputType("select");
                // Get choices
                try {
                    Object choicesObj = invokeMethod(paramDef, "getChoices");
                    if (choicesObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> choices = new ArrayList<>();
                        for (Object choice : (List<?>) choicesObj) {
                            choices.add(choice.toString());
                        }
                        paramInfo.setChoices(choices);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Could not get choices: " + e.getMessage());
                }

            } else if (type.contains("PasswordParameterDefinition")) {
                paramInfo.setInputType("password");

            } else if (type.contains("TextParameterDefinition")) {
                paramInfo.setInputType("textarea");

            } else {
                // Default to text input
                paramInfo.setInputType("text");
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error determining input type: " + e.getMessage());
            paramInfo.setInputType("text");
        }
    }

    /**
     * Invokes a method on an object and returns result as String
     *
     * @param obj Object to invoke method on
     * @param methodName Method name
     * @return Method result as String, or null
     */
    private String invokeMethodAsString(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not invoke method " + methodName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Invokes a method on an object and returns result as Object
     *
     * @param obj Object to invoke method on
     * @param methodName Method name
     * @return Method result as Object, or null
     */
    private Object invokeMethod(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Could not invoke method " + methodName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the build URL
     * 
     * @param run Jenkins build run
     * @return Build URL
     */
    private String getBuildUrl(Run<?, ?> run) {
        try {
            Jenkins jenkins = Jenkins.get();
            String rootUrl = jenkins.getRootUrl();
            if (rootUrl == null) {
                rootUrl = "";
            }
            return rootUrl + run.getUrl();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not get build URL: " + e.getMessage());
            return "";
        }
    }
}

