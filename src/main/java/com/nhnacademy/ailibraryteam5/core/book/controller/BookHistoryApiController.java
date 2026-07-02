package com.nhnacademy.ailibraryteam5.core.book.controller;

import com.nhnacademy.ailibraryteam5.core.history.service.PersonalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class BookHistoryApiController {
    private final PersonalizationService personalizationService;

    @DeleteMapping
    public ResponseEntity<Void> deleteAllHistory(
            @RequestParam(value = "userId", defaultValue = PersonalizationService.TEMP_USER_ID) String userId
    ){
        personalizationService.userDeleteAllBook(userId);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{historyId}")
    public ResponseEntity<Void> deleteHistory(
            @PathVariable Long historyId
    ){
        personalizationService.userDeleteBook(historyId);

        return ResponseEntity.ok().build();
    }
}
