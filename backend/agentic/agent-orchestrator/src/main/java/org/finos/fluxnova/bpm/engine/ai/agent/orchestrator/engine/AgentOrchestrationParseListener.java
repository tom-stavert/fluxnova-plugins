package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.engine;

import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.pvm.PvmEvent;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ActivityImpl;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ScopeImpl;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentModelConstants;

public class AgentOrchestrationParseListener extends AbstractBpmnParseListener {

    private final AgentSubprocessEntryListener subprocessEntryListener;
    private final ToolCompletionListener toolCompletionListener;

    public AgentOrchestrationParseListener(AgentSubprocessEntryListener subprocessEntryListener,
            ToolCompletionListener toolCompletionListener) {
        this.subprocessEntryListener = subprocessEntryListener;
        this.toolCompletionListener = toolCompletionListener;
    }

    // this override will only work properly when ad-hoc subprocesses are implemented
    @Override
    public void parseAdHocSubProcess(Element element, ScopeImpl scope, ActivityImpl activity) {
        Element ext = element.element("extensionElements");
        if (ext == null) {
            return;
        }
        if (ext.elementNS(AgentModelConstants.AGENT_NS, "config") == null) {
            return;
        }

        activity.addBuiltInListener(PvmEvent.EVENTNAME_START, subprocessEntryListener);

        for (ActivityImpl child : activity.getActivities()) {
            if (!child.getIncomingTransitions().isEmpty()) {
                continue;
            }
            child.addBuiltInListener(PvmEvent.EVENTNAME_END, toolCompletionListener);
        }
    }
}
