package com.booknest.service;

import com.booknest.ai.LlmGateway;
import com.booknest.ai.TfIdfIndex;
import com.booknest.dto.Dtos.BookDto;
import com.booknest.dto.Dtos.RecommendationDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Turns the recommender's rule-based reasons ("Shared themes: magic, dragon") into one natural
 * sentence per book, written by the LLM.
 *
 * How it fits together: the TF-IDF engine decides WHICH books to recommend (fast, free, explainable).
 * The LLM only explains WHY, using the facts we give it. One request covers the whole list.
 *
 * Safety nets, because an external service can be slow or rate limited (free tiers have limits):
 * - results are cached, so reloading a page does not call the LLM again;
 * - any failure keeps the rule-based reason, so the page always works;
 * - after a failure the LLM is skipped for 60 seconds instead of making every page wait.
 */
@Service
public class ReasonWriter {

    private static final Logger log = LoggerFactory.getLogger(ReasonWriter.class);
    private static final long PAUSE_AFTER_FAILURE_MS = 60_000;

    private static final String SYSTEM_PROMPT =
            "You write short explanations for a library app's book recommendations. "
                    + "For every candidate book, write ONE friendly sentence (at most 25 words) saying why this reader would enjoy it. "
                    + "Mention one of the books the reader liked or is looking at when it fits. "
                    + "Use only the facts given and do not invent plot details. "
                    + "Reply with ONLY a JSON object whose keys are the book ids and whose values are the sentences, "
                    + "for example {\"12\": \"...\", \"7\": \"...\"}. No markdown, no extra text.";

    private final LlmGateway llm;
    private final TfIdfIndex index;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<Long, String>> cache = new ConcurrentHashMap<>();
    private volatile long pausedUntil = 0;

    public ReasonWriter(LlmGateway llm, TfIdfIndex index) {
        this.llm = llm;
        this.index = index;
    }

    /**
     * @param cacheKey       identifies this exact situation (which user or book, which candidates)
     * @param readerContext one sentence about what the reader liked or is viewing
     * @param items          the recommendations with rule-based reasons
     * @return the same recommendations, with AI-written reasons where the LLM answered
     */
    public List<RecommendationDto> enrich(String cacheKey, String readerContext, List<RecommendationDto> items) {
        if (items.isEmpty() || !llm.isEnabled() || System.currentTimeMillis() < pausedUntil) return items;

        String key = index.version() + "|" + cacheKey;       // a catalog change makes old sentences obsolete
        Map<Long, String> reasons = cache.get(key);
        if (reasons == null) {
            try {
                reasons = askLlm(readerContext, items);
                if (cache.size() > 500) cache.clear();
                cache.put(key, reasons);
            } catch (Exception e) {
                log.warn("Could not get AI-written reasons, keeping the rule-based ones for now: {}", e.getMessage());
                pausedUntil = System.currentTimeMillis() + PAUSE_AFTER_FAILURE_MS;
                return items;
            }
        }
        Map<Long, String> written = reasons;
        return items.stream()
                .map(i -> written.containsKey(i.book().id())
                        ? new RecommendationDto(i.book(), i.score(), written.get(i.book().id()), "ai")
                        : i)
                .toList();
    }

    private Map<Long, String> askLlm(String readerContext, List<RecommendationDto> items) throws IOException, InterruptedException {
        String candidates = items.stream()
                .map(i -> describe(i.book()))
                .collect(Collectors.joining("\n"));
        String prompt = readerContext + "\n\nCandidate books (id | title by author | genre | description):\n" + candidates;

        String raw = llm.ask(SYSTEM_PROMPT, List.of(Map.of("role", "user", "content", prompt)), Duration.ofSeconds(20));
        Map<Long, String> parsed = parse(raw, items);
        if (parsed.isEmpty()) throw new IOException("The reply had no usable sentences");
        return parsed;
    }

    /** Pulls the JSON object out of the reply (models sometimes wrap it in ```json fences) and keeps only valid ids. */
    static Map<Long, String> parse(String raw, List<RecommendationDto> items) throws IOException {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < start) throw new IOException("The reply was not JSON");
        JsonNode root = new ObjectMapper().readTree(raw.substring(start, end + 1));

        Set<Long> allowed = items.stream().map(i -> i.book().id()).collect(Collectors.toSet());
        Map<Long, String> out = new HashMap<>();
        root.fields().forEachRemaining(entry -> {
            try {
                long id = Long.parseLong(entry.getKey().trim());
                String sentence = entry.getValue().asText("").trim();
                if (allowed.contains(id) && !sentence.isEmpty() && sentence.length() <= 300) out.put(id, sentence);
            } catch (NumberFormatException ignored) {
                // a key that is not a book id: skip it
            }
        });
        return out;
    }

    private static String describe(BookDto b) {
        String text = b.description() == null ? "" : b.description();
        if (text.length() > 220) text = text.substring(0, 220) + "...";
        return b.id() + " | " + b.title() + " by " + b.author() + " | " + b.genre() + " | " + text;
    }
}
