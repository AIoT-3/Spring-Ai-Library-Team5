# Spring AI 1.1.2 A2A 구현 가능성 분석

## 개요

본 문서는 현재 step-7 코드를 분석하고 Spring AI 1.1.2 기반으로 A2A (Agent-to-Agent) 아키텍처를 구현하는 것의 **가능성과 마이그레이션 경로**를 상세히 분석합니다.

---

## 1. 현재 아키텍처 분석 (step-7)

### 1.1 구조

```
┌─────────────────────────────────────────────────────────────┐
│                     AiOrchestrationService                  │
│                      (MainAgent)                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. QueryParserAgent (llama3-korean-blossom)               │
│     └─ 쿼리 파싱 및 엔티티 추출                            │
│                                                             │
│  2. FunctionToolCallback 생성 (프로그래밍 방식)             │
│     ├─ SearchBooks                                          │
│     ├─ SearchLibraries                                      │
│     └─ CheckBookExists                                      │
│                                                             │
│  3. ChatClient (qwen2.5) + Function Tools                   │
│     └─ LLM이 스스로 Function 선택 및 실행                   │
│                                                             │
│  4. 결과 종합 및 응답 생성                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 핵심 컴포넌트

#### AiOrchestrationService (MainAgent)
- **역할**: 메인 오케스트레이터
- **모델**: qwen2.5 (메인 LLM)
- **패턴**: Function Calling + Query Parsing
- **특징**:
  - QueryParserAgent로 쿼리 전처리
  - FunctionToolCallback을 프로그래밍 방식으로 생성 (순환 의존성 회피)
  - ChatClient.builder()로 Function 등록

#### QueryParserAgent
- **역할**: 쿼리 파싱 및 엔티티 추출
- **모델**: llama3-korean-blossom (한국어 특화)
- **출력**: ParsedQuery (도서명, 지역명, 의도, 키워드)
- **특징**:
  - 구조화된 JSON 출력
  - Fallback 파싱 (규칙 기반)
  - 지역명 → 지역 코드 변환

#### FunctionToolCallback 패턴
```java
FunctionToolCallback<BookSearchRequest, BookSearchResult> searchBooksTool =
    FunctionToolCallback.builder("SearchBooks", libraryFunctions.searchBooks())
        .description("도서관 시스템에서 도서를 검색합니다.")
        .inputType(BookSearchRequest.class)
        .build();
```

#### LibraryFunctions
- **역할**: 도서관정보나루 API Function 집합
- **Functions**:
  - `searchBooks()`: 도서 검색
  - `searchLibraries()`: 도서관 검색
  - `checkBookExists()`: 소장 여부 확인

### 1.3 실행 흐름

```
User Message
    ↓
[Step 1] QueryParserAgent.parse()
    ├─ llama3-korean-blossom 호출
    └─ ParsedQuery 반환 (bookName, region, intent)
    ↓
[Step 2] FunctionToolCallback 생성
    ├─ SearchBooks
    ├─ SearchLibraries
    └─ CheckBookExists
    ↓
[Step 3] ChatClient.builder(chatModel)
    .defaultToolCallbacks(...tools)
    .build()
    ↓
[Step 4] ChatClient.prompt()
    .system(systemPrompt)
    .user(userMessage)
    .call()
    ↓
[Step 5] qwen2.5 LLM
    ├─ 필요한 Function 스스로 선택
    ├─ Function 실행
    └─ 결과 종합
    ↓
Final Response
```

---

## 2. Spring AI 1.1.2 A2A 기능 분석

### 2.1 지원하는 Multi-Agent 패턴

Spring AI 1.1.2는 다음과 같은 멀티 에이전트 패턴을 지원합니다:

#### 2.1.1 Function Calling 기반 A2A
- **개념**: Agent 간 통신을 Function Calling으로 구현
- **특징**:
  - 각 Agent가 ChatClient를 가짐
  - Agent 간 호출을 Function Tool로 등록
  - Spring AI의 기본 패턴

#### 2.1.2 ReAct Agent Pattern
- **개념**: Reasoning + Acting 패턴
- **특징**:
  - Thought → Action → Observation 반복
  - ChatClient의 Advisors를 활용
  - 단일 LLM 내에서의 reasoning loop

#### 2.1.3 Coordinator Agent Pattern
- **개념**: 중앙 코디네이터가 하위 Agent调度
- **특징**:
  - Main Agent가 Sub Agent들을 Function으로 등록
  - Parallel/Sequential 실행 가능
  - **현재 step-7와 가장 유사한 패턴**

### 2.2 Spring AI 1.1.2 핵심 API

#### ChatClient API
```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultToolCallbacks(agent1Tool, agent2Tool)  // Agent 등록
    .defaultAdvisors(new PromptChatMemoryAdvisor()) // Chat Memory
    .build();
