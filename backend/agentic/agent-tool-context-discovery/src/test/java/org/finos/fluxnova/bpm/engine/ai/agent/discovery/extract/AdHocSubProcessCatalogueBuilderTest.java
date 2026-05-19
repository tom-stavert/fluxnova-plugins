package org.finos.fluxnova.bpm.engine.ai.agent.discovery.extract;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.InputParameter;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.IoMapping;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.OutputParameter;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.value.ConstantValueProvider;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.value.ListValueProvider;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.value.MapValueProvider;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.value.NullValueProvider;
import org.finos.fluxnova.bpm.engine.impl.core.variable.mapping.value.ParameterValueProvider;
import org.finos.fluxnova.bpm.engine.impl.el.ElValueProvider;
import org.finos.fluxnova.bpm.engine.impl.el.Expression;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ActivityImpl;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.finos.fluxnova.bpm.engine.impl.scripting.ScriptValueProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdHocSubProcessCatalogueBuilderTest {

    private AdHocSubProcessCatalogueBuilder builder;
    private ProcessDefinitionImpl procDef;

    @BeforeEach
    void setUp() {
        builder = new AdHocSubProcessCatalogueBuilder();
        procDef = new ProcessDefinitionImpl("proc:1");
    }

    private ActivityImpl createScope(String id) {
        return procDef.createActivity(id);
    }

    private ActivityImpl addActivity(ActivityImpl scope, String id, String type, String name) {
        ActivityImpl activity = scope.createActivity(id);
        activity.setProperty("type", type);
        activity.setProperty("name", name);
        return activity;
    }

    private ElValueProvider elProvider(String expressionText) {
        Expression expression = mock(Expression.class);
        when(expression.getExpressionText()).thenReturn(expressionText);
        return new ElValueProvider(expression);
    }

    @Nested
    class ToolMetadata {

        @Test
        void build_extractsNameProperty() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", "Credit Score Check");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals("Credit Score Check", catalogue.tools().get(0).name());
        }

        @Test
        void build_whenNameAbsent_returnsNull() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", null);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertNull(catalogue.tools().get(0).name());
        }

        @Test
        void build_extractsDocumentation() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            task.setProperty("documentation", "Retrieves the credit score.");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals("Retrieves the credit score.", catalogue.tools().get(0).description());
        }

        @Test
        void build_whenNoDocumentation_descriptionIsNull() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", "Task A");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertNull(catalogue.tools().get(0).description());
        }

        @Test
        void build_whenDocumentationIsBlank_descriptionIsNull() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            task.setProperty("documentation", "   ");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertNull(catalogue.tools().get(0).description());
        }

        @Test
        void build_documentationIsStripped() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            task.setProperty("documentation", "  Some description  ");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals("Some description", catalogue.tools().get(0).description());
        }
    }

    @Nested
    class ReadsExtraction {

        @Test
        void build_simpleElExpression_extractsRead() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("cid", elProvider("${customerId}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customerId"), catalogue.tools().get(0).reads());
        }

        @Test
        void build_dottedElExpression_extractsRootIdentifier() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("email",
                    elProvider("${customer.profile.email}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customer"), catalogue.tools().get(0).reads());
        }

        @Test
        void build_complexExpression_skipped() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("full",
                    elProvider("${firstName + ' ' + lastName}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).reads().isEmpty());
        }

        @Test
        void build_multipleInputParameters_collectsAll() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("a", elProvider("${customerId}")));
            io.addInputParameter(new InputParameter("b", elProvider("${applicationId}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customerId", "applicationId"), catalogue.tools().get(0).reads());
        }

        @Test
        void build_listInputParameter_extractsFromEachValue() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            ListValueProvider list = new ListValueProvider(List.of(
                    elProvider("${primaryEmail}"),
                    elProvider("${fallbackEmail}"),
                    new ConstantValueProvider("noreply@x.com")));
            io.addInputParameter(new InputParameter("emails", list));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("primaryEmail", "fallbackEmail"), catalogue.tools().get(0).reads());
        }

        @Test
        void build_mapInputParameter_extractsFromEntryValues() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            TreeMap<ParameterValueProvider, ParameterValueProvider> providerMap = new TreeMap<>();
            providerMap.put(elProvider("id"), elProvider("${customerId}"));
            providerMap.put(elProvider("name"), elProvider("${customerName}"));
            providerMap.put(elProvider("source"), new ConstantValueProvider("manual"));
            MapValueProvider map = new MapValueProvider(providerMap);
            io.addInputParameter(new InputParameter("data", map));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customerId", "customerName"), catalogue.tools().get(0).reads());
        }

        @Test
        void build_scriptInputParameter_skipped() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            ScriptValueProvider script = mock(ScriptValueProvider.class);
            io.addInputParameter(new InputParameter("val", script));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).reads().isEmpty());
        }

        @Test
        void build_noIoMapping_readsEmpty() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", "Task A");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).reads().isEmpty());
        }

        @Test
        void build_duplicateReadsAreDeduped() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("a", elProvider("${customerId}")));
            io.addInputParameter(new InputParameter("b", elProvider("${customerId}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customerId"), catalogue.tools().get(0).reads());
            assertEquals(1, catalogue.tools().get(0).reads().size());
        }

        @Test
        void build_constantInputParameter_notAddedToReads() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("type", new ConstantValueProvider("manual")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).reads().isEmpty());
        }

        @Test
        void build_nullValueProvider_notAddedToReads() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("x", new NullValueProvider()));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).reads().isEmpty());
        }

        @Test
        void build_mixedSimpleAndComplexParams_onlyExtractsSimple() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("a", elProvider("${customerId}")));
            io.addInputParameter(new InputParameter("b",
                    elProvider("${firstName + ' ' + lastName}")));
            io.addInputParameter(new InputParameter("c", elProvider("${applicationId}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("customerId", "applicationId"), catalogue.tools().get(0).reads());
        }
    }

    @Nested
    class WritesExtraction {

        @Test
        void build_extractsOutputParameterNames() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addOutputParameter(new OutputParameter("creditScore",
                    elProvider("${creditScore}")));
            io.addOutputParameter(new OutputParameter("riskLevel",
                    elProvider("${riskLevel}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("creditScore", "riskLevel"), catalogue.tools().get(0).writes());
        }

        @Test
        void build_noOutputParameters_writesEmpty() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addInputParameter(new InputParameter("a", elProvider("${customerId}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).writes().isEmpty());
        }

        @Test
        void build_noIoMapping_writesEmpty() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", "Task A");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertTrue(catalogue.tools().get(0).writes().isEmpty());
        }

        @Test
        void build_outputParameterWithNullName_isNotAddedToWrites() {
            ActivityImpl scope = createScope("agent1");
            ActivityImpl task = addActivity(scope, "taskA", "serviceTask", "Task A");
            IoMapping io = new IoMapping();
            io.addOutputParameter(new OutputParameter(null, elProvider("${someValue}")));
            io.addOutputParameter(new OutputParameter("valid", elProvider("${other}")));
            task.setIoMapping(io);

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals(Set.of("valid"), catalogue.tools().get(0).writes());
        }
    }

    @Nested
    class CatalogueMetadata {

        @Test
        void build_setsProcessDefinitionIdAndElementId() {
            ActivityImpl scope = createScope("agent1");
            addActivity(scope, "taskA", "serviceTask", "A");

            AgentToolCatalogue catalogue = builder.build(scope);

            assertEquals("proc:1", catalogue.processDefinitionId());
            assertEquals("agent1", catalogue.elementId());
        }
    }

    @Nested
    class ScopeReadFor {

        @Test
        void simpleVariable() {
            assertEquals(Optional.of("customerId"),
                    AdHocSubProcessCatalogueBuilder.scopeReadFor("${customerId}"));
        }

        @Test
        void dottedPath() {
            assertEquals(Optional.of("customer"),
                    AdHocSubProcessCatalogueBuilder.scopeReadFor("${customer.profile.email}"));
        }

        @Test
        void withWhitespace() {
            assertEquals(Optional.of("x"),
                    AdHocSubProcessCatalogueBuilder.scopeReadFor("  ${ x }  "));
        }

        @Test
        void concatenation_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor("${a + b}").isEmpty());
        }

        @Test
        void methodCall_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor("${svc.find(id)}").isEmpty());
        }

        @Test
        void literal_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor("hello").isEmpty());
        }

        @Test
        void null_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor(null).isEmpty());
        }

        @Test
        void emptyString_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor("").isEmpty());
        }

        @Test
        void multipleElSegments_empty() {
            assertTrue(AdHocSubProcessCatalogueBuilder.scopeReadFor("${a}${b}").isEmpty());
        }

        @Test
        void underscoreInIdentifier() {
            assertEquals(Optional.of("_myVar"),
                    AdHocSubProcessCatalogueBuilder.scopeReadFor("${_myVar}"));
        }
    }
}
