package com.booknest.service;

import com.booknest.ai.TfIdfIndex;
import com.booknest.dto.Dtos.BookDto;
import com.booknest.dto.Dtos.BookRequest;
import com.booknest.model.Book;
import com.booknest.model.Favorite;
import com.booknest.model.Rating;
import com.booknest.model.User;
import com.booknest.repository.BookRepository;
import com.booknest.repository.FavoriteRepository;
import com.booknest.repository.RatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Business logic for the book catalog: CRUD, ratings, favourites and the conversion to DTOs. */
@Service
public class BookService {

     private final BookRepository books;
     private final RatingRepository ratings;
     private final FavoriteRepository favorites;
     private final TfIdfIndex index;

    public BookService(BookRepository books, RatingRepository ratings, FavoriteRepository favorites, TfIdfIndex index) {
        this.books = books;
        this.ratings = ratings;
        this.favorites = favorites;
        this.index = index;
    }

    // ------------------------------------------------------------------ reading

     public List<BookDto> list(String q, String genre, String sort, User user) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Book> matches = books.findAll().stream()
                .filter(b -> genre == null || genre.isBlank() || b.getGenre().equalsIgnoreCase(genre))
                .filter(b -> needle.isEmpty()
                        || b.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getAuthor().toLowerCase(Locale.ROOT).contains(needle)
                        || b.getGenre().toLowerCase(Locale.ROOT).contains(needle)
                        || (b.getDescription() != null && b.getDescription().toLowerCase(Locale.ROOT).contains(needle)))
                .toList();

        List<BookDto> result = new ArrayList<>(toDtos(matches, user));
        Comparator<BookDto> byTitle = Comparator.comparing(BookDto::title, String.CASE_INSENSITIVE_ORDER);
        if ("rating".equals(sort)) {
            result.sort(Comparator.comparingDouble(BookDto::avgRating).reversed().thenComparing(byTitle));
        } else if ("newest".equals(sort)) {
            result.sort(Comparator.comparing((BookDto b) -> b.year() == null ? Integer.MIN_VALUE : b.year()).reversed().thenComparing(byTitle));
        } else {
            result.sort(byTitle);
        }
        return result;
    }

    public BookDto get(Long id, User user) {
        return toDtos(List.of(getEntity(id)), user).get(0);
    }

    public Book getEntity(Long id) {
        return books.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
    }

    public List<String> genres() {
        return books.findGenres();
    }

    /**
     * Highest-rated books, optionally limited to one genre and skipping some ids.
     *
     * A plain average is unfair: one 5-star vote would beat a book with fifty 4.8 votes.
     * So we rank by a "weighted rating" that pulls books with few votes towards the overall average:
     *     weighted = (votes x average + m x overallAverage) / (votes + m) with m = 2
     */
    public List<BookDto> topRated(String genre, int limit, User user, Set<Long> exclude) {
        List<BookDto> all = list(null, null, null, user);
        double overall = all.stream().filter(b -> b.ratingCount() > 0).mapToDouble(BookDto::avgRating).average().orElse(3.0);
        final double m = 2;
        Function<BookDto, Double> weighted = b -> (b.ratingCount() * b.avgRating() + m * overall) / (b.ratingCount() + m);
        return all.stream()
                .filter(b -> genre == null || genre.isBlank() || b.genre().equalsIgnoreCase(genre))
                .filter(b -> !exclude.contains(b.id()))
                .sorted(Comparator.comparing(weighted).reversed())
                .limit(limit)
                .toList();
    }

    // ------------------------------------------------------------------ writing (admin)

    @Transactional
    public BookDto create(BookRequest r) {
        Book b = new Book();
        apply(b, r);
        Book saved = books.save(b);
        index.invalidate();
        return toDtos(List.of(saved), null).get(0);
    }

    @Transactional
    public BookDto update(Long id, BookRequest r) {
        Book b = getEntity(id);
        apply(b, r);
        Book saved = books.save(b);
        index.invalidate();
        return toDtos(List.of(saved), null).get(0);
    }

    @Transactional
    public void delete(Long id) {
        getEntity(id);
        ratings.deleteByBookId(id);
        favorites.deleteByBookId(id);
        books.deleteById(id);
        index.invalidate();
    }

    private void apply(Book b, BookRequest r) {
        b.setTitle(r.title().trim());
        b.setAuthor(r.author().trim());
        b.setGenre(r.genre().trim());
        b.setPublishedYear(r.year());
        b.setDescription(r.description() == null ? "" : r.description().trim());
    }

    // ------------------------------------------------------------------ user actions

    @Transactional
    public BookDto rate(Long bookId, int stars, User user) {
        Book book = getEntity(bookId);
        Rating rating = ratings.findByUserIdAndBookId(user.getId(), bookId).orElseGet(() -> new Rating(user, book, stars));
        rating.setStars(stars);
        ratings.save(rating);
        return get(bookId, user);
    }

    @Transactional
    public BookDto setFavorite(Long bookId, boolean favorite, User user) {
        Book book = getEntity(bookId);
        boolean exists = favorites.findByUserIdAndBookId(user.getId(), bookId).isPresent();
        if (favorite && !exists) favorites.save(new Favorite(user, book));
        if (!favorite && exists) favorites.deleteByUserIdAndBookId(user.getId(), bookId);
        return get(bookId, user);
    }

    public List<BookDto> favoritesOf(User user) {
        List<Book> saved = favorites.findByUserId(user.getId()).stream().map(Favorite::getBook).toList() ;
        return toDtos(saved, user).stream()
                .sorted(Comparator.comparing(BookDto::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------ entity -> DTO

    /** Adds average rating, rating count and (if logged in) the user's own favourite / rating to each book. */
    public List<BookDto> toDtos(Collection<Book> items, User user) {
        Map<Long, double[]> stats = new HashMap<>();
        for (Object[] row : ratings.aggregate()) {
            stats.put((Long) row[0], new double[]{((Number) row[1]).doubleValue(), ((Number) row[2]).doubleValue()});
        }
        Set<Long> favoriteIds = Set.of();
        Map<Long, Integer> myRatings = Map.of();
        if (user != null) {
            favoriteIds = favorites.findByUserId(user.getId()).stream().map(f -> f.getBook().getId()).collect(Collectors.toSet());
            myRatings = ratings.findByUserId(user.getId()).stream().collect(Collectors.toMap(r -> r.getBook().getId(), Rating::getStars));
        }
        List<BookDto> out = new ArrayList<>();
        for (Book b : items) {
            double[] s = stats.getOrDefault(b.getId(), new double[]{0, 0});
            out.add(new BookDto(b.getId(), b.getTitle(), b.getAuthor(), b.getGenre(), b.getPublishedYear(), b.getDescription(),
                    Math.round(s[0] * 10) / 10.0, (long) s[1], favoriteIds.contains(b.getId()), myRatings.getOrDefault(b.getId(), 0)));
        }
        return out;
    }
}
