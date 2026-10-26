package com.wontake.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Configuration
public class AiConfiguration {
    @Bean AiClient aiClient(Environment env) {
        String provider = env.getProperty("app.ai.provider", "auto").toLowerCase();
        String key = env.getProperty("app.ai.api-key", "").trim();
        if (!Set.of("auto", "fake", "claude").contains(provider)) throw new IllegalStateException("AI_PROVIDER는 auto, fake, claude 중 하나여야 합니다.");
        if (provider.equals("fake") || provider.equals("auto") && key.isEmpty()) return new FakeAiClient();
        if (key.isEmpty()) throw new IllegalStateException("Claude 모드에는 ANTHROPIC_API_KEY가 필요합니다.");
        return new ClaudeAiClient(URI.create("https://api.anthropic.com/v1/messages"), key,
            env.getProperty("app.ai.model"), env.getProperty("app.ai.workspace", ""),
            env.getProperty("app.ai.version"), Duration.ofSeconds(45),
            Integer.parseInt(env.getProperty("app.ai.max-calls", "500").trim()),
            !"off".equalsIgnoreCase(env.getProperty("app.ai.fallbacks", "default").trim()));
    }
}
