package com.booknest.ai;

import com.booknest.model.Book;
import com.booknest.repository.BookRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A tiny search/recommendation engine built from scratch (no external AI library).
 *
 * Idea: turn every book into a list of numbers (a "vector") describing which words matter in it.
 * Two books are "similar" when their vectors point in nearly the same direction.
 *
 *   TF (term frequency)           = how often a word appears in this book's text       -> 1 + ln(count)
 *   IDF (inverse document freq.) = how rare the word is across all books -> ln((N+1)/(df+1)) + 1
 *   weight of a word              = TF x IDF   ("dragon" matters more than "story")
 *   similarity of two books       = cosine of the angle between their vectors (0 = nothing in common, 1 = identical)
 *
 * The index is built lazily on first use and rebuilt after an admin adds, edits or deletes a book.
 */
@Component
public class TfIdfIndex {

    /** A book id with its similarity score. */
    public record Scored(Long bookId, double score) {}

    private record Snapshot(Map<Long, Map<String, Double>> vectors, Map<String, Double> idf) {}

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "his", "her", "him", "she", "they", "their", "them",
            "are", "was", "were", "has", "have", "had", "who", "whom", "what", "which", "when", "where", "why", "how",
            "into", "onto", "out", "over", "under", "about", "than", "then", "but", "not", "all", "any", "one", "two",
            "its", "our", "your", "you", "can", "will", "would", "could", "should", "also", "more", "most", "some",
            "such", "through", "between", "each", "other", "while", "after", "before", "only", "own", "same", "very",
            // words that appear in nearly every question people ask a book bot
            "book", "books", "read", "reading", "novel", "recommend", "suggest", "want", "like", "give", "show",
            "find", "looking", "something", "good", "great", "best", "please", "tell", "need", "stuff");

    private final BookRepository books;
    private volatile Snapshot snapshot;
    private final AtomicLong version = new AtomicLong();

    public TfIdfIndex(BookRepository books) {
        this.books = books;
    }

    /** Call after the catalog changes so the next request rebuilds the index. */
    public void invalidate() {
        snapshot = null;
        version.incrementAndGet();
    }

    /** Changes every time the catalog changes. Used to throw away cached AI sentences that may be out of date. */
    public long version() {
        return version.get();
    }

    // ------------------------------------------------------------------ public API

    /** Books most similar to the given book (excluding itself). */
    public List<Scored> similarTo(Long bookId, int limit) {
        Map<String, Double> target = snap().vectors().get(bookId);
        if (target == null) return List.of();
        return snap().vectors().entrySet().stream()
                .filter(e -> !e.getKey().equals(bookId))
                .map(e -> new Scored(e.getKey(), cosine(target, e.getValue())))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
    }

    /** Free-text search: ranks books by how well they match the words of the query. */
    public List<Scored> search(String query, int limit) {
        Map<String, Double> q = vectorizeQuery(query);
        if (q.isEmpty()) return List.of();
        return rank(q, Set.of(), limit);
    }

    /** Ranks all books (except the excluded ones) against any vector, e.g. a user's taste profile. */
    public List<Scored> rank(Map<String, Double> vector, Set<Long> exclude, int limit) {
        return snap().vectors().entrySet().stream()
                .filter(e -> !exclude.contains(e.getKey()))
                .map(e -> new Scored(e.getKey(), cosine(vector, e.getValue())))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(limit)
                .toList();
    }

    public Map<String, Double> vectorOf(Long bookId) {
        return snap().vectors().getOrDefault(bookId, Map.of());
    }

    /** Turns a sentence into a normalised TF-IDF vector. Words the catalog has never seen are ignored. */
    public Map<String, Double> vectorizeQuery(String text) {
        Map<String, Integer> counts = new HashMap<>();
        for (String t : tokenize(text)) counts.merge(t, 1, Integer::sum);
        Map<String, Double> idf = snap().idf();
        Map<String, Double> vec = new HashMap<>();
        counts.forEach((term, c) -> {
            Double w = idf.get(term);
            if (w != null) vec.put(term, (1 + Math.log(c)) * w);
        });
        return normalise(vec);
    }

    /**
      * The words that two vectors share most strongly - used to explain a recommendation.
      * Words in {@code ignore} (for example the genre name) are skipped because they say nothing new.
      */
    public List<String> sharedTerms(Map<String, Double> a, Map<String, Double> b, int limit, Set<String> ignore) {
         return a.entrySet().stream()
                 .filter(e -> b.containsKey(e.getKey()) && ! ignore.contains(e.getKey()))
                 .sorted((x, y) -> Double.compare(y.getValue() * b.get(y.getKey()), x.getValue() * b.get(x.getKey())))
                 .map(Map.Entry::getKey)
                 .limit(limit)
                 .collect(Collectors.toList());
    }

    /** Dot product of two unit vectors = cosine similarity. */
    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) dot += e.getValue() * other;
        }
        return dot;
    }

    // ------------------------------------------------------------------ building the index

    private Snapshot snap() {
        Snapshot s = snapshot;
        if (s == null) {
            synchronized (this) {
                s = snapshot;
                if (s == null) {
                    s = build();
                    snapshot = s;
                }
            }
        }
        return s;
    }
    private Snapshot build() {
        List<Book> all = books.findAll();
        Map<Long, Map<String, Integer>> termCounts = new HashMap<>();
        Map<String, Integer> docFreq = new HashMap<>();

        for (Book b : all) {
            Map<String, Integer> counts = new HashMap<>();
            for (String t : tokenize(documentText(b))) counts.merge(t, 1, Integer::sum);
            termCounts.put(b.getId(), counts);
            for (String term : counts.keySet()) docFreq.merge(term, 1, Integer::sum);
        }

        int n = all.size();
        Map<String, Double> idf = new HashMap<>();
        docFreq.forEach((term, df) -> idf.put(term, Math.log((n + 1.0) / (df + 1.0)) + 1.0));

        Map<Long, Map<String, Double>> vectors = new HashMap<>();
        termCounts.forEach((id, counts) -> {
            Map<String, Double> vec = new HashMap<>();
            counts.forEach((term, c) -> vec.put(term, (1 + Math.log(c)) * idf.get(term)));
            vectors.put(id, normalise(vec));
        });
        return new Snapshot(vectors, idf);
    }

    /** Genre is repeated 3x and title 2x so they count more than a single word in the description. */
    private static String documentText(Book b) {
        return (b.getTitle() + " ").repeat(2)
                + b.getAuthor() + " "
                + (b.getGenre() + " ").repeat(3)
                + (b.getDescription() == null ? "" : b.getDescription());
    }

    // ------------------------------------------------------------------ text helpers

    public static List<String> tokenize(String text) {
        if (text == null) return List.of();
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
        if (cleaned.isEmpty()) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String word : cleaned.split("\\s+")) {
            if (word.length() < 3 || STOP_WORDS.contains(word)) continue;
            tokens.add(stem(word));
        }
        return tokens;
    }

    /** Very light stemming so "dragons" matches "dragon" and "stories" matches "story". */
    private static String stem(String w) {
        int n = w.length();
        if (w.endsWith("ies") && n > 4) return w.substring(0, n - 3) + "y";
        if (w.endsWith("ing") && n > 5) return w.substring(0, n - 3);
        if (w.endsWith("ed") && n > 4) return w.substring(0, n - 2);
        if (w.endsWith("s") && !w.endsWith("ss") && n > 3) return w.substring(0, n - 1);
        return w;
    }

    private static Map<String, Double> normalise(Map<String, Double> vec) {
        double norm = Math.sqrt(vec.values().stream().mapToDouble(v -> v * v).sum());
        if (norm == 0) return vec;
        vec.replaceAll((k, v) -> v / norm);
        return vec;
    }
}
