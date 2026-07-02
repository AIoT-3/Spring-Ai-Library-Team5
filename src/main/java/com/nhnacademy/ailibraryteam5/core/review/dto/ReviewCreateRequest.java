package com.nhnacademy.ailibraryteam5.core.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReviewCreateRequest(
        @NotBlank String content,
        @NotNull
        @Min(1)
        @Max(5)
        Integer rating
) {

}
