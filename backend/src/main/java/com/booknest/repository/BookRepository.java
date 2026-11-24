package com.booknest.repository;

import com.booknest.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
public interface BookRepository extends JpaRepository<Book, Long> {

     @Query("select distinct b.genre from Book b order by b.genre")
     List<String> findGenres();
}
