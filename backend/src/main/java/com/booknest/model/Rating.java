package com.booknest.model;

import jakarta.persistence.*;

/** One user's 1-5 star score for one book (a user can rate a book only once). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    private int stars;

    public Rating() {}

    public Rating(User user, Book book, int stars) {
        this.user = user;
         this.book = book;
         this.stars = stars;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Book getBook() { return book; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
}
