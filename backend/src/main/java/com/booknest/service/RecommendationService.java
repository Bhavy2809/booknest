package com.booknest.service;

import com.booknest.ai.TfIdfIndex;
import com.booknest.ai.TfIdfIndex.Scored;
import com.booknest.dto.Dtos.BookDto;
import com.booknest.dto.Dtos.RecommendationDto;
import com.booknest.model.Book;
import com.booknest.model.Favorite;
import com.booknest.model.Rating;
import com.booknest.model.User;
import com.booknest.repository.BookRepository;
import com.booknest.repository.FavoriteRepository;
import com.booknest.repository.RatingRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Content-based recommender.
 *
 * 1. "Similar books" : cosine similarity between one book's TF-IDF vector and all others.
 * 2. "For you"         : build a taste profile = weighted sum of the vectors of books the user
 *                         favourited / rated highly, then rank every unseen book against that profile.
 *
 * Every suggestion carries a plain-English reason, so the user can see why it was picked.
 */
@Service
public class RecommendationService {

    private final TfIdfIndex index;
    private final BookRepository books;
    private final RatingRepository ratings;
    private final FavoriteRepository favorites;
    private final BookService bookService;
    private final ReasonWriter reasonWriter;

    public RecommendationService(TfIdfIndex index, BookRepository books, RatingRepository ratings,
                                 FavoriteRepository favorites, BookService bookService, ReasonWriter reasonWriter) {
        this.index = index;
        this.books = books;
        this.ratings = ratings;
        this.favorites = favorites;
        this.bookService = bookService;
        this.reasonWriter = reasonWriter;
    }

    // ------------------------------------------------------------------ similar books

    public List<RecommendationDto> similarBooks(Long bookId, int limit, User user) {
        Book source = bookService.getEntity(bookId);
        Map<String, Double> sourceVec = index.vectorOf(bookId);
        List<Scored> scored = index.similarTo(bookId, limit);

        Map<Long, BookDto> dtos = dtoMap(scored.stream().map(Scored::bookId).toList(), user);
        List<RecommendationDto> out = new ArrayList<>();
        for (Scored s : scored) {
            BookDto dto = dtos.get(s.bookId());
            if (dto == null) continue;
            List<String> shared = index.sharedTerms(sourceVec, index.vectorOf(s.bookId()), 3, genreWords(source.getGenre(), dto.genre()));
            String reason = (dto.genre().equals(source.getGenre()) ? "Also " + dto.genre() + ". " : "")
                    + (shared.isEmpty() ? "" : "Shared themes: " + String.join(", ", shared));
            out.add(new RecommendationDto(dto, round(s.score()), reason.trim(), "rules"));
        }

        // The engine chose the books. If an LLM is configured, it now words the explanations.
        String context = "The reader is looking at \"" + source.getTitle() + "\" by " + source.getAuthor()
                + " (" + source.getGenre() + "): " + shorten(source.getDescription(), 220);
        String key = "similar:" + bookId + ":" + out.stream().map(r -> String.valueOf(r.book().id())).collect(Collectors.joining(","));
        return reasonWriter.enrich(key, context, out);
    }

    // ------------------------------------------------------------------ personalised

    public List<RecommendationDto> forUser(User user, int limit) {
        // 1. Collect what the user told us. Positive weight = liked, negative = disliked.
        Map<Long, Double> weights = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (Favorite f : favorites.findByUserId(user.getId())) {
            weights.merge(f.getBook().getId(), 2.0, Double::sum);
            seen.add(f.getBook().getId());
        }
        for (Rating r : ratings.findByUserId(user.getId())) {
            double w = r.getStars() >= 4 ? r.getStars() - 2 : (r.getStars() == 3 ? 0.5 : -1.0);
            weights.merge(r.getBook().getId(), w, Double::sum);
            seen.add(r.getBook().getId());
        }

        // 2. Taste profile = weighted sum of the vectors of those books.
        Map<String, Double> profile = new HashMap<>();
        weights.forEach((bookId, w) -> index.vectorOf(bookId).forEach((term, v) -> profile.merge(term, w * v, Double::sum)));
        double norm = Math.sqrt(profile.values().stream().mapToDouble(v -> v * v).sum());
        boolean hasProfile = norm > 0 && weights.values().stream().anyMatch(w -> w > 0);

        // Cold start: nothing known about this user yet, so fall back to what other readers rate highly.
        if (!hasProfile) {
            return bookService.topRated(null, limit, user, seen).stream()
                    .map(b -> new RecommendationDto(b, 0, "Popular with readers. Rate or save a few books to personalise this list.", "rules"))
                    .toList();
        }
        profile.replaceAll((k, v) -> v / norm);

        // 3. Rank unseen books by similarity to the profile, with a small nudge for popular books.
        List<Scored> ranked = index.rank(profile, seen, 40);
        Map<Long, BookDto> dtos = dtoMap(ranked.stream().map(Scored::bookId).toList(), user);
        List<Long> likedIds = weights.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).toList();
        Map<Long, Book> likedBooks = books.findAllById(likedIds).stream().collect(Collectors.toMap(Book::getId, Function.identity()));

         List<RecommendationDto> out = new ArrayList<>();
         for (Scored s : ranked) {
             BookDto dto = dtos.get(s.bookId());
             if (dto == null) continue;
             double popularity = dto.ratingCount() > 0 ? dto.avgRating() / 5.0 : 0.6;
             double finalScore = 0.85 * s.score() + 0.15 * popularity;

             // Which liked book is this closest to? That becomes the explanation.
            Map<String, Double> candidateVec = index.vectorOf(s.bookId());
            Long bestSource = likedIds.stream()
                    .max(Comparator.comparingDouble(id -> TfIdfIndex.cosine(index.vectorOf(id), candidateVec)))
                    .orElse(null);
            String reason = "Matches your taste.";
            if (bestSource != null && likedBooks.containsKey(bestSource)) {
                Book liked = likedBooks.get(bestSource);
                List<String> shared = index.sharedTerms(index.vectorOf(bestSource), candidateVec, 3, genreWords(liked.getGenre(), dto.genre()));
                reason = "Because you liked " + liked.getTitle()
                        + (shared.isEmpty() ? " (also " + dto.genre() + ")" : " - shared themes: " + String.join(", ", shared));
            }
            out.add(new RecommendationDto(dto, round(finalScore), reason, "rules"));
        }

out.sort(Comparator.comparingDouble(RecommendationDto::score).reversed());
        List<RecommendationDto> top = out.stream().limit(limit).toList();

        // The engine chose the books. If an LLM is configured, it now words the explanations.
        String likedText = likedIds.stream().limit(6)
                .map(likedBooks::get).filter(Objects::nonNull)
                .map(b -> b.getTitle() + " (" + b.getGenre() + ")")
                .collect(Collectors.joining("; "));
        String key = "user:" + user.getId()
                + ":" + likedIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(", "))
                + ":" + top.stream().map(r -> String.valueOf(r.book().id())).collect(Collectors.joining(","));
        return reasonWriter.enrich(key, "This reader liked: " + likedText + ".", top);
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, BookDto> dtoMap(List<Long> ids, User user) {
        return bookService.toDtos(books.findAllById(ids), user).stream()
                .collect(Collectors.toMap(BookDto::id, Function.identity()));
    }

    /** Genre names as index tokens, so "science fiction" is not reported as a "shared theme". */
    private static Set<String> genreWords(String... genres) {
        Set<String> words = new HashSet<>();
        for (String g : genres) words.addAll(TfIdfIndex.tokenize(g));
        return words;
    }

    private static String shorten(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