```

#### FunctionToolCallback
```java
FunctionToolCallback<AgentRequest, AgentResponse> agentTool =
    FunctionToolCallback.builder("AgentName", agent::execute)
        .description("Agent description for LLM")
        .inputType(AgentRequest.class)
        .build();
```

#### Advisors API
- **PromptChatMemoryAdvisor**: 대화 기록 유지
- **QuestionAnswerAdvisor**: RAG 패턴 지원
- **VectorStoreChatMemoryAdvisor**: 벡터 스토어 기반 메모리

---

## 3. A2A 구현 가능성 평가

### 3.1 가능성: ✅ **매우 높음 (HIGHLY FEASIBLE)**

**이유:**

1. **이미 Function Calling 패턴 사용 중**
   - 현재 `AiOrchestrationService`가 FunctionToolCallback 패턴 사용
   - A2A는 이를 Agent 간 통신으로 확장하는 것뿐

2. **이미 다중 LLM 사용 중**
   - QueryParserAgent: llama3-korean-blossom
   - MainAgent: qwen2.5
   - 각 Agent가 독립적인 ChatClient 보유

3. **이미 Agent 인터페이스 존재**
   - `Agent.java` 인터페이스 정의됨
   - `BookAgent`, `LibraryAgent`, `LoanAgent`, `ReviewAgent` 등 구현됨
   - A2A 마이그레이션 용이

4. **Spring AI 1.1.2 충분한 기능**
   - ChatClient.builder()의 Tool 등록
   - FunctionToolCallback.builder() 패턴
   - Advisors를 통한 확장성

### 3.2 기술적 제약사항

**제약 없음** - Spring AI 1.1.2로 A2A 구현에 필요한 모든 기능이 제공됨

---

## 4. 마이그레이션 경로

### 4.1 Phase 1: Agent Function Tool 등록 (가능)

현재 `LibraryFunctions`를 Agent들로 대체:

```java
// Before: LibraryFunctions
FunctionToolCallback<BookSearchRequest, BookSearchResult> searchBooksTool =
    FunctionToolCallback.builder("SearchBooks", libraryFunctions.searchBooks())
        .description("...")
        .build();

// After: BookAgent를 Function Tool로 등록
FunctionToolCallback<AgentRequest, AgentResponse> bookAgentTool =
    FunctionToolCallback.builder("BookAgent", bookAgent::execute)
        .description("도서 검색 전담 Agent. 책 제목, 저자명으로 도서를 검색합니다.")
        .inputType(AgentRequest.class)
        .build();
```

**변경 필요:**
1. `BookAgent.execute(AgentRequest)` 반환 타입을 `AgentResponse`로 변경
2. 각 Agent를 FunctionToolCallback으로 등록
3. AiOrchestrationService에서 Agent Tools 등록

### 4.2 Phase 2: Advisor 체인 추가 (권장)

Chat Memory와 Prompt Enhancement 추가:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultToolCallbacks(bookAgentTool, libraryAgentTool, loanAgentTool)
    .defaultAdvisors(
        new PromptChatMemoryAdvisor(chatMemory),  // 대화 기록 유지
        new SimpleLoggerAdvisor()                 // 로깅
    )
    .build();
```

### 4.3 Phase 3: Multi-Step Orchestration (선택)

복잡한 작업을 여러 Agent가 협력하여 처리:

```java
// Step 1: QueryParserAgent로 쿼리 파싱
ParsedQuery parsed = queryParserAgent.parse(userMessage);

// Step 2: Intent에 따라 Agent 선택
if (parsed.intent() == SearchIntent.CHECK_AVAILABILITY) {
    // BookAgent → LoanAgent 협력
    String isbn = bookAgent.search(parsed.bookName());
    return loanAgent.checkAvailability(parsed.regionCode(), isbn);
}
```

---

## 5. 코드 예시: A2A 마이그레이션

### 5.1 Agent 인터페이스 변경

```java
public interface Agent {
    AgentType getType();
    String getName();
    String getDescription();  // LLM이 Agent 선택 시 사용

    // 변경: 반환 타입을 AgentResponse로 통일
    AgentResponse execute(AgentRequest request);
}
```

