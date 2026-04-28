package org.finos.fluxnova.bpm.engine.ai.agent.extract;

import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AgentConfigValidator {

    private AgentConfigValidator() {}

    public static List<String> validate(Element configElement, String elementId, Set<String> processElementIds) {
        List<String> errors = new ArrayList<>();

        if (isBlank(configElement.attribute("provider"))) {
            errors.add("agent:config on element '" + elementId + "' is missing required attribute 'provider'");
        }
        if (isBlank(configElement.attribute("model"))) {
            errors.add("agent:config on element '" + elementId + "' is missing required attribute 'model'");
        }

        String toolScopeElementId = configElement.attribute("toolScopeElementId");
        if (!isBlank(toolScopeElementId) && !processElementIds.contains(toolScopeElementId)) {
            errors.add("agent:config on element '" + elementId
                    + "' references unknown toolScopeElementId '" + toolScopeElementId + "'");
        }

        return errors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
