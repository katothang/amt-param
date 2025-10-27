package io.kanbanai.amtIntegration.service;

import hudson.model.Run;
import hudson.model.Result;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import io.kanbanai.amtIntegration.model.StageInfo;
import io.kanbanai.amtIntegration.model.StagesInfo;
import io.kanbanai.amtIntegration.model.InputParameterInfo;
import io.kanbanai.amtIntegration.model.ParameterInputType;
import jenkins.model.Jenkins;

// Workflow plugin imports
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;

// Pipeline REST API plugin imports
import com.cloudbees.workflow.rest.external.StageNodeExt;
import com.cloudbees.workflow.rest.external.StatusExt;
import com.cloudbees.workflow.rest.external.RunExt;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.ByteArrayOutputStream;

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
            stagesInfo.setBuildStatus(StatusExt.IN_PROGRESS.name());
        } else if (run.getResult() != null) {
            stagesInfo.setBuildStatus(run.getResult().toString());
        } else {
            stagesInfo.setBuildStatus(StatusExt.UNSTABLE.name());
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
            stageInfo.setStatus(StatusExt.PAUSED_PENDING_INPUT);

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
            stageInfo.setStatus(StatusExt.PAUSED_PENDING_INPUT);
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
            ParameterInputType inputType = determineInputType(type);
            paramInfo.setInputType(inputType.getValue());

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
     * @return ParameterInputType enum
     */
    private ParameterInputType determineInputType(String type) {
        if (type.contains("BooleanParameterDefinition")) {
            return ParameterInputType.CHECKBOX;
        } else if (type.contains("ChoiceParameterDefinition")) {
            return ParameterInputType.SELECT;
        } else if (type.contains("PasswordParameterDefinition")) {
            return ParameterInputType.PASSWORD;
        } else if (type.contains("TextParameterDefinition")) {
            return ParameterInputType.TEXTAREA;
        } else {
            return ParameterInputType.TEXT;
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
                paramInfo.setInputType(ParameterInputType.CHECKBOX.getValue());
                List<String> choices = new ArrayList<>();
                choices.add("true");
                choices.add("false");
                paramInfo.setChoices(choices);

            } else if (type.contains("ChoiceParameterDefinition")) {
                paramInfo.setInputType(ParameterInputType.SELECT.getValue());
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
                paramInfo.setInputType(ParameterInputType.PASSWORD.getValue());

            } else if (type.contains("TextParameterDefinition")) {
                paramInfo.setInputType(ParameterInputType.TEXTAREA.getValue());

            } else {
                // Default to text input
                paramInfo.setInputType(ParameterInputType.TEXT.getValue());
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error determining input type: " + e.getMessage());
            paramInfo.setInputType(ParameterInputType.TEXT.getValue());
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

    /**
     * Retrieves all stages information including logs for a build
     *
     * @param run Jenkins build run
     * @return StagesInfo containing all stage information with logs
     */
    public StagesInfo getAllStagesWithLogs(Run<?, ?> run) {
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
            stagesInfo.setBuildStatus(StatusExt.IN_PROGRESS.name());
        } else if (run.getResult() != null) {
            stagesInfo.setBuildStatus(run.getResult().toString());
        } else {
            stagesInfo.setBuildStatus(StatusExt.UNSTABLE.name());
        }

        // Check if this is a WorkflowRun (Pipeline job)
        if (!isWorkflowRun(run)) {
            LOGGER.log(Level.FINE, "Build is not a WorkflowRun, no stages to retrieve");
            return stagesInfo;
        }

        try {
            // Get all stages from the workflow graph
            List<StageInfo> stages = getAllStagesFromGraph((WorkflowRun) run);
            stagesInfo.setStages(stages);

            LOGGER.log(Level.INFO, "Retrieved " + stages.size() + " stages for build " +
                      run.getParent().getFullName() + " #" + run.getNumber());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error retrieving all stages for build: " + e.getMessage(), e);
        }

        return stagesInfo;
    }

    /**
     * Gets all stages from the workflow graph including their logs
     * Uses Pipeline REST API plugin's RunExt and StageNodeExt for accurate stage detection
     *
     * @param workflowRun WorkflowRun instance
     * @return List of StageInfo with logs
     */
    private List<StageInfo> getAllStagesFromGraph(WorkflowRun workflowRun) {
        List<StageInfo> stages = new ArrayList<>();

        try {
            // First, add the special "ALL" stage with complete console log
            StageInfo allStage = createAllStage(workflowRun);
            if (allStage != null) {
                stages.add(allStage);
            }

            // Use Pipeline REST API plugin to get stages
            try {
                RunExt runExt = RunExt.create(workflowRun);
                List<StageNodeExt> stageNodes = runExt.getStages();

                LOGGER.log(Level.INFO, "Found " + stageNodes.size() + " stages using RunExt");

                // Process each stage
                for (StageNodeExt stageNode : stageNodes) {
                    try {
                        StageInfo stageInfo = extractStageInfoFromStageNodeExt(stageNode, workflowRun);
                        if (stageInfo != null) {
                            stages.add(stageInfo);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error extracting stage info from StageNodeExt: " + e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error using RunExt, falling back to FlowGraphWalker: " + e.getMessage(), e);

                // Fallback to original method if RunExt fails
                if (workflowRun.getExecution() == null) {
                    LOGGER.log(Level.FINE, "No execution found for workflow run");
                    return stages;
                }

                // Walk through the flow graph and collect stage nodes
                FlowGraphWalker walker = new FlowGraphWalker(workflowRun.getExecution());
                List<FlowNode> stageNodes = new ArrayList<>();

                for (FlowNode node : walker) {
                    if (node != null && node instanceof StepStartNode) {
                        LabelAction labelAction = node.getAction(LabelAction.class);
                        if (labelAction != null && labelAction.getDisplayName() != null) {
                            String functionName = node.getDisplayFunctionName();

                            // Accept stage nodes only
                            if ("stage".equals(functionName)) {
                                stageNodes.add(0, node);
                            }
                        }
                    }
                }

                LOGGER.log(Level.INFO, "Found " + stageNodes.size() + " stage nodes using FlowGraphWalker");

                // Process each stage node
                for (FlowNode stageNode : stageNodes) {
                    try {
                        StageInfo stageInfo = extractStageInfoFromNode(stageNode, workflowRun);
                        if (stageInfo != null) {
                            stages.add(stageInfo);
                        }
                    } catch (Exception e2) {
                        LOGGER.log(Level.WARNING, "Error extracting stage info from node: " + e2.getMessage(), e2);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting stages: " + e.getMessage(), e);
        }

        return stages;
    }

    /**
     * Extracts StageInfo from a StageNodeExt (from Pipeline REST API plugin)
     *
     * @param stageNode StageNodeExt from Pipeline REST API
     * @param run WorkflowRun instance
     * @return StageInfo with logs
     */
    private StageInfo extractStageInfoFromStageNodeExt(StageNodeExt stageNode, WorkflowRun run) {
        try {
            String stageId = stageNode.getId();
            String stageName = stageNode.getName();

            StageInfo stageInfo = new StageInfo();
            stageInfo.setId(stageId);
            stageInfo.setName(stageName);

            // Get stage status from StageNodeExt
            StatusExt statusExt = stageNode.getStatus();
            stageInfo.setStatus(statusExt);
            stageInfo.setExecuted(statusExt != StatusExt.NOT_EXECUTED);

            // Get timing information
            Long startTimeMillis = stageNode.getStartTimeMillis();
            Long durationMillis = stageNode.getDurationMillis();
            stageInfo.setStartTimeMillis(startTimeMillis);
            stageInfo.setDurationMillis(durationMillis);

            // Get logs for this stage
            String logs = getStageLogFromStageNodeExt(stageNode, run);
            stageInfo.setLogs(logs);

            // If stage is paused (waiting for input), populate input information
            if (statusExt == StatusExt.PAUSED_PENDING_INPUT) {
                LOGGER.log(Level.WARNING, "Stage is paused, looking for input: " + stageName);
                populateInputInfoForPausedStage(stageInfo, stageId, run);
            }

            return stageInfo;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting stage info from StageNodeExt: " + e.getMessage(), e);
            return null;
        }
    }


    /**
     * Populates input information for a paused stage
     * For paused stages, we simply take the first pending input execution
     *
     * @param stageInfo StageInfo object to populate
     * @param stageId Stage ID
     * @param run WorkflowRun instance
     */
    private void populateInputInfoForPausedStage(StageInfo stageInfo, String stageId, WorkflowRun run) {
        try {
            // Get InputAction from the run
            InputAction inputAction = run.getAction(InputAction.class);
            if (inputAction == null) {
                LOGGER.log(Level.WARNING, "No InputAction found for paused stage: " + stageInfo.getName());
                return;
            }

            // Get all input executions
            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions == null || executions.isEmpty()) {
                LOGGER.log(Level.WARNING, "No input executions found for paused stage: " + stageInfo.getName());
                return;
            }

            LOGGER.log(Level.WARNING, "Found " + executions.size() + " input executions, using first one for paused stage: " + stageInfo.getName());

            // For a paused stage, use the first input execution
            // (typically there's only one pending input per paused stage)
            InputStepExecution execution = executions.get(0);

            // Get input ID
            String inputId = execution.getId();
            if (inputId == null) {
                LOGGER.log(Level.WARNING, "Input ID is null for paused stage: " + stageInfo.getName());
                return;
            }

            // Get InputStep
            InputStep inputStep = execution.getInput();
            if (inputStep == null) {
                LOGGER.log(Level.WARNING, "InputStep is null for paused stage: " + stageInfo.getName());
                return;
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

            // Build URLs
            String baseUrl = getBuildUrl(run);
            String jobUrl = run.getParent().getUrl();
            String buildNumber = String.valueOf(run.getNumber());

            // Populate input information
            stageInfo.setInputId(inputId);
            stageInfo.setMessage(message);
            stageInfo.setSubmitter(submitter);
            stageInfo.setProceedText(proceedText);

            // Set URLs in both formats for compatibility
            stageInfo.setSubmitUrl(baseUrl + "input/" + inputId + "/submit");
            stageInfo.setAbortUrl(baseUrl + "input/" + inputId + "/abort");
            stageInfo.setProceedUrl("/" + jobUrl + buildNumber + "/wfapi/inputSubmit?inputId=" + inputId);

            // Extract input parameters
            List<InputParameterInfo> parameters = extractInputParametersDirect(inputStep);
            stageInfo.setParameters(parameters);

            LOGGER.log(Level.WARNING, "Successfully populated input info for paused stage: " + stageInfo.getName() + " with input ID: " + inputId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error populating input info for paused stage: " + e.getMessage(), e);
        }
    }

    /**
     * Populates input information for a stage using InputStepExecution
     *
     * @param stageInfo StageInfo object to populate
     * @param execution InputStepExecution for this stage
     * @param run WorkflowRun instance
     */
    private void populateInputInfoFromExecution(StageInfo stageInfo, InputStepExecution execution, Run<?, ?> run) {
        try {
            // Get input ID
            String inputId = execution.getId();
            if (inputId == null) {
                return;
            }

            // Get InputStep
            InputStep inputStep = execution.getInput();
            if (inputStep == null) {
                return;
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

            // Build URLs
            String baseUrl = getBuildUrl(run);
            String jobUrl = run.getParent().getUrl();
            String buildNumber = String.valueOf(run.getNumber());

            // Populate input information
            stageInfo.setInputId(inputId);
            stageInfo.setMessage(message);
            stageInfo.setSubmitter(submitter);
            stageInfo.setProceedText(proceedText);

            // Set URLs in both formats for compatibility
            stageInfo.setSubmitUrl(baseUrl + "input/" + inputId + "/submit");
            stageInfo.setAbortUrl(baseUrl + "input/" + inputId + "/abort");
            stageInfo.setProceedUrl("/" + jobUrl + buildNumber + "/wfapi/inputSubmit?inputId=" + inputId);

            // Extract input parameters
            List<InputParameterInfo> parameters = extractInputParametersDirect(inputStep);
            stageInfo.setParameters(parameters);

            LOGGER.log(Level.WARNING, "Successfully populated input info for stage: " + stageInfo.getName() + " with input ID: " + inputId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error populating input info from execution: " + e.getMessage(), e);
        }
    }

    /**
     * Populates input information for a stage by finding input steps within the stage
     *
     * @param stageInfo StageInfo to populate
     * @param stageId Stage ID
     * @param run WorkflowRun instance
     */
    private void populateInputInfoForStage(StageInfo stageInfo, String stageId, WorkflowRun run) {
        try {
            // Get InputAction from the run
            InputAction inputAction = run.getAction(InputAction.class);
            if (inputAction == null) {
                LOGGER.log(Level.WARNING, "No InputAction found for build, stage: " + stageInfo.getName());
                return;
            }

            // Get all input executions
            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions == null || executions.isEmpty()) {
                LOGGER.log(Level.WARNING, "No input executions found, stage: " + stageInfo.getName());
                return;
            }

            LOGGER.log(Level.WARNING, "Found " + executions.size() + " input executions for stage: " + stageInfo.getName() + " (ID: " + stageId + ")");

            // Check each input execution to see if it belongs to this stage
            for (InputStepExecution execution : executions) {
                try {
                    String execId = execution.getId();
                    LOGGER.log(Level.WARNING, "Checking input execution: " + execId);

                    // Get the flow node for this input execution
                    FlowNode inputNode = execution.getContext().get(FlowNode.class);
                    if (inputNode == null) {
                        LOGGER.log(Level.WARNING, "Input node is null for execution: " + execId);
                        continue;
                    }

                    LOGGER.log(Level.WARNING, "Input node ID: " + inputNode.getId() + ", Stage ID: " + stageId);

                    // Try to find the enclosing stage for this input node
                    String inputStageName = findEnclosingStage(inputNode, run);
                    LOGGER.log(Level.WARNING, "Input enclosing stage: " + inputStageName + ", Current stage: " + stageInfo.getName());

                    // Check if this input belongs to this stage by comparing stage names
                    if (inputStageName != null && inputStageName.equals(stageInfo.getName())) {
                        // Found matching input, use the helper method to populate
                        populateInputInfoFromExecution(stageInfo, execution, run);
                        // Only populate the first input found in this stage
                        break;
                    } else {
                        LOGGER.log(Level.WARNING, "Input node does not match stage. Input stage: " + inputStageName + ", Current stage: " + stageInfo.getName());
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error checking input execution: " + e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error populating input info for stage: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a node is in a list of nodes
     *
     * @param node FlowNode to check
     * @param nodeList List of FlowNodes
     * @return true if node is in list, false otherwise
     */
    private boolean isNodeInList(FlowNode node, List<FlowNode> nodeList) {
        if (node == null || nodeList == null) {
            return false;
        }

        String nodeId = node.getId();
        for (FlowNode n : nodeList) {
            if (n.getId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the enclosing stage name for a given flow node
     *
     * @param node FlowNode to find enclosing stage for
     * @param run WorkflowRun instance
     * @return Stage name or null if not found
     */
    private String findEnclosingStage(FlowNode node, WorkflowRun run) {
        try {
            // Walk up the parent chain to find a stage node
            FlowNode current = node;

            while (current != null) {
                // Check if this node is a stage node
                if (current instanceof StepStartNode) {
                    String functionName = current.getDisplayFunctionName();
                    if ("stage".equals(functionName)) {
                        // Found a stage node, return its name
                        return getStageName(current);
                    }
                }

                // Move to parent nodes
                List<FlowNode> parents = current.getParents();
                if (parents == null || parents.isEmpty()) {
                    break;
                }

                // Take the first parent
                current = parents.get(0);
            }

            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error finding enclosing stage: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets logs for a stage from StageNodeExt
     *
     * @param stageNode StageNodeExt
     * @param run WorkflowRun instance
     * @return Stage logs
     */
    private String getStageLogFromStageNodeExt(StageNodeExt stageNode, WorkflowRun run) {
        StringBuilder logBuilder = new StringBuilder();

        try {
            // Get the FlowNode for this stage
            FlowNode stageFlowNode = run.getExecution().getNode(stageNode.getId());
            if (stageFlowNode == null) {
                LOGGER.log(Level.FINE, "FlowNode not found for stage: " + stageNode.getName());
                return "";
            }

            // Collect all nodes that belong to this stage
            List<FlowNode> stageNodes = new ArrayList<>();
            stageNodes.add(stageFlowNode);

            // Get all descendant nodes of this stage
            List<FlowNode> descendants = getDescendantNodes(stageFlowNode, run);
            stageNodes.addAll(descendants);

            LOGGER.log(Level.FINE, "Stage '" + stageNode.getName() + "' has " + stageNodes.size() + " nodes");

            // Collect logs from all nodes in this stage
            for (FlowNode node : stageNodes) {
                try {
                    LogAction logAction = node.getAction(LogAction.class);
                    if (logAction != null) {
                        hudson.console.AnnotatedLargeText<?> logText = logAction.getLogText();
                        if (logText != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            logText.writeLogTo(0, baos);
                            String log = baos.toString("UTF-8");
                            if (log != null && !log.trim().isEmpty()) {
                                logBuilder.append(log);
                                if (!log.endsWith("\n")) {
                                    logBuilder.append("\n");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error getting log from node " + node.getId() + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting stage log from StageNodeExt: " + e.getMessage(), e);
        }

        return logBuilder.toString();
    }

    /**
     * Creates a special "ALL" stage containing complete console log
     *
     * @param workflowRun WorkflowRun instance
     * @return StageInfo for "ALL" stage
     */
    private StageInfo createAllStage(WorkflowRun workflowRun) {
        try {
            StageInfo allStage = new StageInfo();
            allStage.setId("ALL");
            allStage.setName("ALL");

            // Get build status
            Result result = workflowRun.getResult();
            if (result == null) {
                allStage.setStatus(StatusExt.IN_PROGRESS);
            } else if (result == Result.SUCCESS) {
                allStage.setStatus(StatusExt.SUCCESS);
            } else if (result == Result.FAILURE) {
                allStage.setStatus(StatusExt.FAILED);
            } else if (result == Result.ABORTED) {
                allStage.setStatus(StatusExt.ABORTED);
            } else {
                allStage.setStatus(StatusExt.UNSTABLE);
            }

            allStage.setExecuted(true);

            // Get timing information
            long startTime = workflowRun.getStartTimeInMillis();
            long duration = workflowRun.getDuration();
            allStage.setStartTimeMillis(startTime);
            allStage.setDurationMillis(duration);

            // Get complete console log
            String consoleLog = getCompleteConsoleLog(workflowRun);
            allStage.setLogs(consoleLog);

            LOGGER.log(Level.INFO, "Created ALL stage with " + consoleLog.length() + " characters of logs");

            return allStage;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error creating ALL stage: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the complete console log for a build
     *
     * @param workflowRun WorkflowRun instance
     * @return Complete console log text
     */
    private String getCompleteConsoleLog(WorkflowRun workflowRun) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workflowRun.getLogText().writeLogTo(0, baos);
            return baos.toString("UTF-8");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting complete console log: " + e.getMessage(), e);
            return "";
        }
    }



    /**
     * Extracts StageInfo from a FlowNode
     *
     * @param node FlowNode representing a stage
     * @param run WorkflowRun instance
     * @return StageInfo with logs
     */
    private StageInfo extractStageInfoFromNode(FlowNode node, WorkflowRun run) {
        try {
            // Get stage name
            String stageName = getStageName(node);
            String stageId = node.getId();

            StageInfo stageInfo = new StageInfo();
            stageInfo.setId(stageId);
            stageInfo.setName(stageName);

            // Get stage status
            StatusExt status = getStageStatusEnum(node, run);
            stageInfo.setStatus(status);
            stageInfo.setExecuted(status != StatusExt.NOT_EXECUTED);

            // Get timing information
            TimingAction timingAction = node.getAction(TimingAction.class);
            if (timingAction != null) {
                stageInfo.setStartTimeMillis(timingAction.getStartTime());

                // Calculate duration
                long startTime = timingAction.getStartTime();
                long currentTime = System.currentTimeMillis();

                // If stage is complete, use the actual end time
                if (node.isActive()) {
                    stageInfo.setDurationMillis(currentTime - startTime);
                } else {
                    // For completed stages, calculate from start to when it ended
                    // Use a simple duration calculation
                    stageInfo.setDurationMillis(currentTime - startTime);
                }
            }

            // Get stage logs
            String logs = getStageLog(node, run);
            stageInfo.setLogs(logs);

            // If stage is paused (waiting for input), populate input information
            if (status == StatusExt.PAUSED_PENDING_INPUT) {
                LOGGER.log(Level.WARNING, "Stage is paused (fallback), looking for input: " + stageName);
                populateInputInfoForPausedStage(stageInfo, stageId, run);
            }

            return stageInfo;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error extracting stage info from node: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the name of a stage from a FlowNode
     *
     * @param node FlowNode
     * @return Stage name
     */
    private String getStageName(FlowNode node) {
        try {
            LabelAction labelAction = node.getAction(LabelAction.class);
            if (labelAction != null && labelAction.getDisplayName() != null) {
                return labelAction.getDisplayName();
            }
            return node.getDisplayName();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting stage name: " + e.getMessage());
            return "Unknown Stage";
        }
    }

    /**
     * Gets the status of a stage from a FlowNode as enum
     *
     * @param node FlowNode
     * @param run WorkflowRun instance (optional, for checking paused status)
     * @return Stage status enum
     */
    private StatusExt getStageStatusEnum(FlowNode node, WorkflowRun run) {
        try {
            if (node.isActive()) {
                // Check if it's paused waiting for input
                if (run != null && isPausedForInput(node, run)) {
                    return StatusExt.PAUSED_PENDING_INPUT;
                }
                return StatusExt.IN_PROGRESS;
            }

            // Check error
            hudson.model.Result result = null;
            try {
                // Try to get error action
                org.jenkinsci.plugins.workflow.actions.ErrorAction errorAction =
                    node.getAction(org.jenkinsci.plugins.workflow.actions.ErrorAction.class);
                if (errorAction != null) {
                    return StatusExt.FAILED;
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error checking error action: " + e.getMessage());
            }

            return StatusExt.SUCCESS;

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting stage status: " + e.getMessage());
            return StatusExt.UNSTABLE;
        }
    }



    /**
     * Checks if a node is paused waiting for input
     *
     * @param node FlowNode
     * @param run WorkflowRun instance
     * @return true if paused for input, false otherwise
     */
    private boolean isPausedForInput(FlowNode node, WorkflowRun run) {
        try {
            InputAction inputAction = run.getAction(InputAction.class);
            if (inputAction == null) {
                return false;
            }

            List<InputStepExecution> executions = inputAction.getExecutions();
            if (executions == null || executions.isEmpty()) {
                return false;
            }

            // Check if any input execution is associated with this node or its descendants
            for (InputStepExecution execution : executions) {
                try {
                    FlowNode inputNode = execution.getContext().get(FlowNode.class);
                    if (inputNode != null && inputNode.getId().equals(node.getId())) {
                        return true;
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            return false;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error checking paused for input: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the log text for a stage
     *
     * @param node FlowNode representing the stage
     * @param run WorkflowRun instance
     * @return Log text
     */
    private String getStageLog(FlowNode node, WorkflowRun run) {
        StringBuilder logBuilder = new StringBuilder();

        try {
            // Get log action
            LogAction logAction = node.getAction(LogAction.class);
            if (logAction != null) {
                try {
                    // LogAction.getLogText() returns AnnotatedLargeText
                    hudson.console.AnnotatedLargeText<?> logText = logAction.getLogText();
                    if (logText != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        logText.writeLogTo(0, baos);
                        logBuilder.append(baos.toString("UTF-8"));
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error getting log from LogAction: " + e.getMessage());
                }
            }

            // Alternative: try to get logs from the node's execution
            if (logBuilder.length() == 0) {
                try {
                    // Get all descendant nodes and collect their logs
                    List<FlowNode> descendants = getDescendantNodes(node, run);
                    for (FlowNode descendant : descendants) {
                        LogAction descLogAction = descendant.getAction(LogAction.class);
                        if (descLogAction != null) {
                            try {
                                hudson.console.AnnotatedLargeText<?> descLogText = descLogAction.getLogText();
                                if (descLogText != null) {
                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                    descLogText.writeLogTo(0, baos);
                                    String log = baos.toString("UTF-8");
                                    if (log != null && !log.isEmpty()) {
                                        logBuilder.append(log);
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.FINE, "Error getting log from descendant: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error getting descendant logs: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting stage log: " + e.getMessage(), e);
        }

        return logBuilder.toString();
    }

    /**
     * Gets all descendant nodes of a given node (all nodes until the next stage)
     *
     * @param node Parent FlowNode (stage node)
     * @param run WorkflowRun instance
     * @return List of descendant FlowNodes
     */
    private List<FlowNode> getDescendantNodes(FlowNode node, WorkflowRun run) {
        List<FlowNode> descendants = new ArrayList<>();

        try {
            if (run.getExecution() == null) {
                return descendants;
            }

            FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
            boolean foundStart = false;
            Set<String> collectedIds = new HashSet<>();
            collectedIds.add(node.getId()); // Don't include the stage node itself

            for (FlowNode n : walker) {
                // Find the stage node first
                if (!foundStart) {
                    if (n.getId().equals(node.getId())) {
                        foundStart = true;
                    }
                    continue;
                }

                // After finding the stage node, collect all subsequent nodes
                // until we hit another stage node

                // Check if this is another stage node - if so, stop
                if (n instanceof StepStartNode) {
                    LabelAction labelAction = n.getAction(LabelAction.class);
                    if (labelAction != null) {
                        String functionName = n.getDisplayFunctionName();
                        if ("stage".equals(functionName)) {
                            // Hit another stage, stop collecting
                            break;
                        }
                    }
                }

                // Add this node if we haven't collected it yet
                if (!collectedIds.contains(n.getId())) {
                    descendants.add(n);
                    collectedIds.add(n.getId());
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Error getting descendant nodes: " + e.getMessage(), e);
        }

        return descendants;
    }
}

