package com.booknest.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Anything that can answer a prompt with an LLM (Large Language Model).
 * BookNest has two implementations: {@link OpenAiCompatibleClient} (Gemini, Groq, Ollama ...)
 * and {@link ClaudeClient}. The rest of the app only talks to {@link LlmGateway}.
 */
public interface LlmClient {

     /** True when an API key is configured. */
     boolean isEnabled();

    /** Human-readable name shown in the chat header, e.g. "Gemini (gemini-3.8-flash)". */
    String label();

    /**
     * @param system   instructions for the model
     * @param messages alternating {"role": "user"|"assistant", "content": "..."} maps, starting with "user"
     * @param timeout give up after this long
     * @return the model's text reply
     */
    String ask(String system, List<Map<String, String>> messages, Duration timeout) throws IOException, InterruptedException;
}
