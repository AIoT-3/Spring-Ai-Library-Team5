# 04. Step 3 직접 구현 TODO 가이드

이 문서는 Step 3을 직접 구현하면서 채워 넣을 코드 뼈대다. 예시 코드는 완성본이 아니라 TODO를 남긴 학습용 코드다.

## 1. ChatModel 선택 확인

현재 프로젝트에는 Google GenAI, Ollama starter가 함께 있다. 먼저 어떤 `ChatModel`이 주입되는지 확인한다.

```java
// TODO: 실제 Bean 이름은 애플리케이션 시작 로그나 Bean 목록으로 확인한다.
@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            // TODO: @Qualifier 이름을 현재 프로젝트에서 확인해서 채운다.
            ChatModel ollamaChatModel
            // ChatModel geminiChatModel
    ) {
        // TODO: spring.ai.selected-model 값에 따라 모델을 선택하도록 확장한다.
        return ollamaChatModel;
    }
}
```

설명할 수 있어야 하는 것:

```text
왜 ChatModel Bean 충돌이 생길 수 있는가?
왜 selected-model은 Spring 표준 속성이 아니라 우리 프로젝트 정책인가?
```

## 2. RAG 결과 DTO

검색 결과 DTO에 RAG 필드를 계속 추가하면 복잡해진다. RAG 응답은 별도 DTO로 감싼다.

```java
public record BookAiRecommendationResponse(
        Long id,
        Integer relevance,
        String why
) {
    // TODO: relevance가 0~100 범위인지 검증하는 compact constructor를 추가한다.
}
```

```java
public record BookRagResult(
        List<BookSearchResponse> books,
        List<BookAiRecommendationResponse> recommendations,
        boolean aiAvailable
) {
    public static BookRagResult fallback(List<BookSearchResponse> books) {
        // TODO: LLM 실패 시 사용할 fallback 결과를 반환한다.
        return null;
    }
}
```

## 3. BookSearchService 진입점

Controller가 RAG를 직접 알기보다 Service가 검색 타입을 분기한다.

```java
@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final BookRepository bookRepository;
    // TODO: Step 2에서 만든 vector/hybrid 의존성을 주입한다.
    // private final BookRagService bookRagService;

    public BookSearchResult searchBooks(Pageable pageable, BookSearchRequest request) {
        return switch (request.searchType()) {
            case KEYWORD -> keywordSearch(pageable, request);
            case VECTOR -> {
                // TODO: 검색어를 embedding으로 변환하고 vectorSearch를 호출한다.
                yield keywordSearch(pageable, request);
            }
            case HYBRID -> {
                // TODO: keyword + vector 후보를 RRF로 병합한다.
                yield keywordSearch(pageable, request);
            }
            case RAG -> {
                // TODO: BookRagService로 위임한다.
                // RAG는 Page 구조와 맞지 않을 수 있으므로 BookSearchResult 확장 여부를 결정한다.
                yield keywordSearch(pageable, request);
            }
        };
    }

    private BookSearchResult keywordSearch(Pageable pageable, BookSearchRequest request) {
        return BookSearchResult.of(bookRepository.search(pageable, request));
    }
}
```

## 4. RagContextBuilder

`volumeTitle`은 사용하지 않는다. 현재 프로젝트는 `category` 기준이다.

```java
@Component
public class RagContextBuilder {

    private static final int MAX_CONTENT_LENGTH = 300;

    public String build(List<BookSearchResponse> books) {
        // TODO: books가 비어 있으면 빈 문자열을 반환한다.
        StringBuilder context = new StringBuilder("## 참고 도서\n\n");

        for (int i = 0; i < books.size(); i++) {
            BookSearchResponse book = books.get(i);

            context.append("### 도서 ").append(i + 1).append('\n');
            context.append("- ID: ").append(book.getId()).append('\n');
            context.append("- 제목: ").append(nullToDash(book.getTitle())).append('\n');
            context.append("- 저자: ").append(nullToDash(book.getAuthorName())).append('\n');
            context.append("- 출판사: ").append(nullToDash(book.getPublisherName())).append('\n');
            context.append("- 분류: ").append(nullToDash(book.getCategory())).append('\n');
            context.append("- 소개: ").append(truncate(book.getBookContent(), MAX_CONTENT_LENGTH)).append("\n\n");
        }

        // TODO: bookContent가 비어 있는 후보 비율을 로그로 남긴다.
        return context.toString();
    }

    private String nullToDash(String value) {
        // TODO: null 또는 blank면 "-"를 반환한다.
        return value;
    }

    private String truncate(String value, int maxLength) {
        // TODO: 공백 정규화 후 maxLength로 자른다.
        return value;
    }
}
```

## 5. BookAiService

LLM 호출은 실패할 수 있으므로 시간과 길이를 로그로 남긴다.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class BookAiService {

    private final ChatModel chatModel;

    public String call(String prompt) {
        long start = System.currentTimeMillis();
        try {
            // TODO: prompt 전체가 아니라 길이만 info/debug로 남긴다.
            String response = chatModel.call(prompt);
            log.info("LLM call completed. elapsed={}ms, responseLength={}",
                    System.currentTimeMillis() - start,
                    response == null ? 0 : response.length());
            return response;
        } catch (Exception e) {
            log.warn("LLM call failed. elapsed={}ms, promptLength={}",
                    System.currentTimeMillis() - start,
                    prompt == null ? 0 : prompt.length(), e);
            throw e;
        }
    }
}
```

## 6. RagResponseParser

처음에는 수동 파싱으로 시작하고, 나중에 Spring AI `BeanOutputConverter`를 검토한다.

```java
@Component
@RequiredArgsConstructor
public class RagResponseParser {

    private final ObjectMapper objectMapper;

    public List<BookAiRecommendationResponse> parse(
            String rawResponse,
            Set<Long> allowedBookIds
    ) {
        // TODO: rawResponse가 blank면 빈 리스트를 반환한다.
        // TODO: ```json 코드 블록을 제거한다.
        // TODO: 첫 '['와 마지막 ']' 사이의 JSON 배열을 추출한다.
        // TODO: ObjectMapper로 List<BookAiRecommendationResponse>로 변환한다.
        // TODO: allowedBookIds에 없는 id를 제거한다.
        // TODO: relevance가 null이거나 범위를 벗어나면 제거하거나 보정한다.
        return List.of();
    }
}
```

## 7. 직접 설명 체크리스트

```text
RAG는 왜 Repository 메서드가 아니라 Service 흐름인가?
왜 LLM 응답은 title이 아니라 id로 매칭하는가?
왜 category를 쓰고 volumeTitle을 쓰지 않는가?
LLM이 실패하면 사용자는 무엇을 보게 되는가?
컨텍스트가 길어지면 어떤 비용이 증가하는가?
```
