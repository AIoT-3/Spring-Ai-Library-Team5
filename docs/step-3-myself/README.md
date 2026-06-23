# Step 3 Myself

이 패키지는 `docs/step-3` 강의자료를 현재 프로젝트 기준으로 보강한 문서 모음이다.

Step 3의 목표는 Step 2에서 만든 `HYBRID` 검색 결과를 LLM에 넘겨 추천 이유를 생성하는 것이다. 현재 프로젝트 기준으로는 `volumeTitle`이 아니라 `category`를 사용하고, `Book.embedding`은 `vector(1024)` 기준으로 유지한다.

## 챕터 구성

| 원본 | 보강 문서 | 핵심 보강 |
| --- | --- | --- |
| `01.understanding-rag.md` | `01-understanding-rag-myself.md` | 현재 검색 타입 기준 RAG 책임 분리 |
| `02.context-construction.md` | `02-context-construction-myself.md` | `category` 기반 컨텍스트, 토큰 제한, 근거 보존 |
| `03.llm-integration.md` | `03-llm-integration-myself.md` | Spring AI 설정, JSON 응답 파싱, fallback |
| 추가 | `04-implementation-todo-guide.md` | 직접 구현용 TODO 코드 뼈대 |

## 구현 권장 순서

```text
1. HYBRID 검색이 안정적으로 topN 후보를 반환하게 만든다.
2. RAG 전용 DTO를 만든다.
3. 검색 결과를 LLM 컨텍스트 문자열로 변환한다.
4. 프롬프트 템플릿과 JSON 응답 DTO를 정한다.
5. ChatModel 호출 서비스를 만든다.
6. RAG 실패 시 HYBRID 검색 결과만이라도 화면에 보여주는 fallback을 둔다.
```

RAG는 검색을 대체하지 않는다. 검색 결과를 근거로 답변을 생성하는 마지막 단계다.

## 학습 방식

Step 3은 코드를 한 번에 완성하기보다 아래 순서로 직접 채워 넣는다.

```text
1. ChatModel Bean 충돌을 먼저 확인한다.
2. BookSearchService에서 SearchType.RAG 진입점을 만든다.
3. BookRagService는 아직 LLM을 부르지 말고 HYBRID 결과만 반환해 본다.
4. RagContextBuilder TODO를 채워 컨텍스트 문자열을 눈으로 확인한다.
5. BookAiService TODO를 채워 LLM 호출 시간을 로그로 남긴다.
6. RagResponseParser TODO를 채워 JSON 파싱과 fallback을 확인한다.
```

완성 코드를 외우는 것이 목표가 아니다. 각 클래스가 왜 분리됐는지, 실패했을 때 어디에서 fallback되는지 설명할 수 있으면 된다.

---

## 코드 리뷰 의견

### 총평

Step 3 문서의 구성이 탄탄하다. 특히 "RAG는 DB 검색 방식이 아니라 서비스 흐름"이라는 점을 분명히 한 것, 그리고 fallback 정책까지 미리 설계한 것이 좋다. `category` 기준 유지도 일관적이다. 현재 코드 기반에서 구현할 때 추가로 고려할 포인트를 아래에 남긴다.

### 1. ChatModel Bean 충돌 문제를 먼저 해결해야 한다

현재 `pom.xml`에 Google GenAI, OpenAI, Ollama starter가 모두 있다. Spring AI는 각 starter마다 `ChatModel` Bean을 자동 등록하므로, 세 개가 동시에 올라오면 `NoUniqueBeanDefinitionException`이 발생할 수 있다.

문서 03에서 `spring.ai.selected-model=ollama`를 언급했지만, 이건 Spring AI 표준 속성이 아니라 커스텀 속성이다. 따라서 실제로 어떤 `ChatModel`을 사용할지 라우팅하는 `@Configuration`이 필요하다.

```java
@Configuration
@RequiredArgsConstructor
public class ChatModelConfig {

    @Value("${spring.ai.selected-model:ollama}")
    private String selectedModel;

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("ollamaChatModel") ChatModel ollamaModel,
            @Qualifier("googleGenAiChatModel") ChatModel geminiModel
    ) {
        return "gemini".equalsIgnoreCase(selectedModel) ? geminiModel : ollamaModel;
    }
}
```

**또는** 더 단순하게, 사용하지 않는 starter의 auto configuration을 `@SpringBootApplication(exclude = ...)`로 제외한다. Step 3 시작 전에 이 충돌 문제를 먼저 확인하고 넘어가야 한다.

### 2. `BookAiService.call()`이 너무 단순하다 — 에러 추적이 어렵다

