package com.booknest.config;

import com.booknest.model.Book;
import com.booknest.model.Favorite;
import com.booknest.model.Rating;
import com.booknest.model.User;
import com.booknest.repository.BookRepository;
import com.booknest.repository.FavoriteRepository;
import com.booknest.repository.RatingRepository;
import com.booknest.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * Fills an empty database with demo data the first time the app starts:
 * 49 books, an admin, a demo student and three sample readers with ratings.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    record SeedBook(String title, String author, String genre, Integer year, String description) {}

    private final BookRepository books;
    private final UserRepository users;
    private final RatingRepository ratings;
    private final FavoriteRepository favorites;
    private final PasswordEncoder encoder;
    public DataSeeder(BookRepository books, UserRepository users, RatingRepository ratings,
                      FavoriteRepository favorites, PasswordEncoder encoder) {
        this.books = books;
        this.users = users;
        this.ratings = ratings;
        this.favorites = favorites;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (books.count() > 0) return;

        List<SeedBook> seed;
        try (InputStream in = new ClassPathResource("seed-books.json").getInputStream()) {
            seed = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
        List<Book> saved = new ArrayList<>();
        for (SeedBook s : seed) {
            Book b = new Book();
            b.setTitle(s.title());
            b.setAuthor(s.author());
            b.setGenre(s.genre());
            b.setPublishedYear(s.year());
            b.setDescription(s.description());
            saved.add(books.save(b));
        }

        makeUser("Library Admin", "admin@booknest.com", "admin123", "ADMIN");
        User student = makeUser("Demo Student", "student@booknest.com", "student123", "USER");

        // The demo student already likes fantasy and science fiction, so "For You" has something to show.
        like(student, find(saved, "The Hobbit"), 5, true);
        like(student, find(saved, "Dune"), 5, true);
        like(student, find(saved, "Foundation"), 4, false);
        like(student, find(saved, "The Name of the Wind"), 4, false);

        // Three sample readers so the catalog shows average ratings. Deterministic (fixed seed).
        String[] readers = {"Aarav", "Diya", "Kabir"};
        Random random = new Random(7);
        for (String name : readers) {
            User reader = makeUser(name, name.toLowerCase() + "@booknest.com", "reader123", "USER");
            List<Book> shuffled = new ArrayList<>(saved);
            Collections.shuffle(shuffled, random);
            for (Book b : shuffled.subList(0, 22)) {
                ratings.save(new Rating(reader, b, 3 + random.nextInt(3))); // 3, 4 or 5 stars
            }
        }
        log.info("Seeded {} books and demo users. Admin login: admin@booknest.com / admin123, Student: student@booknest.com / student123", saved.size());
    }

    private User makeUser(String name, String email, String password, String role) {
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

     private void like(User user, Book book, int stars, boolean favorite) {
         ratings.save(new Rating(user, book, stars));
         if (favorite) favorites.save(new Favorite(user, book));
     }

    private Book find(List<Book> list, String title) {
        return list.stream().filter(b -> b.getTitle().equals(title)).findFirst().orElseThrow();
    }
}
