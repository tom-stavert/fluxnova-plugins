package org.finos.fluxnova.bpm.engine.ai.agent.registry;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AgentConfigRegistry {

    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();
    private final Set<String> scanned = ConcurrentHashMap.newKeySet();

    private final RepositoryService repositoryService;
    private final AgentConfigExtractor extractor;

    public AgentConfigRegistry(RepositoryService repositoryService, AgentConfigExtractor extractor) {
        this.repositoryService = repositoryService;
        this.extractor = extractor;
    }

    public Optional<AgentConfig> resolve(String processDefinitionId, String elementId) {
        ensureScanned(processDefinitionId);
        return Optional.ofNullable(configs.get(key(processDefinitionId, elementId)));
    }

    public void unregisterAll() {
        configs.clear();
        scanned.clear();
    }

    private void ensureScanned(String processDefinitionId) {
        if (!scanned.contains(processDefinitionId)) {
            doScan(processDefinitionId);
            scanned.add(processDefinitionId);
        }
    }

    private void doScan(String processDefinitionId) {
        InputStream xml = repositoryService.getProcessModel(processDefinitionId);
        extractor.extractAll(xml, processDefinitionId)
                .forEach(config -> configs.put(key(processDefinitionId, config.elementId()), config));
    }

    private static String key(String processDefinitionId, String elementId) {
        return processDefinitionId + "#" + elementId;
    }
}
