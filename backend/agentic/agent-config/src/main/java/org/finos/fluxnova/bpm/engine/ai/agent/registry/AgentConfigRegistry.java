package org.finos.fluxnova.bpm.engine.ai.agent.registry;

import org.finos.fluxnova.bpm.engine.ProcessEngineException;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AgentConfigRegistry {

    private final ConcurrentHashMap<String, HashMap<String, AgentConfig>> configCache = new ConcurrentHashMap<>();

    private final RepositoryService repositoryService;
    private final AgentConfigExtractor extractor;

    public AgentConfigRegistry(RepositoryService repositoryService, AgentConfigExtractor extractor) {
        this.repositoryService = repositoryService;
        this.extractor = extractor;
    }

    public Optional<AgentConfig> resolve(String processDefinitionId, String elementId) {
        HashMap<String, AgentConfig> definitionConfigs = configCache.computeIfAbsent(
                processDefinitionId, this::doScan);
        return Optional.ofNullable(definitionConfigs.get(elementId));
    }

    public void unregisterAll() {
        configCache.clear();
    }

    private HashMap<String, AgentConfig> doScan(String processDefinitionId) {
        InputStream xml;
        try {
            xml = repositoryService.getProcessModel(processDefinitionId);
        } catch (ProcessEngineException e) {
            throw new ProcessEngineException(
                    "Failed to load BPMN process model for processDefinitionId '" + processDefinitionId
                            + "' while resolving agent configuration",
                    e);
        }
        HashMap<String, AgentConfig> result = new HashMap<>();
        extractor.extractAll(xml, processDefinitionId)
                .forEach(config -> result.put(config.elementId(), config));
        return result;
    }
}
