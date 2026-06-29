package com.nhnacademy.ailibraryteam5.core.book.rag.builder;

import org.springframework.stereotype.Component;

@Component
public class RagPromptBuilder {

    public String build(String question, String context) {
        return """
            너는 도서 검색 결과를 재정렬하는 추천 엔진이다.
            사용자의 입력은 도서 검색 키워드이며, 아래 [참고 도서] 후보 중에서만 관련 책을 고른다.
            인물, 캐릭터, 작품 줄거리, 일반 지식을 설명하지 않는다.

            반드시 지켜야 할 규칙:
            1. [참고 도서]에 있는 id만 사용한다.
            2. 없는 책, 없는 id, 없는 제목을 새로 만들지 않는다.
            3. 참고 도서 밖의 인물명, 캐릭터명, 작품명을 답변에 포함하지 않는다.
            4. 사용자의 키워드와 제목, 카테고리, 소개, 저자, 출판사가 관련 있는 책만 추천한다.
            5. 관련 책이 없으면 [] 만 반환한다.
            6. 최대 5권을 추천한다.
            7. 유사도 높은 순서로 정렬한다.
            8. 유사도는 0부터 100 사이의 정수다.
            9. why는 한국어로 30자 이내의 짧은 추천 이유만 작성한다.
            10. 응답은 JSON 배열만 반환한다.
            11. 첫 글자는 반드시 [ 이고 마지막 글자는 반드시 ] 이어야 한다.
            12. 설명 문장, 마크다운, 코드블록, 번호 목록, 제목 문장은 절대 출력하지 않는다.
            13. JSON 객체의 필드는 id, relevance, why만 사용한다.
            14. 반드시 제목/저자/카테고리/소개 중 어떤 근거로 추천했는지 구체적으로 쓴다.
            15. 사용자 키워드와 직접 연결되는 단어를 포함한다.
            16. 근거가 약하면 추천하지 않는다.

            출력 형식:
            [
              {"id": 1, "relevance": 90, "why": "추천 이유"}
            ]

            사용자 질문:
            %s

            참고 도서:
            %s
            """.formatted(question, context);
    }
}
