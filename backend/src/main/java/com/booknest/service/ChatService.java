package com.booknest.service;

import com.booknest.ai.LlmGateway;
import com.booknest.ai.TfIdfIndex;
import com.booknest.ai.TfIdfIndex.Scored;
import com.booknest.dto.Dtos.*;
import com.booknest.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BookBot, the chatbot.
 *
 * Two modes, same endpoint:
 * - "llm"     : used when an API key is configured (Gemini by default, or Claude). We first RETRIEVE
 *                the best-matching books with the TF-IDF engine, then ask the LLM (Large Language Model)
 *                to write the answer using ONLY those books as context. This pattern is called
 *                retrieval-augmented generation, or RAG. The LLM does the understanding and the writing.
 * - "local" : the offline fallback, used when there is no key or the LLM call fails. It reads the
 *                message, works out the intent (greeting, "books like X", "books by Y", a genre,
 *                "top rated", "for me" ...) with plain rules and answers from the catalog.
 *                It is NOT an LLM, it only keeps the app usable without internet or a key.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Words people use for a genre -> the genre name in the catalog. */
    private static final Map<String, String> GENRE_ALIASES = Map.ofEntries(
            Map.entry("sci fi", "Science Fiction"), Map.entry("scifi", "Science Fiction"), Map.entry("sf", "Science Fiction"),
            Map.entry("mystery", "Mystery & Thriller"), Map.entry("thriller", "Mystery & Thriller"),
            Map.entry("detective", "Mystery & Thriller"), Map.entry("crime", "Mystery & Thriller"),
            Map.entry("romantic", "Romance"), Map.entry("love story", "Romance"),
            Map.entry("classic", "Classics"), Map.entry("selfhelp", "Self-Help"), Map.entry("self help", "Self-Help"),
            Map.entry("productivity", "Self-Help"), Map.entry("motivation", "Self-Help"),
            Map.entry("tech", "Technology"), Map.entry("programming", "Technology"), Map.entry("coding", "Technology"),
            Map.entry("software", "Technology"), Map.entry("biographies", "Biography"), Map.entry("memoir", "Biography"),
            Map.entry("autobiography", "Biography"), Map.entry("historical", "History"),
            Map.entry("philosophical", "Philosophy"), Map.entry("stoic", "Philosophy"));

    private record Local(String reply, List<BookDto> books) {}

    private final BookService bookService;
    private final RecommendationService recommendations;
    private final TfIdfIndex index;
    private final LlmGateway llm;

    public ChatService(BookService bookService, RecommendationService recommendations, TfIdfIndex index, LlmGateway llm) {
        this.bookService = bookService;
        this.recommendations = recommendations;
        this.index = index;
        this.llm = llm;
    }

    public ChatStatus status() {
        return new ChatStatus(llm.isEnabled() ? "llm" : "local", llm.label(), llm.lastError());
    }

    /** Sends one tiny request so you can see the real answer or the real error from the provider. */
    public Map<String, Object> selfTest() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("llmConfigured", llm.isEnabled());
        if (!llm.isEnabled()) {
            out.put("ok", false);
            out.put("error", "No API key found. Set GEMINI_API_KEY (or app.llm.api-key) and restart the backend.");
            return out;
        }
        long start = System.currentTimeMillis();
        try {
            String reply = llm.ask("You are a connectivity test.", List.of(Map.of("role", "user", "content", "Reply with the single word OK.")), Duration.ofSeconds(30));
            out.put("ok", true);
            out.put("model", llm.label());
            out.put("reply", reply);
        } catch (Exception e) {
            out.put("ok", false);
            out.put("model", llm.label());
            out.put("error", e.getMessage());
        }
        out.put("millis", System.currentTimeMillis() - start);
        return out;
    }
    public ChatResponse chat(ChatRequest request, User user) {
        String message = request.message().trim();
        List<BookDto> catalog = bookService.list(null, null, null, user);
        Local local = answerLocally(message, user, catalog);

        if (llm.isEnabled()) {
            try {
                return askLlm(request, message, user, catalog, local);
            } catch (Exception e) {
                log.warn("LLM call failed, using the offline answer instead: {}", e.getMessage());
            }
        }
        return new ChatResponse(local.reply(), local.books(), "local");
    }

    // =================================================================== LLM (retrieval + generation)

    private ChatResponse askLlm(ChatRequest request, String message, User user, List<BookDto> catalog, Local local) throws Exception {
        Map<Long, BookDto> byId = catalog.stream().collect(Collectors.toMap(BookDto::id, Function.identity()));

        // Retrieve: the books most relevant to this question become Claude's only source of truth.
        Map<Long, BookDto> context = new LinkedHashMap<>();
        local.books().forEach(b -> context.put(b.id(), b));
        for (Scored s : index.search(message, 6)) context.putIfAbsent(s.bookId(), byId.get(s.bookId()));
        if (user != null) recommendations.forUser(user, 4).forEach(r -> context.putIfAbsent(r.book().id(), r.book()));
        if (context.size() < 4) bookService.topRated(null, 4, user, Set.of()).forEach(b -> context.putIfAbsent(b.id(), b));
        context.values().removeIf(Objects::isNull);

        String catalogText = context.values().stream().limit(12)
                .map(b -> String.format("- \"%s\" by %s (%s, %s). Readers rate it %s/5 (%d ratings). %s",
                        b.title(), b.author(), b.genre(), b.year() == null ? "n/a" : b.year().toString(),
                        b.ratingCount() == 0 ? "-" : String.valueOf(b.avgRating()), b.ratingCount(), b.description()))
                .collect(Collectors.joining("\n"));

        String system = "You are BookBot, the friendly assistant of BookNest, a small online library. "
                + "Answer in 2 to 4 short sentences of plain text (no markdown, no lists). "
                + "Only recommend books that appear in the CATALOG below, and always write their titles exactly as given. "
                + "If nothing in the catalog fits, say so honestly and suggest what the user could ask instead.\n\nCATALOG:\n" + catalogText;
        String text = llm.ask(system, buildMessages(request.history(), message), Duration.ofSeconds(30));
        String lower = text.toLowerCase(Locale.ROOT);
        List<BookDto> mentioned = context.values().stream()
                .filter(b -> lower.contains(b.title().toLowerCase(Locale.ROOT)))
                .limit(5).toList();
        return new ChatResponse(text, mentioned, "llm");
    }

    /** Last few turns of history + the new message, merged so roles strictly alternate user/assistant. */
    private List<Map<String, String>> buildMessages(List<ChatTurn> history, String message) {
        List<Map<String, String>> messages = new ArrayList<>();
        List<ChatTurn> turns = history == null ? List.of() : history;
        turns = turns.subList(Math.max(0, turns.size() - 6), turns.size());
        List<ChatTurn> all = new ArrayList<>(turns);
        all.add(new ChatTurn("user", message));
        for (ChatTurn t : all) {
            String role = "assistant".equals(t.role()) ? "assistant" : "user";
            String content = t.content() == null ? "" : t.content().trim();
            if (content.isEmpty()) continue;
            if (messages.isEmpty() && role.equals("assistant")) continue; // must start with a user turn
            if (!messages.isEmpty() && messages.get(messages.size() - 1).get("role").equals(role)) {
                Map<String, String> last = messages.get(messages.size() - 1);
                last.put("content", last.get("content") + "\n" + content);
            } else {
                messages.add(new HashMap<>(Map.of("role", role, "content", content)));
            }
        }
        return messages;
    }

    // =================================================================== local engine

    private Local answerLocally(String original, User user, List<BookDto> catalog) {
        String m = normalise(original);
        Map<Long, BookDto> byId = catalog.stream().collect(Collectors.toMap(BookDto::id, Function.identity()));

        // ---- small talk
        if (m.matches("(hi+|hello+|hey+|namaste|yo|good (morning|afternoon| evening))( there| bookbot| bot)?")) {
            return new Local("Hi" + (user == null ? "" : " " + user.getName().split(" ")[0])
                    + "! I'm BookBot. Ask me for a genre, an author, or a book you enjoyed, for example \"something like Dune\".", List.of());
        }
        if (has(m, "help|what can you do|how do you work|who are you")) {
            return new Local("I can find books by genre (\"a history book\"), by author (\"books by Orwell\"), "
                    + "by theme (\"a story with dragons\"), similar to a book you liked (\"like The Hobbit\"), "
                    + "or show the top rated ones. Log in and ask \"recommend something for me\" for picks based on your taste.", List.of());
        }
        if (has(m, "thanks|thank you|thx")) return new Local("Happy to help! Ask me anything else about books.", List.of());
        if (has(m, "bye|goodbye|see you")) return new Local("Bye! Happy reading.", List.of());

        // ---- a specific book is mentioned
        BookDto mentioned = findMentionedTitle(m, catalog);
        if (mentioned != null) {
            List<RecommendationDto> similar = recommendations.similarBooks(mentioned.id(), 4, user);
            if (has(m, "similar|like|alike|more")) {
                List<BookDto> picks = similar.stream().map(RecommendationDto::book).toList();
                return new Local("If you enjoyed " + mentioned.title() + ", try these:", picks);
            }
            List<BookDto> picks = new ArrayList<>();
            picks.add(mentioned);
            similar.stream().limit(3).forEach(r -> picks.add(r.book()));
            String rating = mentioned.ratingCount() > 0 ? " Readers rate it " + mentioned.avgRating() + "/5." : "";
            return new Local(mentioned.title() + " by " + mentioned.author() + " (" + mentioned.genre() + "). "
                     + firstSentence(mentioned.description()) + rating + " Similar books are listed after it.", picks);
        }

        // ---- an author is mentioned
        List<BookDto> byAuthor = findByAuthor(m, catalog);
        if (!byAuthor.isEmpty()) {
            return new Local("Here is what we have by " + byAuthor.get(0).author() + ":", byAuthor.stream().limit(5).toList());
        }

        String genre = detectGenre(m);

        // ---- "I don't like fantasy": the genre is named, but the reader wants to avoid it
        if (genre != null && has(m, "dont|do not|not|no|never|hate|hated| dislike|avoid|except|without|sick of|tired of|bored of|boring")) {
            String avoided = genre;
            List<BookDto> others = bookService.topRated(null, 40, user, Set.of()).stream()
                    .filter(b -> ! b.genre().equalsIgnoreCase(avoided)).limit(4).toList();
            return new Local("Okay, no " + avoided + ". Here are top rated books from other genres:", others);
        }

        // ---- surprise me
        if (has(m, "surprise|random")) {
            BookDto pick = catalog.get(new Random().nextInt(catalog.size()));
            return new Local("How about " + pick.title() + " by " + pick.author() + "?", List.of(pick));
        }

        // ---- personalised
        if (genre == null && has(m, "for me|my taste|my favou?rites?|based on my|what should i read|recommend me|suggest me|something new|next read")) {
            if (user == null) {
                return new Local("Log in and I can tailor suggestions to your ratings and favourites. For now, these are the top rated:",
                        bookService.topRated(null, 4, null, Set.of()));
            }
            List<BookDto> picks = recommendations.forUser(user, 4).stream().map(RecommendationDto::book).toList();
            return new Local("Based on your favourites and ratings, you might like these:", picks);
        }

        // ---- top rated
        if (has(m, "top|best|highest|popular|most loved|famous|well rated")) {
             List<BookDto> picks = bookService.topRated(genre, 4, user, Set.of());
            if (!picks.isEmpty()) {
                return new Local("Top rated" + (genre == null ? "" : " in " + genre) + " by our readers:", picks);
            }
        }

        // ---- free-text search (TF-IDF)
        List<BookDto> found = new ArrayList<>();
        for (Scored s : index.search(original, 15)) {
            BookDto dto = byId.get(s.bookId());
            if (dto == null || s.score() < 0.05) continue;
            if (genre != null && !dto.genre().equalsIgnoreCase(genre)) continue;
            found.add(dto);
            if (found.size() == 4) break;
        }
        if (!found.isEmpty()) {
            return new Local(genre != null ? "Here are some " + genre + " books that fit:" : "These look closest to what you described:", found);
        }
        if (genre != null) {
            return new Local("Here are the top rated " + genre + " books:", bookService.topRated(genre, 4, user, Set.of()));
        }
        return new Local("I couldn't find a close match for that. Try a genre (fantasy, history, technology), an author, "
                + "or a book you enjoyed, for example \"something like Atomic Habits\".", List.of());
    }

    // ------------------------------------------------------------------ helpers

    private static boolean has(String text, String alternatives) {
        return Pattern.compile("\\b(" + alternatives + ")\\ b").matcher(text).find();
    }

    /** Lower-case, drop apostrophes, turn punctuation into spaces. "Ender's Game!" -> "enders game" */
    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("['’`]", "").replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    /** Finds a catalog title inside the message (longest match wins). */
    private BookDto findMentionedTitle(String m, List<BookDto> catalog) {
        String padded = " " + m + " ";
        BookDto best = null;
        int bestLength = 0;
        for (BookDto b : catalog) {
            String full = normalise(b.title());
            Set<String> keys = new LinkedHashSet<>();
            keys.add(full);
            keys.add(full.replaceFirst("^(the|a|an) ", ""));
            int and = full.indexOf(" and ");
            if (and > 0) keys.add(full.substring(0, and));
            String rawLower = b.title().toLowerCase(Locale.ROOT);
            int colon = rawLower.indexOf(':');
            if (colon > 0) keys.add(normalise(rawLower.substring(0, colon)).replaceFirst("^(the|a|an) ", ""));
            for (String key : keys) {
                if (key.length() >= 4 && padded.contains(" " + key + " ") && key.length() > bestLength) {
                     best = b;
                     bestLength = key.length();
                }
            }
        }
        return best;
    }

    private List<BookDto> findByAuthor(String m, List<BookDto> catalog) {
        boolean authorWords = has(m, "by|author|written|writer|books from");
        String padded = " " + m + " ";
        List<BookDto> out = new ArrayList<>();
        for (BookDto b : catalog) {
            String full = normalise(b.author());
            String[] parts = full.split(" ");
            String last = parts[parts.length - 1];
            boolean fullMatch = padded.contains(" " + full + " ");
            boolean lastMatch = authorWords && last.length() >= 4 && padded.contains(" " + last + " ");
            if (fullMatch || lastMatch) out.add(b);
        }
        return out;
    }

    private String detectGenre(String m) {
        String padded = " " + m + " ";
        for (String g : bookService.genres()) {
            if (padded.contains(" " + normalise(g) + " ")) return g;
        }
        Set<String> genres = new HashSet<>(bookService.genres());
        for (Map.Entry<String, String> e : GENRE_ALIASES.entrySet()) {
            if (genres.contains(e.getValue()) && padded.contains(" " + e.getKey() + " ")) return e.getValue();
        }
        for (String g : genres) {    // "fantasy" / "romance" / "classics" typed in singular or plural
            String n = normalise(g);
            if (n.endsWith("s") && padded.contains(" " + n.substring(0, n.length() - 1) + " ")) return g;
        }
        return null;
    }

     private static String firstSentence(String text) {
         if (text == null || text.isBlank()) return "";
         int dot = text.indexOf(". ");
         return dot > 0 ? text.substring(0, dot + 1) : text;
     }
}
