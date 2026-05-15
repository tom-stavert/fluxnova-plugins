package org.finos.fluxnova.bpm.engine.ai.agent.extract;

import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.xml.BpmnXmlParser;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Parse;

import static org.finos.fluxnova.bpm.engine.shared.agent.AgentModelConstants.AGENT_NS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class AgentConfigExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(AgentConfigExtractor.class);

    private final AgentConfigElementWalker walker;

    public AgentConfigExtractor() {
        this(new AgentConfigElementWalker());
    }

    AgentConfigExtractor(AgentConfigElementWalker walker) {
        this.walker = walker;
    }

    public List<AgentConfig> extractAll(InputStream bpmnXml, String processDefinitionId) {
        List<AgentConfig> results = new ArrayList<>();
        Parse parse = new BpmnXmlParser().createParse().sourceInputStream(bpmnXml).execute();
        Element root = parse.getRootElement();
        for (Element process : root.elements("process")) {
            List<Element> walked = walker.walk(process);
            Set<String> elementIds = collectElementIds(process, walked);
            for (Element element : walked) {
                extract(element, processDefinitionId).ifPresent(config -> {
                    validateToolScope(config, elementIds);
                    results.add(config);
                });
            }
        }
        return results;
    }

    private Set<String> collectElementIds(Element process, List<Element> walked) {
        Set<String> ids = new HashSet<>();
        String processId = process.attribute("id");
        if (processId != null) {
            ids.add(processId);
        }
        for (Element element : walked) {
            String id = element.attribute("id");
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private void validateToolScope(AgentConfig config, Set<String> elementIds) {
        if (!elementIds.contains(config.toolScopeElementId())) {
            throw new IllegalArgumentException(
                "agent:config on element '" + config.elementId()
                    + "' references unknown toolScopeElementId '" + config.toolScopeElementId() + "'");
        }
    }

    public Optional<AgentConfig> extract(Element element, String processDefinitionId) {
        Element ext = element.element("extensionElements");
        if (ext == null) {
            return Optional.empty();
        }

        Element config = ext.elementNS(AGENT_NS, "config");
        if (config == null) {
            return Optional.empty();
        }

        String elementId = element.attribute("id");
        String provider = config.attribute("provider");
        String model = config.attribute("model");
        String systemPrompt = config.attribute("systemPrompt");

        if (isBlank(provider) || isBlank(model) || isBlank(systemPrompt)) {
            LOG.warn("agent:config on element '{}' is missing required attribute(s) (provider, model, systemPrompt) - skipping", elementId);
            return Optional.empty();
        }

        String toolScopeElementId = config.attribute("toolScopeElementId");
        if (isBlank(toolScopeElementId)) {
            toolScopeElementId = elementId;
        }

        return Optional.of(new AgentConfig(processDefinitionId, elementId, provider, model, systemPrompt, toolScopeElementId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
