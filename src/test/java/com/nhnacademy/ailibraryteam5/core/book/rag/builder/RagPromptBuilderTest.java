package com.nhnacademy.ailibraryteam5.core.book.rag.builder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final RagPromptBuilder ragPromptBuilder = new RagPromptBuilder();

    @Test
    void buildContainsGroundedRecommendationReasonRules() {
        String prompt = ragPromptBuilder.build("피자", "참고 도서 내용");

        assertThat(prompt)
                .contains("제목에 검색어가 들어 있다는 이유만으로 추천하지 않는다")
                .contains("제목만 일치하고 카테고리나 소개에서 관련 근거를 찾을 수 없으면 제외한다")
                .contains("사용자의 검색 의도와 책의 구체 근거를 연결한 한국어 한 문장")
                .contains("\"제목에 ~ 포함\", \"~ 관련 제목\", \"검색어와 일치\" 같은 표면적 이유는 금지한다")
                .contains("제목에 피자가 포함됨");
    }
}
