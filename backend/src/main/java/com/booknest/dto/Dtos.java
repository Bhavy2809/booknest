package com.booknest.dto;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * DTOs (Data Transfer Objects) are the exact JSON shapes that go over the REST API.
 * We never send the JPA entities directly: it keeps password hashes out of responses
 * and lets the API change without changing the database.
 */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 60) String name,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Password is required") @Size(min = 6, max = 100, message = "Password needs at least 6 characters") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UserDto(Long id, String name, String email, String role) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---------- books ----------
    public record BookRequest(
            @NotBlank(message = "Title is required") @Size(max = 200) String title,
            @NotBlank(message = "Author is required") @Size(max = 200) String author,
            @NotBlank(message = "Genre is required") @Size(max = 60) String genre,
            @Min(value = -3000, message = "Year looks wrong") @Max(value = 2100, message = "Year looks wrong") Integer year,
            @Size(max = 2000, message = "Description can be up to 2000 characters") String description) {}
     /** favorite and myRating are personal: false/0 when nobody is logged in. */
    public record BookDto(Long id, String title, String author, String genre, Integer year, String description,
                           double avgRating, long ratingCount, boolean favorite, int myRating) {}

    public record RatingRequest(@Min(value = 1, message = "Rating is 1 to 5") @Max(value = 5, message = "Rating is 1 to 5") int stars) {}

    // ---------- AI ----------
    /**
     * A book suggested by the recommender, with a human-readable explanation.
     * source says who wrote the explanation: "ai" (written by the LLM) or "rules" (built from shared words).
     */
    public record RecommendationDto(BookDto book, double score, String reason, String source) {}

     public record ChatTurn(String role, String content) {}

    public record ChatRequest(@NotBlank(message = "Say something first") @Size(max = 500) String message, List<ChatTurn> history) {}

    public record ChatResponse(String reply, List<BookDto> books, String mode) {}

    /** mode is "llm" or "local"; model is a display name such as "Gemini (gemini-3.8-flash)". */
    public record ChatStatus(String mode, String model, String error) {}
}
