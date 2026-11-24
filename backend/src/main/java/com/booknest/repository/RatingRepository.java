package com.booknest.repository;

import com.booknest.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

     Optional<Rating> findByUserIdAndBookId(Long userId, Long bookId);

     List<Rating> findByUserId(Long userId);

     void deleteByBookId(Long bookId);

    /** One row per book: [bookId, average stars, number of ratings]. */
    @Query("select r.book.id, avg(r.stars), count(r) from Rating r group by r.book.id")
    List<Object[]> aggregate();
}
