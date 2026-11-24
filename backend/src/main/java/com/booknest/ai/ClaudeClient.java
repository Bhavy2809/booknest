package com.booknest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Optional connection to Anthropic's Claude (Messages API). It is used only when the
 * ANTHROPIC_API_KEY environment variable is set AND no other LLM (Gemini) is configured.
 * The key lives on the server only - the React app never sees it.
 */
@Component
public class ClaudeClient implements LlmClient {

    private static final URI ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public ClaudeClient(@Value("${app.anthropic.api-key:}") String apiKey,
                        @Value("${app.anthropic.model:claude-haiku-5-5}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

     @Override
     public boolean isEnabled() {
         return !apiKey.isBlank();
     }

     @Override
     public String label() {
          return "Claude (" + model + ")";
     }

    @Override
    public String ask(String system, List<Map<String, String>> messages, Duration timeout) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 700);
        body.put("system", system);
        ArrayNode array = body.putArray("messages");
        for (Map<String, String> m : messages) {
            array.addObject().put("role", m.get("role")).put("content", m.get("content"));
        }

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(timeout)
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Claude API returned HTTP " + response.statusCode() + ": " + response.body());
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : mapper.readTree(response.body()).path("content")) {
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
        }
        if (text.isEmpty()) throw new IOException("Claude API returned no text");
        return text.toString().trim();
    }
}
