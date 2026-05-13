package org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdHocSubprocessTerminatorTest {

    private static final String SCOPE_EXECUTION_ID = "scope-exec-001";

    @Mock
    private RuntimeService runtimeService;

    private AdHocSubprocessTerminator terminator;

    @BeforeEach
    void setUp() {
        terminator = new AdHocSubprocessTerminator(runtimeService);
    }

    @Test
    void complete_delegatesToRuntimeService() {
        terminator.complete(SCOPE_EXECUTION_ID);

        verify(runtimeService).completeAdHocSubprocess(SCOPE_EXECUTION_ID);
    }
}
