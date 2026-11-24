package com.booknest.ai;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The single door to "the LLM". It picks whichever provider has a key:
 * Gemini (or any OpenAI-compatible service) first, then Claude. With no key at all,
 * {@link #isEnabled()} is false and the app uses its offline engine instead.
 */
@Component
public class LlmGateway {

    private final LlmClient active;    // null when no key is configured
    private volatile String lastError;    // why the most recent call failed, or null if it worked

    public LlmGateway(OpenAiCompatibleClient openAiCompatible, ClaudeClient claude) {
        this.active = openAiCompatible.isEnabled() ? openAiCompatible : claude.isEnabled() ? claude : null;
    }

    public boolean isEnabled() {
        return active != null;
    }

    public String label() {
        return active == null ? "Offline engine" : active.label();
    }

    public String ask(String system, List<Map<String, String>> messages, Duration timeout) throws IOException, InterruptedException {
        if (active == null) throw new IOException("No LLM configured");
        try {
            String text = active.ask(system, messages, timeout);
            lastError = null;
            return text;
        } catch (IOException | RuntimeException e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        }
    }

    /** The reason the most recent call failed (shown in the chat header), or null if it worked. */
    public String lastError() {
        return lastError;
    }
}
