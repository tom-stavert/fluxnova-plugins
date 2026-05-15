package org.finos.fluxnova.bpm.engine.ai.agent.extract;

import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;

import java.util.ArrayList;
import java.util.List;

public class AgentConfigElementWalker {

    public List<Element> walk(Element root) {
        List<Element> elements = new ArrayList<>();
        collect(root, elements);
        return elements;
    }

    private void collect(Element parent, List<Element> elements) {
        for (Element child : parent.elements()) {
            elements.add(child);
            if (!"extensionElements".equals(child.getTagName())) {
                collect(child, elements);
            }
        }
    }
}