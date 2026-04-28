package org.finos.fluxnova.bpm.engine.ai.agent.registry;

import org.finos.fluxnova.bpm.engine.ProcessEngineException;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.ai.agent.extract.AgentConfigExtractor;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AgentConfigRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AgentConfigRegistry.class);

    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> scanned = new ConcurrentHashMap<>();

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
        scanned.computeIfAbsent(processDefinitionId, this::doScan);
    }

    private Boolean doScan(String processDefinitionId) {
        try (InputStream xml = repositoryService.getProcessModel(processDefinitionId)) {
            extractor.extractAll(xml, processDefinitionId)
                    .forEach(config -> configs.put(key(processDefinitionId, config.elementId()), config));
            return Boolean.TRUE;
        } catch (IOException e) {
            LOG.error("I/O error while scanning agent configurations for process definition '{}'", processDefinitionId, e);
            // Return null to avoid caching the failure; this allows computeIfAbsent to retry on the next call
            return null;
        } catch (ProcessEngineException e) {
            LOG.error("Engine error while scanning agent configurations for process definition '{}'", processDefinitionId, e);
            // Return null to avoid caching the failure; this allows computeIfAbsent to retry on the next call
            return null;
        }
    }

    private static String key(String processDefinitionId, String elementId) {
        return processDefinitionId + "#" + elementId;
    }
}