### 5.2 AgentResponse DTO 추가

```java
public record AgentResponse(
    String result,           // 자연어 응답
    Map<String, Object> data,  // 구조화된 데이터
    AgentType agentType     // 응답 Agent 타입
) {}
```

### 5.3 AiOrchestrationService 개선

```java
@Service
public class AiOrchestrationService {

    private final ChatModel chatModel;
    private final QueryParserAgent queryParserAgent;
    private final List<Agent> agents;  // 모든 Agent 주입

    public String ask(String userMessage) {
        // 1. QueryParserAgent로 쿼리 파싱
        ParsedQuery parsed = queryParserAgent.parse(userMessage);

        // 2. Agent들을 Function Tool로 변환
        List<FunctionToolCallback<AgentRequest, AgentResponse>> agentTools =
            agents.stream()
                .map(agent -> FunctionToolCallback.builder(
                        agent.getName(),
                        req -> agent.execute(AgentRequest.from(parsed, req))
                    )
                    .description(agent.getDescription())
                    .inputType(AgentRequest.class)
                    .build())
                .toList();

        // 3. ChatClient 생성 (Agent Tools 등록)
        ChatClient chatClient = ChatClient.builder(chatModel)
            .defaultToolCallbacks(agentTools)
            .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
            .build();

        // 4. LLM 호출
        return chatClient.prompt()
            .system(buildSystemPrompt(parsed))
            .user(userMessage)
            .call()
            .content();
    }
}
```

### 5.4 BookAgent 개선 예시

```java
@Component
public class BookAgent implements Agent {

    @Override
    public String getDescription() {
        return """
            도서 검색 전담 Agent입니다.

            **책임:**
            - 도서 제목으로 검색
            - 저자명으로 검색
            - ISBN으로 검색
            - 도서관정보나루 API와 내부 DB 검색

            **입력:** 검색어 (도서명 또는 저자명)
            **출력:** 도서 목록 (제목, 저자, 출판사, ISBN)
            """;
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        List<BookInfo> books = searchBooks(request.query());

        return new AgentResponse(
            formatAsNaturalLanguage(books),  // 자연어 응답
            Map.of("books", books),          // 구조화된 데이터
            AgentType.BOOK
        );
    }
}
```

---

## 6. Nacos 통합 (선택 사항)

### 6.1 필요성

**분산 환경에서의 A2A**를 위해 Nacos를 활용할 수 있습니다:

- **Service Discovery**: Agent 동적 검색
- **Configuration Management**: Agent 설정 중앙화
- **Service Mesh**: Agent 간 통신 관리

### 6.2 구현 예시

```java
@NacosInjected
private NamingService namingService;

public AgentResponse invokeRemoteAgent(String agentName, AgentRequest request) {
    try {
        // Nacos에서 Agent 서비스 검색
        Instance instance = namingService.selectOneHealthyInstance("agent-" + agentName);

        // HTTP 호출
        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.postForObject(
            instance.toInetAddr() + "/api/agent/execute",
            request,
            AgentResponse.class
        );
    } catch (NacosException e) {
        throw new AgentInvocationException("Agent invocation failed", e);
    }
}
```

**주의:** 단일 애플리케이션 내에서는 Nacos 없이도 A2A 가능

---

## 7. 권장 구현 전략

### 7.1 점진적 마이그레이션

**Phase 1: 기존 패턴 유지 + Agent Tools**
- ✅ 현재 FunctionToolCallback 패턴 유지
- ✅ 기존 LibraryFunctions를 Agent로 대체
- ✅ 최소한의 변경으로 A2A 도입

**Phase 2: Advisor 체인 추가**
- ✅ ChatMemoryAdvisor로 대화 기록 유지
- ✅ LoggingAdvisor로 디버깅 개선
- ✅ CustomAdvisor로 비즈니스 로직 강화

**Phase 3: 복잡한 Orchestration**
- ✅ Multi-Agent Collaboration
- ✅ Sequential/Parallel Execution
- ✅ 선택사항: Nacos 통합

### 7.2 코드 변경 최소화

현재 `AiOrchestrationService`의 핵심 패턴을 유지:

```java
// ✅ 유지: QueryParserAgent 파싱
ParsedQuery parsed = queryParserAgent.parse(userMessage);

// ✅ 유지: FunctionToolCallback.builder() 패턴
// ✅ 변경: LibraryFunctions → Agents

// ✅ 유지: ChatClient.builder() + defaultToolCallbacks()
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultToolCallbacks(...agentTools)  // Agents로 변경
    .build();

// ✅ 유지: LLM 호출
return chatClient.prompt()
    .system(systemPrompt)
    .user(userMessage)
    .call()
    .content();
```

