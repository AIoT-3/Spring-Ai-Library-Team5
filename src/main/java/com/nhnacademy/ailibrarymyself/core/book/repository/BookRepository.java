package com.nhnacademy.ailibrarymyself.core.book.repository;

import com.nhnacademy.ailibrarymyself.core.book.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> , BookRepositoryCustom {

    Optional<Book> findByIsbn(String isbn);

    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findById(@Param("id") long id);
}
