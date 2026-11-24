package com.booknest.controller;

import com.booknest.dto.Dtos.*;
import com.booknest.security.CurrentUser;
import com.booknest.service.BookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalog endpoints.
 *   GET    /api/books?q=&genre=&sort=        public
 *   GET    /api/books/{id}                   public
 *   GET    /api/genres                       public
 *   POST   /api/books                        admin
 *   PUT    /api/books/{id}                   admin
 *   DELETE /api/books/{id}                   admin
 *   PUT    /api/books/{id}/rating            logged in
 *   GET    /api/favorites                    logged in
 *   PUT    /api/favorites/{bookId}           logged in
 *   DELETE /api/favorites/{bookId}           logged in
 */
@RestController
@RequestMapping("/api")
public class BookController {

     private final BookService books;
     private final CurrentUser currentUser;

     public BookController(BookService books, CurrentUser currentUser) {
         this.books = books;
         this.currentUser = currentUser;
     }
    @GetMapping("/books")
    public List<BookDto> list(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String genre,
                              @RequestParam(required = false) String sort) {
        return books.list(q, genre, sort, currentUser.orNull());
    }

    @GetMapping("/books/{id}")
    public BookDto get(@PathVariable Long id) {
        return books.get(id, currentUser.orNull());
    }

    @GetMapping("/genres")
    public List<String> genres() {
        return books.genres();
    }

    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    public BookDto create(@Valid @RequestBody BookRequest request) {
        currentUser.requireAdmin();
        return books.create(request);
    }

    @PutMapping("/books/{id}")
    public BookDto update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        currentUser.requireAdmin();
        return books.update(id, request);
    }

    @DeleteMapping("/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        currentUser.requireAdmin();
        books.delete(id);
    }

    @PutMapping("/books/{id}/rating")
    public BookDto rate(@PathVariable Long id, @Valid @RequestBody RatingRequest request) {
        return books.rate(id, request.stars(), currentUser.require());
    }

    @GetMapping("/favorites")
    public List<BookDto> favorites() {
        return books.favoritesOf(currentUser.require());
    }

    @PutMapping("/favorites/{bookId}")
    public BookDto addFavorite(@PathVariable Long bookId) {
        return books.setFavorite(bookId, true, currentUser.require());
    }
     @DeleteMapping("/favorites/{bookId}")
     public BookDto removeFavorite(@PathVariable Long bookId) {
         return books.setFavorite(bookId, false, currentUser.require());
     }
}
