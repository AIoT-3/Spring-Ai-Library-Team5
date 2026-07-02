package com.nhnacademy.ailibraryteam5.core.history.repository;

import com.nhnacademy.ailibraryteam5.core.history.domain.BookViewHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookViewHistoryRepository extends JpaRepository<BookViewHistory, Long> {

    @Query("""
                SELECT b.bookId FROM  BookViewHistory b 
                WHERE b.userId = :userId
                ORDER BY b.viewedAt DESC 
                LIMIT 20
            """)
    List<Long> findRecentBookIds(@Param("userId") String userId);

    Optional<BookViewHistory> findByUserIdAndBookId(String userId, Long bookId);

    @Modifying(clearAutomatically = true)
    @Query("delete from BookViewHistory b where b.userId = :userId and b.bookId = :bookId")
    void deleteByUserIdAndBookId(String userId, Long bookId);

    @Modifying(clearAutomatically = true)
    @Query("delete from BookViewHistory b where b.userId = :userId")
    void deleteAllByUserId(String userId);

    List<BookViewHistory> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);
}
