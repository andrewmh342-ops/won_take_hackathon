package com.wontake.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class AiConfigurationTest {
    @Test void automaticModeWithoutKeyIsExplicitlyFake() {
        assertEquals("FAKE",new AiConfiguration().aiClient(new MockEnvironment()).mode());
    }
    @Test void explicitClaudeWithoutKeyFailsAtStartup() {
        assertThrows(IllegalStateException.class,()->new AiConfiguration().aiClient(new MockEnvironment().withProperty("app.ai.provider","claude")));
    }
    @Test void explicitFakeDoesNotUsePresentKey() {
        assertEquals("FAKE",new AiConfiguration().aiClient(new MockEnvironment().withProperty("app.ai.provider","fake").withProperty("app.ai.api-key","test-key")).mode());
    }
}
