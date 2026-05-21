package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.integration;

import org.finos.fluxnova.bpm.engine.ManagementService;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.LlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.runtime.Execution;
import org.finos.fluxnova.bpm.engine.runtime.Job;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = TestApplication.class)
@Import(TestConfig.class)
class AgentOrchestrationIntegrationTest {

    private static final String PROCESS_KEY = "agentProcess";
    private static final String AD_HOC_ID = "agentScope";
    private static final String DELEGATE_CLASS =
            "org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.integration.StaticOutputDelegate";

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private ManagementService managementService;

    @Autowired
    private LlmService llmOrchestrationService;

    @Autowired
    private AgentStateManager stateManager;

    private String deploymentId;

    @BeforeEach
    void resetMocks() {
        reset(llmOrchestrationService);
    }

    @AfterEach
    void cleanup() {
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
            deploymentId = null;
        }
    }

    // -----------------------------------------------------------------------
    // Scenario 1: Single-turn completion — LLM returns text, no tool calls
    // -----------------------------------------------------------------------

    @Test
    void singleTurnCompletion_noToolCalls_subprocessCompletes() {
        deploy(agentXml("toolA"));

        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(textOnlyResponse("I'm done, no tools needed."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Entry listener creates the first orchestration job
        executeNextJob(pi);

        // LLM returned no tool calls — subprocess should have completed
        assertProcessEnded(pi);
        verify(llmOrchestrationService, times(1)).call(any(), any(), any(), anyList());
    }

    // -----------------------------------------------------------------------
    // Scenario 2: Single tool call, single turn
    // -----------------------------------------------------------------------

    @Test
    void singleToolCall_singleTurn_completesAfterToolResult() {
        deploy(agentXml("toolA"));

        // First LLM call: request toolA
        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(toolCallResponse("call-1", "toolA"))
                .thenReturn(textOnlyResponse("Done after using toolA."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Step 1: Entry job — LLM requests toolA
        executeNextJob(pi);

        Execution adHocExec = adHocExecution(pi);
        assertThat(adHocExec).isNotNull();
        assertThat(stateManager.isPendingToolCall(adHocExec.getId(), "call-1")).isTrue();

        // The tool activity (service task) runs and completes synchronously.
        // SubprocessToolCompletionListener fires and creates the next orchestration job.

        // Step 2: Tool completion job — feeds result to LLM, LLM returns text
        executeNextJob(pi);

        // Verify conversation history was accumulated
        verify(llmOrchestrationService, times(2)).call(any(), any(), any(), anyList());
        assertProcessEnded(pi);
    }

    // -----------------------------------------------------------------------
    // Scenario 3: Multi-turn conversation
    // -----------------------------------------------------------------------

    @Test
    void multiTurn_twoSequentialToolCalls_completesAfterBoth() {
        deploy(agentXml("toolA", "toolB"));

        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(toolCallResponse("call-1", "toolA"))
                .thenReturn(toolCallResponse("call-2", "toolB"))
                .thenReturn(textOnlyResponse("All done after two tools."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Step 1: Entry — LLM requests toolA
        executeNextJob(pi);
        assertProcessNotEnded(pi);

        // Step 2: toolA completes — LLM requests toolB
        executeNextJob(pi);
        assertProcessNotEnded(pi);

        // Step 3: toolB completes — LLM returns text, subprocess completes
        executeNextJob(pi);

        verify(llmOrchestrationService, times(3)).call(any(), any(), any(), anyList());
        assertProcessEnded(pi);
    }

    // -----------------------------------------------------------------------
    // Scenario 4: Parallel tool calls
    // -----------------------------------------------------------------------

    @Test
    void parallelToolCalls_allCompleteBeforeNextLlmCall() {
        deploy(agentXml("toolA", "toolB"));

        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(parallelToolCallResponse(new ToolCallRequest("call-1", "toolA"),
                        new ToolCallRequest("call-2", "toolB")))
                .thenReturn(textOnlyResponse("Done after parallel tools."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Step 1: Entry — LLM requests toolA and toolB in parallel
        executeNextJob(pi);

        Execution adHocExec = adHocExecution(pi);
        assertThat(adHocExec).isNotNull();
        assertThat(stateManager.isPendingToolCall(adHocExec.getId(), "call-1")).isTrue();
        assertThat(stateManager.isPendingToolCall(adHocExec.getId(), "call-2")).isTrue();

        // Both tool activities run. Each completion fires a tool-completion job.
        // The first tool-completion job removes its ID from pending but doesn't
        // trigger the next LLM call (pending still has the other).
        executeNextJob(pi);
        assertProcessNotEnded(pi);

        // The second tool-completion job clears pending and triggers the next LLM call.
        executeNextJob(pi);

        verify(llmOrchestrationService, times(2)).call(any(), any(), any(), anyList());
        assertProcessEnded(pi);
    }

    // -----------------------------------------------------------------------
    // Scenario 5: Tool invocation failure
    // -----------------------------------------------------------------------

    @Test
    void toolInvocationFailure_errorFedBackToLlm() {
        deploy(agentXml("toolA"));

        // LLM requests a tool that doesn't exist in the subprocess
        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(toolCallResponse("call-1", "nonExistentTool"))
                .thenReturn(textOnlyResponse("Understood, the tool failed."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Step 1: Entry — LLM requests nonExistentTool, invocation fails
        executeNextJob(pi);

        // The failure should be buffered and a follow-up job scheduled immediately
        // (no pending tools, all failed → scheduleNextStep)

        // Step 2: Follow-up job — error result fed back to LLM, LLM returns text
        executeNextJob(pi);

        verify(llmOrchestrationService, times(2)).call(any(), any(), any(), anyList());
        assertProcessEnded(pi);
    }

    // -----------------------------------------------------------------------
    // Scenario 6: Empty tool catalogue — early termination
    // -----------------------------------------------------------------------

    @Test
    void emptyToolCatalogue_terminatesImmediately() {
        deploy(agentXmlNoTools());

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Entry job fires — job handler detects empty catalogue and completes
        executeNextJob(pi);

        assertProcessEnded(pi);
    }

    // -----------------------------------------------------------------------
    // Scenario 7: Inactive execution guard
    // -----------------------------------------------------------------------

    @Test
    void inactiveExecution_orchestrationStepSkipped() {
        deploy(agentXml("toolA"));

        when(llmOrchestrationService.call(any(), any(), any(), anyList()))
                .thenReturn(textOnlyResponse("Done."));

        ProcessInstance pi = runtimeService.startProcessInstanceByKey(PROCESS_KEY);

        // Execute the entry job — subprocess completes (text-only response)
        executeNextJob(pi);
        assertProcessEnded(pi);

        // If there were any stale jobs remaining for this execution, executing
        // them should be a no-op due to the inactive execution guard.
        List<Job> remainingJobs =
                managementService.createJobQuery().processInstanceId(pi.getId()).list();
        for (Job job : remainingJobs) {
            managementService.executeJob(job.getId());
        }

        // LLM should only have been called once (the entry step)
        verify(llmOrchestrationService, times(1)).call(any(), any(), any(), anyList());
    }

    // -----------------------------------------------------------------------
    // Helpers — job execution
    // -----------------------------------------------------------------------

    private void executeNextJob(ProcessInstance pi) {
        List<Job> jobs = managementService.createJobQuery().processInstanceId(pi.getId()).list();
        assertThat(jobs).as("Expected at least one executable job").isNotEmpty();
        managementService.executeJob(jobs.get(0).getId());
    }

    // -----------------------------------------------------------------------
    // Helpers — process assertions
    // -----------------------------------------------------------------------

    private Execution adHocExecution(ProcessInstance pi) {
        return runtimeService.createExecutionQuery().processInstanceId(pi.getId())
                .activityId(AD_HOC_ID).singleResult();
    }

    private void assertProcessEnded(ProcessInstance pi) {
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId())
                .singleResult()).as("Process instance should have ended").isNull();
    }

    private void assertProcessNotEnded(ProcessInstance pi) {
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(pi.getId())
                .singleResult()).as("Process instance should still be running").isNotNull();
    }

    // -----------------------------------------------------------------------
    // Helpers — LLM response builders
    // -----------------------------------------------------------------------

    private static LlmResponse textOnlyResponse(String text) {
        return new LlmResponse(text, List.of(),
                List.of(ConversationEntry.assistant(text, List.of())));
    }

    private static LlmResponse toolCallResponse(String toolCallId, String toolId) {
        List<ToolCallRequest> toolCalls = List.of(new ToolCallRequest(toolCallId, toolId));
        return new LlmResponse(null, toolCalls,
                List.of(ConversationEntry.assistant(null, toolCalls)));
    }

    private static LlmResponse parallelToolCallResponse(ToolCallRequest... requests) {
        List<ToolCallRequest> toolCalls = List.of(requests);
        return new LlmResponse(null, toolCalls,
                List.of(ConversationEntry.assistant(null, toolCalls)));
    }

    // -----------------------------------------------------------------------
    // Helpers — BPMN XML builders
    // -----------------------------------------------------------------------

    private void deploy(String bpmnXml) {
        deploymentId = repositoryService.createDeployment().addString("agent-process.bpmn", bpmnXml)
                .deploy().getId();
    }

    private static String agentXml(String... toolActivityIds) {
        StringBuilder tools = new StringBuilder();
        for (String toolId : toolActivityIds) {
            tools.append("""
                    <serviceTask id="%s" name="%s" camunda:class="%s"/>
                    """.formatted(toolId, toolId, DELEGATE_CLASS));
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent"
                  targetNamespace="http://test">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"><outgoing>toAgent</outgoing></startEvent>
                    <sequenceFlow id="toAgent" sourceRef="start" targetRef="%s"/>
                    <adHocSubProcess id="%s" ordering="Parallel">
                      <extensionElements>
                        <agent:config provider="test" model="test-model"
                          systemPrompt="You are a test agent."/>
                      </extensionElements>
                      <incoming>toAgent</incoming>
                      <outgoing>toEnd</outgoing>
                %s    <completionCondition>${false}</completionCondition>
                    </adHocSubProcess>
                    <sequenceFlow id="toEnd" sourceRef="%s" targetRef="end"/>
                    <endEvent id="end"><incoming>toEnd</incoming></endEvent>
                  </process>
                </definitions>
                """.formatted(PROCESS_KEY, AD_HOC_ID, AD_HOC_ID, tools, AD_HOC_ID);
    }

    private static String agentXmlNoTools() {
        return agentXml();
    }
}