문서의 예시가 `chatModel.call(prompt)` 한 줄인데, 실제 운영에서는 아래 정보를 같이 남겨야 디버깅이 가능하다.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BookAiService {

    private final ChatModel chatModel;

    public String call(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String response = chatModel.call(prompt);
            log.debug("LLM call completed. elapsed={}ms, responseLength={}",
                    System.currentTimeMillis() - start,
                    response == null ? 0 : response.length());
            return response;
        } catch (Exception e) {
            log.error("LLM call failed. elapsed={}ms, promptLength={}",
                    System.currentTimeMillis() - start, prompt.length(), e);
            throw e;
        }
    }
}
```

Step 4에서 성능 로그를 추가하게 되므로, 처음부터 시간 측정을 넣어두면 나중에 수정 범위가 줄어든다.

### 3. `RagResponseParser`에서 JSON 추출 로직이 생각보다 까다롭다

문서에서 "첫 `[`부터 마지막 `]`까지 추출"이라고 했는데, LLM이 JSON 안에 배열을 중첩으로 쓰면 잘못 잘릴 수 있다. 실전에서 더 안전한 방법은 Spring AI의 `BeanOutputConverter`를 활용하는 것이다.

```java
var converter = new BeanOutputConverter<>(
    new ParameterizedTypeReference<List<BookAiRecommendationResponse>>() {}
);
// 프롬프트에 converter.getFormat()을 포함
// 응답을 converter.convert(response)로 파싱
```

이 방식을 쓰면 프롬프트에 JSON schema를 자동으로 삽입하고, 파싱도 한 줄로 끝난다. 다만 Spring AI 버전에 따라 API가 다를 수 있으므로 1.1.4 기준 API를 확인해야 한다.

수동 파싱을 고집하려면, 최소한 `indexOf('[')` / `lastIndexOf(']')` 사이에 유효한 JSON인지 `ObjectMapper.readValue()`로 검증하고, 실패하면 fallback으로 넘어가는 2단계 로직이 필요하다.

### 4. `BookRagService`와 `BookSearchService`의 관계를 명확히 해야 한다

문서 01에서 `BookSearchController → BookSearchService or BookRagService` 경로를 제안했다. 여기서 "or"가 모호하다. 두 가지 구조가 가능하다.

| 구조 | 장점 | 단점 |
| --- | --- | --- |
| Controller가 SearchType을 보고 직접 분기 | 명시적, 흐름이 보임 | Controller가 로직을 많이 안다 |
| `BookSearchService`가 RAG일 때 `BookRagService`에 위임 | Service 계층에서 분기, Controller 단순 | Service 간 의존 발생 |

**추천**: `BookSearchService`가 모든 SearchType의 진입점이 되고, `RAG`일 때 내부에서 `BookRagService`를 호출하는 방식이 좋다. Controller는 SearchType을 모르게 유지한다.

```java
public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
    return switch (request.searchType()) {
        case KEYWORD -> keywordSearch(pageable, request);
        case VECTOR  -> vectorSearch(pageable, request);
        case HYBRID  -> hybridSearch(pageable, request);
        case RAG     -> ragSearch(pageable, request);
    };
}
```

### 5. RAG 응답 DTO와 일반 검색 응답 DTO를 분리할지 결정해야 한다

현재 `BookSearchResult`는 `Page<BookSearchResponse>`를 감싸고 있다. RAG 응답에는 추천 이유(`why`), 관련도(`relevance`) 같은 필드가 추가된다. 이걸 `BookSearchResponse`에 넣을지, 별도 래퍼를 만들지 결정해야 한다.

**추천**: `BookRagResult`를 별도로 만들어 검색 결과와 추천 이유를 조합한다.

```java
public record BookRagResult(
    List<BookSearchResponse> books,  // HYBRID 검색 결과
    List<BookAiRecommendationResponse> recommendations,  // LLM 추천 목록
    boolean aiAvailable  // fallback 여부
) {}
```

화면에서는 `recommendations`의 `id`로 `books`를 찾아 표시한다. 이러면 `BookSearchResponse`는 검색 전용 DTO로 유지되고, RAG 관련 필드가 섞이지 않는다.

### 6. `RagContextBuilder.build()`에서 빈 `bookContent` 비율이 높으면 컨텍스트 품질이 크게 떨어진다

CSV 데이터에서 `bookContent`가 null인 도서가 상당수일 수 있다. 이 경우 컨텍스트가 "소개: -"로 가득 차서 LLM이 추천 이유를 만들기 어렵다.

**추천**: `bookContent`가 비어 있는 도서의 비율을 로그로 남기고, 비율이 높으면 `subtitle`이나 `category`를 보조 근거로 강화한다.

```java
long emptyContentCount = books.stream()
    .filter(b -> b.getBookContent() == null || b.getBookContent().isBlank())
    .count();
if (emptyContentCount > books.size() / 2) {
    log.warn("RAG context: {}% of candidates have no bookContent",
        emptyContentCount * 100 / books.size());
}
```

### 우선순위 요약

| 우선순위 | 항목 | 시점 |
| --- | --- | --- |
| **1** | ChatModel Bean 충돌 해결 | Step 3 시작 전 |
| **2** | `BookSearchService` ↔ `BookRagService` 관계 결정 | RAG 서비스 생성 시 |
| **3** | RAG 응답 DTO 분리 여부 결정 | Controller 연결 시 |
| **4** | JSON 파싱 전략 결정 (수동 vs `BeanOutputConverter`) | LLM 연동 시 |
| **5** | `BookAiService`에 시간 측정 로그 | LLM 연동 시 |
| **6** | 빈 `bookContent` 비율 경고 | 컨텍스트 구성 시 |
