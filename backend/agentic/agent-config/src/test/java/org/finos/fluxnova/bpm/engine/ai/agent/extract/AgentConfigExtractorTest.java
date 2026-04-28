package org.finos.fluxnova.bpm.engine.ai.agent.extract;

import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.xml.BpmnXmlParser;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Parse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigExtractorTest {

    private static final String PROCESS_DEFINITION_ID = "proc:1";

    private AgentConfigExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new AgentConfigExtractor();
    }

    private Element parseAdHocSubProcess(String bpmn) {
        BpmnXmlParser parser = new BpmnXmlParser();
        Parse parse = parser.createParse()
                .sourceInputStream(new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)))
                .execute();
        return parse.getRootElement()
                .element("process")
                .element("adHocSubProcess");
    }

    // extract(Element, ...) valid config and defaulting

    @Test
    void extract_whenValidAgentConfig_returnsPopulatedAgentConfig() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="p">
                    <adHocSubProcess id="creditCheckAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"
                                      systemPrompt="You are a credit analyst."
                                      toolScopeElementId="creditCheckAgent"/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isPresent());
        AgentConfig config = result.get();
        assertEquals(PROCESS_DEFINITION_ID, config.processDefinitionId());
        assertEquals("creditCheckAgent", config.elementId());
        assertEquals("ollama", config.provider());
        assertEquals("llama3.1", config.model());
        assertEquals("You are a credit analyst.", config.systemPrompt());
        assertEquals("creditCheckAgent", config.toolScopeElementId());
    }

    @Test
    void extract_whenToolScopeAbsent_defaultsToElementId() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="p">
                    <adHocSubProcess id="myAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"
                                      systemPrompt="You are an assistant."/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isPresent());
        assertEquals("myAgent", result.get().toolScopeElementId());
    }

    @Test
    void extract_whenToolScopeBlank_defaultsToElementId() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="p">
                    <adHocSubProcess id="myAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"
                                      systemPrompt="You are an assistant."
                                      toolScopeElementId=""/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isPresent());
        assertEquals("myAgent", result.get().toolScopeElementId());
    }

    // extract(Element, ...) missing and invalid config

    @Test
    void extract_whenNoExtensionElements_returnsEmpty() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p">
                    <adHocSubProcess id="sub1"/>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void extract_whenExtensionElementsButNoAgentConfig_returnsEmpty() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:other="http://example.com/other">
                  <process id="p">
                    <adHocSubProcess id="sub1">
                      <extensionElements>
                        <other:config provider="x"/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isEmpty());
    }

    @Test
    void extract_whenSystemPromptAbsent_returnsConfigWithNullSystemPrompt() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="p">
                    <adHocSubProcess id="myAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        Optional<AgentConfig> result = extractor.extract(parseAdHocSubProcess(bpmn), PROCESS_DEFINITION_ID);

        assertTrue(result.isPresent());
        assertNull(result.get().systemPrompt());
        assertEquals("ollama", result.get().provider());
        assertEquals("llama3.1", result.get().model());
    }

    // extractAll(...) tool-scope discovery

    @Test
    void extractAll_whenToolScopeReferencesSiblingElement_succeeds() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <subProcess id="toolRegistry"/>
                    <adHocSubProcess id="myAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"
                                      systemPrompt="You are an assistant."
                                      toolScopeElementId="toolRegistry"/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("toolRegistry", results.get(0).toolScopeElementId());
    }

    // extractAll(...) discovery across BPMN structures

    @Test
    void extractAll_findsAgentConfigOnServiceTask() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <serviceTask id="taskAgent">
                      <extensionElements>
                        <agent:config provider="ollama"
                                      model="llama3.1"
                                      systemPrompt="Task agent."/>
                      </extensionElements>
                    </serviceTask>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("taskAgent", results.get(0).elementId());
        assertEquals("taskAgent", results.get(0).toolScopeElementId());
    }

    @Test
    void extractAll_findsAdHocSubProcessInsideSubProcess() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <subProcess id="outer">
                      <adHocSubProcess id="nestedAgent">
                        <extensionElements>
                          <agent:config provider="ollama"
                                        model="llama3.1"
                                        systemPrompt="Nested agent."/>
                        </extensionElements>
                      </adHocSubProcess>
                    </subProcess>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("nestedAgent", results.get(0).elementId());
    }

    @Test
    void extractAll_findsAdHocSubProcessInsideEventSubProcess() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <subProcess id="eventSub" triggeredByEvent="true">
                      <adHocSubProcess id="eventAgent">
                        <extensionElements>
                          <agent:config provider="ollama"
                                        model="llama3.1"
                                        systemPrompt="Event agent."/>
                        </extensionElements>
                      </adHocSubProcess>
                    </subProcess>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("eventAgent", results.get(0).elementId());
    }

    @Test
    void extractAll_findsAdHocSubProcessInsideTransaction() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <transaction id="tx1">
                      <adHocSubProcess id="transactionAgent">
                        <extensionElements>
                          <agent:config provider="ollama"
                                        model="llama3.1"
                                        systemPrompt="Transaction agent."/>
                        </extensionElements>
                      </adHocSubProcess>
                    </transaction>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("transactionAgent", results.get(0).elementId());
    }

    @Test
    void extractAll_findsMultipleAdHocSubProcessesInOneProcess() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <adHocSubProcess id="agentA">
                      <extensionElements>
                        <agent:config provider="ollama" model="llama3.1" systemPrompt="Agent A."/>
                      </extensionElements>
                    </adHocSubProcess>
                    <adHocSubProcess id="agentB">
                      <extensionElements>
                        <agent:config provider="ollama" model="llama3.1" systemPrompt="Agent B."/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(2, results.size());
        assertEquals("agentA", results.get(0).elementId());
        assertEquals("agentB", results.get(1).elementId());
    }

    @Test
    void extractAll_onlyWalksProcessMatchingProcessDefinitionId() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:agent="http://fluxnova.finos.org/schema/1.0/ai/agent">
                  <process id="proc">
                    <adHocSubProcess id="agentP1">
                      <extensionElements>
                        <agent:config provider="ollama" model="llama3.1" systemPrompt="P1 agent."/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                  <process id="other">
                    <adHocSubProcess id="agentOther">
                      <extensionElements>
                        <agent:config provider="ollama" model="llama3.1" systemPrompt="Other agent."/>
                      </extensionElements>
                    </adHocSubProcess>
                  </process>
                </definitions>
                """;

        List<AgentConfig> results = extractor.extractAll(
                new ByteArrayInputStream(bpmn.getBytes(StandardCharsets.UTF_8)), PROCESS_DEFINITION_ID);

        assertEquals(1, results.size());
        assertEquals("agentP1", results.get(0).elementId());
    }
}
