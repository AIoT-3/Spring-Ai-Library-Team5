package com.nhnacademy.ailibraryteam5.core.book.rag.builder;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

    public String build(String question, String context) {
        return """
            너는 도서 추천 결과를 만드는 엔진이다.
            사용자 검색어와 [참고 도서]만 보고 관련 있는 책을 최대 5권 고른다.
            참고 도서에 없는 책, id, 제목은 만들지 않는다.
            인물, 캐릭터, 작품 줄거리, 일반 지식 설명은 하지 않는다.
            점수, 유사도, 확률은 만들지 않는다.

            선택 기준:
            - 검색어가 제목, 저자, 카테고리, 소개와 직접 관련 있어야 한다.
            - 근거가 약하면 제외한다.
            - why에는 제목/저자/카테고리/소개 중 어떤 근거인지 포함한다.
            - why는 한국어 30자 이내로 쓴다.

            응답 규칙:
            - JSON 배열만 반환한다.
            - 첫 글자는 [ 이고 마지막 글자는 ] 이어야 한다.
            - 마크다운, 설명, 코드블록은 쓰지 않는다.
            - 객체 필드는 id, title, why만 사용한다.
            - title은 참고 도서의 같은 id 제목을 그대로 쓴다.

            출력 예:
            [
              {"id": 1, "title": "도서 제목", "why": "추천 이유"}
            ]

            사용자 검색어:
            %s

            참고 도서:
            %s
            """.formatted(question, context);
    }
}
