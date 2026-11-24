package com.booknest.controller;

import com.booknest.ai.TfIdfIndex;
import com.booknest.ai.TfIdfIndex.Scored;
import com.booknest.dto.Dtos.*;
import com.booknest.model.Book;
import com.booknest.repository.BookRepository;
import com.booknest.security.CurrentUser;
import com.booknest.service.BookService;
import com.booknest.service.ChatService;
import com.booknest.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The AI features, all consumed by the React front-end through REST:
 *   GET /api/books/{id}/similar     "readers also like" (content-based similarity)   public
 *   GET /api/recommendations        personalised "For you" list logged in
 *   GET /api/ai/search?q=           smart search (ranks by meaning, not exact text)   public
 *   POST /api/chat                  BookBot public
 *   GET /api/chat/status            which engine BookBot is currently using public
 *   GET /api/chat/test              one tiny LLM call, shows the real error if it fails public
 */
@RestController
@RequestMapping("/api")
public class AiController {

     private final RecommendationService recommendations;
     private final ChatService chat;
     private final TfIdfIndex index;
     private final BookRepository bookRepository;
     private final BookService bookService;
     private final CurrentUser currentUser;

    public AiController(RecommendationService recommendations, ChatService chat, TfIdfIndex index,
                        BookRepository bookRepository, BookService bookService, CurrentUser currentUser) {
        this.recommendations = recommendations;
        this.chat = chat;
        this.index = index;
        this.bookRepository = bookRepository;
        this.bookService = bookService;
        this.currentUser = currentUser;
    }

    @GetMapping("/books/{id}/similar")
    public List<RecommendationDto> similar(@PathVariable Long id, @RequestParam(defaultValue = "4") int limit) {
        return recommendations.similarBooks(id, Math.min(Math.max(limit, 1), 12), currentUser.orNull());
    }

    @GetMapping("/recommendations")
    public List<RecommendationDto> forYou(@RequestParam(defaultValue = "8") int limit) {
        return recommendations.forUser(currentUser.require(), Math.min(Math.max(limit, 1), 20));
    }

    @GetMapping("/ai/search")
    public List<BookDto> smartSearch(@RequestParam String q) {
        List<Scored> scored = index.search(q, 12);
        Map<Long, Book> books = bookRepository.findAllById(scored.stream().map(Scored::bookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        Map<Long, BookDto> dtos = bookService.toDtos(books.values(), currentUser.orNull()).stream()
                .collect(Collectors.toMap(BookDto::id, Function.identity()));
        return scored.stream().map(s -> dtos.get(s.bookId())).filter(java.util.Objects::nonNull).toList();
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chat.chat(request, currentUser.orNull());
    }

    /** Open http://localhost:8080/api/chat/test in a browser to see whether the LLM key works, and the exact error if not. */
    @GetMapping("/chat/test")
    public java.util.Map<String, Object> chatTest() {
        return chat.selfTest();
    }

    @GetMapping("/chat/status")
    public ChatStatus chatStatus() {
        return chat.status();
    }
}
