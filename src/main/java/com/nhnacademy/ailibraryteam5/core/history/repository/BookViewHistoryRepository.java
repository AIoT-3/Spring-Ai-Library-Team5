package com.nhnacademy.ailibraryteam5.core.history.repository;

import com.nhnacademy.ailibraryteam5.core.history.domain.BookViewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookViewHistoryRepository extends JpaRepository<BookViewHistory, Long> {

    @Query("""
                SELECT b.bookId FROM  BookViewHistory b 
                WHERE b.userId = :userId
                ORDER BY b.viewedAt DESC 
                LIMIT 20
            """)
    List<Long> findRecentBookIds(@Param("userId") String userId);
}
