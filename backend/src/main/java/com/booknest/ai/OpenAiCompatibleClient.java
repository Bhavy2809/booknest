package com.booknest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Talks to any service that offers the "OpenAI-style" chat completions API:
 * POST {base-url}/chat/completions with {"model": ..., "messages": [...]}.
 *
 * Many providers copied this format, so one small class covers several of them. The default is
 * Google Gemini (it has a free tier). To switch provider, change only the settings:
 *
 *    Gemini base-url = https://generativelanguage.googleapis.com/v1beta/openai    model = gemini-3.8-flash
 *    Groq   base-url = https://api.groq.com/openai/v1
 *    Ollama base-url = http://localhost:11434/v1   (runs on your own laptop, no key needed: use any text as the key)
 *
 * Free tiers differ per model (one model may have no free quota while another does), so if the main
 * model answers with "not found" or "quota exceeded", the next model in app.llm.fallback-models is tried
 * and the one that worked is remembered.
 *
 * The key stays on the server. The React app never sees it.
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private final String apiKey;
    private final String baseUrl;
    private final List<String> models;          // main model first, then the fallbacks
    private volatile String working;             // the model that last answered, tried first next time
    private final String provider;
    private final String reasoningEffort;      // "low" keeps Gemini's built-in thinking short; blank = do not send
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiCompatibleClient(String apiKey, String baseUrl, String model, String provider) {
        this(apiKey, baseUrl, model, "", provider);
    }

    public OpenAiCompatibleClient(String apiKey, String baseUrl, String model, String fallbackModels, String provider) {
        this(apiKey, baseUrl, model, fallbackModels, provider, "");
    }

    @Autowired
    public OpenAiCompatibleClient(@Value("${app.llm.api-key:}") String apiKey,

@Value("${app.llm.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
                                  @Value("${app.llm.model:gemini-3.8-flash}") String model,
                                  @Value("${app.llm.fallback-models:gemini-3.5-flash,gemini-2.5-flash}") String fallbackModels,
                                  @Value("${app.llm.provider:Gemini}") String provider,
                                  @Value("${app.llm.reasoning-effort:low}") String reasoningEffort) {
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.provider = provider;
        List<String> list = new ArrayList<>();
        list.add(model.trim());
        for (String m : fallbackModels.split(",")) {
            if (!m.isBlank() && !list.contains(m.trim())) list.add(m.trim());
        }
        this.models = List.copyOf(list);
        this.working = this.models.get(0);
    }

    /** An HTTP error from the provider, so the caller can tell "try another model" from "the key is wrong". */
    private static class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
        boolean worthTryingAnotherModel() { return status == -1 || status == 400 || status == 404 || status == 429 || status >= 500; }   // -1 = timed out
    }

    @Override
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    @Override
    public String label() {
        return provider + " (" + working + ")";
    }

    @Override
    public String ask(String system, List<Map<String, String>> messages, Duration timeout) throws IOException, InterruptedException {
        List<String> order = new ArrayList<>();
        order.add(working);
        for (String m : models) if (!order.contains(m)) order.add(m);

        List<String> problems = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        for (int i = 0; i < order.size(); i++) {
            String model = order.get(i);
            long remaining = deadline - System.nanoTime();
            if (remaining < 1_500_000_000L) { problems.add(model + ": no time left to try it"); break; }
            // A slow model must not eat the whole budget: it gets 60% of what is left, the last one gets everything.
            Duration attempt = i == order.size() - 1 ? Duration.ofNanos(remaining) : Duration.ofNanos((long) (remaining * 0.6));
            try {
                String text = call(model, system, messages, attempt);
                working = model;                       // remember what works
                return text;
            } catch (HttpTimeoutException e) {
                problems.add(model + ": no answer within " + attempt.toSeconds() + "s");
            } catch (ApiException e) {
                problems.add(model + ": " + e.getMessage());
                if (!e.worthTryingAnotherModel()) break;   // e.g. 401/403: the key itself is the problem
            }
        }
        throw new IOException(String.join(" | ", problems));
    }

    /** Sends the request; if the provider rejects the reasoning setting, repeats once without it. */
    private String call(String model, String system, List<Map<String, String>> messages, Duration timeout) throws IOException, InterruptedException {
        try {
            return send(model, system, messages, timeout, true);
        } catch (ApiException e) {
            String msg = e.getMessage().toLowerCase();
            boolean aboutReasoning = msg.contains("reasoning") || msg.contains("thinking") || msg.contains("effort");
            if (e.status == 400 && !reasoningEffort.isBlank() && aboutReasoning) {
                return send(model, system, messages, timeout, false);
            }
            throw e;
        }
    }

    private String send(String model, String system, List<Map<String, String>> messages, Duration timeout, boolean withReasoning) throws IOException, InterruptedException {
         ObjectNode body = mapper.createObjectNode();
         body.put("model", model);
         if (withReasoning && !reasoningEffort.isBlank()) body.put("reasoning_effort", reasoningEffort);
         ArrayNode array = body.putArray("messages");
         array.addObject().put("role", "system").put("content", system);      // the instructions go first
         for (Map<String, String> m : messages) {
             array.addObject().put("role", m.get("role")).put("content", m.get("content"));
         }

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
             throw new ApiException(response.statusCode(), "HTTP " + response.statusCode() + " " + shorten(response.body()));
        }
        JsonNode content = mapper.readTree(response.body()).path("choices").path(0).path("message").path ("content");
        String text = content.asText("").trim();
        if (text.isEmpty()) throw new IOException(model + " returned no text");
        return text;
    }

    private static String shorten(String s) {
        return s == null ? "" : (s.length() > 220 ? s.substring(0, 220) + "..." : s);
     }
}