---

## 8. 예상 작업량

### 8.1 필수 작업 (Phase 1)

| 작업 | 파일 | 작업량 | 난이도 |
|------|------|--------|--------|
| AgentResponse DTO 생성 | AgentResponse.java | 30분 | 쉬움 |
| Agent 인터페이스 변경 | Agent.java | 1시간 | 쉬움 |
| BookAgent 개선 | BookAgent.java | 2시간 | 보통 |
| AiOrchestrationService 수정 | AiOrchestrationService.java | 2시간 | 보통 |
| 단위 테스트 작성 | AgentTest.java | 3시간 | 보통 |
| 통합 테스트 | IntegrationTest.java | 2시간 | 쉬움 |
| **합계** | | **~10시간** | **보통** |

### 8.2 선택 작업 (Phase 2~3)

| 작업 | 작업량 | 난이도 |
|------|--------|--------|
| Chat Memory 추가 | 2시간 | 쉬움 |
| Nacos 통합 | 8시간 | 어려움 |
| Multi-Step Orchestration | 6시간 | 어려움 |

---

## 9. 결론

### 9.1 요약

1. **가능성**: ✅ **매우 높음**
   - Spring AI 1.1.2로 A2A 구현 가능
   - 현재 코드가 이미 Function Calling 패턴 사용
   - Agent 인터페이스 이미 존재

2. **마이그레이션 난이도**: **보통 (Medium)**
   - 핵심 패턴 유지 가능
   - 점진적 마이그레이션 가능
   - 예상 작업량: ~10시간

3. **권장 방식**: **Coordinator Agent Pattern**
   - MainAgent (AiOrchestrationService)가 코디네이터 역할
   - 각 전문 Agent를 Function Tool로 등록
   - LLM이 자동으로 Agent 선택 및 실행

### 9.2 다음 단계

1. **설계 검토**: A2A 아키텍처 상세 설계
2. **Phase 1 구현**: Agent Tools 등록
3. **테스트**: 단위 테스트 및 통합 테스트
4. **Phase 2~3**: 점진적 기능 확장

### 9.3 참고 자료

- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Function Calling](https://docs.spring.io/spring-ai/reference/api/functions.html)
- [Spring AI Alibaba A2A Article](https://m.toutiao.com/a7591737423961358900/)
- [Multi-Agent Patterns Overview](https://blog.csdn.net/yangshangwei/article/details/156135803)

---

## 부록 A: Spring AI 버전별 기능 비교

| 기능 | Spring AI 1.1.2 | Spring AI 1.0.0 | 비고 |
|------|-----------------|-----------------|------|
| ChatClient API | ✅ | ✅ | 1.1.2 개선됨 |
| FunctionToolCallback.builder() | ✅ | ❌ | 1.1.2新增 |
| PromptChatMemoryAdvisor | ✅ | ✅ | |
| ReAct Agent Pattern | ✅ | ✅ | |
| Flow/Graph Agent | ❌ | ❌ | 1.2.0 예정 |
| Nacos Integration | 수동 | 수동 | |

**결론**: Spring AI 1.1.2는 A2A 구현에 충분한 기능을 제공함

---

## 부록 B: 현재 코드에서의 A2A 활용도

### 현재 코드의 장점

1. ✅ **이미 다중 LLM 사용**
   - QueryParserAgent (llama3-korean-blossom)
   - MainAgent (qwen2.5)

2. ✅ **이미 Agent 인터페이스 구조**
   - Agent.java 인터페이스
   - BookAgent, LibraryAgent, LoanAgent 등

3. ✅ **이미 Function Calling 패턴**
   - FunctionToolCallback.builder() 사용
   - ChatClient.defaultToolCallbacks() 사용

### 추가 필요한 부분

1. ⚠️ **Agent 간 직접 통신**
   - 현재: MainAgent가 LibraryFunctions 호출
   - 개선: Agent가 다른 Agent를 직접 호출

2. ⚠️ **Chat Memory**
   - 현재: Stateless
   - 개선: 대화 기록 유지

3. ⚠️ **Multi-Step Collaboration**
   - 현재: 단일 LLM 호출
   - 개선: 여러 Agent가 협력하여 복잡한 작업 처리

---

**문서 버전**: 1.0
**작성일**: 2025-03-05
**Spring AI 버전**: 1.1.2
