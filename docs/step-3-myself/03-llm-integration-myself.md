# 03. 현재 프로젝트 LLM 연동과 RAG 응답 처리

이 문서는 강의자료 `docs/step-3/03.llm-integration.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

현재 `pom.xml`에는 Google GenAI, OpenAI, Ollama starter가 모두 들어가 있고, `application.properties`에는 Gemini와 Ollama 설정이 있다. 따라서 의존성을 새로 추가하기보다 어떤 `ChatModel`을 사용할지와 실패 처리를 정하는 것이 먼저다.

## 현재 설정 기준

```properties
spring.ai.google.genai.chat.options.model=gemini-2.5-flash
spring.ai.google.genai.chat.options.temperature=0.3

spring.ai.ollama.chat.options.model=qwen2.5:3b-instruct-q4_K_M
spring.ai.ollama.chat.options.temperature=0.3

spring.ai.selected-model=ollama
```

Step 3 RAG 추천은 일관성이 중요하므로 temperature는 낮게 둔다. `0.3` 정도면 충분하다.

## BookAiService 책임

LLM 호출은 한 클래스에 모은다.

```text
BookRagService
  -> RagContextBuilder
  -> RagPromptTemplate
  -> BookAiService
  -> RagResponseParser
```

`BookAiService`는 prompt를 받아 문자열 응답을 반환하는 정도로 작게 시작한다.

```java
@Service
@RequiredArgsConstructor
public class BookAiService {

    private final ChatModel chatModel;

    public String call(String prompt) {
        return chatModel.call(prompt);
    }
}
```

Gemini와 Ollama를 선택적으로 바꾸고 싶다면 `spring.ai.selected-model` 값을 읽는 라우터를 둘 수 있다. 하지만 Step 3 첫 구현에서는 자동 주입되는 `ChatModel` 하나로 먼저 동작을 확인하는 편이 낫다.

## JSON 응답 DTO

LLM 응답은 화면 표시와 도서 매칭을 위해 구조화한다.

```java
public record BookAiRecommendationResponse(
        Long id,
        Integer relevance,
        String why
) {
}
```

검색 결과의 상세 정보는 이미 `BookSearchResponse`에 있으므로, LLM은 `id`, `relevance`, `why`만 내려줘도 된다. 이후 화면에서는 `id`로 검색 결과와 합쳐 제목, 이미지, 저자, category를 보여준다.

## 프롬프트 출력 형식

```text
[출력 형식]
반드시 JSON 배열만 출력한다.
마크다운 코드 블록을 쓰지 않는다.
참고 도서에 없는 id를 쓰지 않는다.

[
  {
    "id": 101,
    "relevance": 92,
    "why": "자바 입문자가 문법과 객체지향을 순서대로 학습하기 좋습니다."
  }
]
```

`title`을 LLM 응답에 넣지 않는 이유는 모델이 제목을 조금 바꿔 쓰는 경우가 있기 때문이다. 매칭 키는 `id`가 가장 안전하다.

## 응답 파싱

LLM은 규칙을 줘도 가끔 코드 블록이나 설명 문장을 섞는다. 파서는 최소한 아래 처리를 해야 한다.

```text
1. 앞뒤 공백 제거
2. ```json, ``` 코드 블록 제거
3. 첫 `[`부터 마지막 `]`까지만 추출
4. Jackson으로 List<BookAiRecommendationResponse> 파싱
5. 검색 결과에 없는 id 제거
6. relevance 범위 0-100 검증
```

파싱 실패 시 전체 검색을 실패시키지 말고 HYBRID 결과만 반환한다.

## BookRagService 흐름

```text
1. question 검증
2. HYBRID 검색으로 후보 도서 조회
3. 후보가 없으면 빈 RAG 결과 반환
4. topK 후보로 컨텍스트 생성
5. 프롬프트 생성
6. LLM 호출
7. JSON 파싱
8. id 기준으로 검색 결과와 추천 이유 병합
```

`RAG`에서 `KEYWORD`나 `VECTOR` 단독 검색을 바로 쓰기보다 `HYBRID`를 쓰는 이유는 고유명사와 자연어 질문을 모두 처리하기 위해서다.

## fallback 정책

LLM 호출은 실패할 수 있다. 아래 fallback을 정해둔다.

| 실패 지점 | 대응 |
| --- | --- |
| 검색 결과 없음 | LLM 호출하지 않고 빈 결과 |
| ChatModel timeout | HYBRID 검색 결과만 표시 |
| JSON 파싱 실패 | 원문 로그 남기고 HYBRID 결과 표시 |
| 없는 id 추천 | 해당 항목 제거 |
| 모든 추천 제거 | HYBRID topK 표시 |

로그에는 prompt 전체를 info로 남기지 않는다. 도서 정보와 사용자 질문이 길 수 있으므로 debug 로그나 길이만 남긴다.

## 완료 기준

```text
1. RAG는 HYBRID 후보를 기반으로 동작한다.
2. LLM 응답은 id 기반 JSON으로 받는다.
3. category 필드는 검색 결과에서 유지된다.
4. 없는 id와 파싱 실패를 방어한다.
5. LLM 실패 시에도 화면은 검색 결과를 보여줄 수 있다.
```
