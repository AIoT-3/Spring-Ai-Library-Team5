package com.nhnacademy.ailibraryteam5.core.book.repository;

import com.nhnacademy.ailibraryteam5.core.book.domain.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> , BookRepositoryCustom {

    Optional<Book> findByIsbn(String isbn);

    List<Book> findByEmbeddingIsNullOrderByIdAsc(Pageable pageable);

    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findById(@Param("id") long id);

    @Query("SELECT b.embedding FROM Book b WHERE b.id IN :bookIds")
    List<float[]> findEmbeddingsByIds(@Param("bookIds") List<Long> bookIds);
}
