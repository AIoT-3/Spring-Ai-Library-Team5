package com.nhnacademy.ailibraryteam5.core.history.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "book_view_history",
        indexes = {
                @Index(name = "idx_history_user_time", columnList = "user_id, viewed_at DESC"),
                @Index(name = "idx_history_user_book", columnList = "user_id, book_id")
        }
)

@Getter
@NoArgsConstructor
public class BookViewHistory {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "user_id")
        private String userId;

        @Column(name = "book_id")
        private Long bookId;

        @Column(name = "viewed_at")
        private LocalDateTime viewedAt;

        public BookViewHistory(String userId, Long bookId) {
                this.userId = userId;
                this.bookId = bookId;
                this.viewedAt = LocalDateTime.now();
        }

        public void updateViewAt(){
                this.viewedAt = LocalDateTime.now();
        }
}
