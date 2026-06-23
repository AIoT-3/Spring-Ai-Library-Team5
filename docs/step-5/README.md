# Step 5: Telegram Bot 피드백 수집 시스템

## 학습 개요

이번 Step에서는 **Telegram Bot을 통한 사용자 피드백 수집 시스템**을 구축합니다. 사용자가 검색 결과에 대해 만족하는지 아니면 불만족하는지를 피드백으로 수집하고, 이를 통해 검색 품질을 분석할 수 있는 기반을 마련합니다.

## 학습 목표

1. **Telegram Bot의 메시지 처리 흐름 이해**
   - 사용자 메시지 수신부터 응답까지의 전체 과정 이해
   - 일반 메시지와 Callback Query의 차이 파악
   - Inline Keyboard의 작동 원리 학습

2. **계층형 아키텍처 설계 경험**
   - Entity → Repository → Service → Handler → Controller 계층 구조 체험
   - 각 계층의 역할과 책임 명확히 이해

3. **데이터 설계와 피드백 모델링**
   - 사용자 피드백을 어떻게 저장할지 설계
   - JPA를 활용한 데이터베이스 스키마 설계

4. **REST API와 통계 분석**
   - 관리자용 API 설계
   - 피드백 데이터를 통계로 변환하는 방법 학습

---

## 전체 시스템 아키텍처

### 1. 시스템 구조도

```
┌─────────────────────────────────────────────────────────────────┐
│                         Telegram 서버                           │
│  사용자 메시지를 수신하고, 우리 Bot 서버로 전달하는 역할         │
└───────────────────────────────┬─────────────────────────────┘
                                │ Long Polling
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      LibraryTelegramBot                        │
│  • onUpdateReceived(): 메시지 수신                             │
│  • handleCommand(): /start, /help 등 명령어 처리               │
│  • handleSearch(): 도서 검색 처리                               │
│  • CallbackQueryHandler: 버튼 클릭 처리                         │
└───────┬───────────────────────────────────────┬─────────────────┘
        │                                   │
        ▼                                   ▼
┌─────────────────────────┐     ┌──────────────────────────┐
│  BookSearchService      │     │ CallbackQueryHandler    │
│  • 도서 검색 수행         │     │  • 버튼 클릭 해석       │
│  • 결과 포맷팅           │     │  • 피드백 저장 요청     │
└──────────┬──────────────┘     └──────────┬───────────────┘
           │                               │
           ▼                               ▼
┌─────────────────────────────────────────────────────────┐
│                    사용자 전달                            │
│  • 검색 결과 메시지                                         │
│  • Inline Keyboard (버튼)                                 │
└─────────────────────────────────────────────────────────┘
                                │
                               ▼
┌─────────────────────────────────────────────────────────┐
│                사용자가 버튼 클릭                         │
│  [👍 좋았음] 또는 [👎 별로였음]                          │
└───────────────────────────────┬─────────────────────────┘
                                │ Callback Query
                                ▼
┌─────────────────────────────────────────────────────────┐
│            CallbackQueryHandler 처리                       │
│  1. Callback 데이터 파싱 (fb:bookId:type)                │
│  2. 중복 체크                                               │
│  3. FeedbackService 호출                                   │
│  4. 감사 메시지 전송                                         │
└───────────────────────────────┬─────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────┐
│              FeedbackService (비즈니스 로직)              │
│  • 피드백 저장                                             │
│  • 통계 계산 (긍정/부정 비율, 점수)                         │
│  • 사용자별/검색어별/도서별 조회                             │
└───────┬───────────────────────────┬───────────────────────┘
        │                           │
        ▼                           ▼
┌─────────────────────┐   ┌──────────────────────┐
│ SearchFeedbackRepo  │   │ FeedbackAdminController│
│ (데이터 영속성)      │   │ (REST API)            │
└─────────────────────┘   └──────────────────────┘
```

### 2. 데이터 흐름도

```
[검색 요청]
    │
    ▼
[Telegram 서버] → [LibraryTelegramBot.onUpdateReceived()]
    │
    ├─ 일반 메시지?
    │   └─→ [Command 분기] → /start, /help, /stats 등
    │
    └─ Callback Query?
        └─→ [CallbackQueryHandler.handleCallback()]
            │
            ├─ [데이터 파싱] → fb:12345:GOOD
            │
            ├─ [중복 체크] → findByChatIdAndQueryAndBookId()
            │
            ├─ [피드백 저장] → FeedbackService.recordFeedback()
            │
            └─ [감사 메시지] → AnswerCallbackQuery()
```

---

## 핵심 컴포넌트 상세 설명

### 1. Telegram Bot 작동 원리

#### Long Polling 방식

Telegram Bot은 **Long Polling** 방식으로 동작합니다.

```
[일반적인 Polling]
Client → 서버에 요청: "메시지 왔어?"
서버 → 응답: "없음" (연결 종료)
Client → 1초 후 다시 요청
→ 불필요한 네트워크 오버헤드

[Long Polling]
Client → 서버에 요청: "메시지 왔어?"
서버 → 응답: "없음" (연결 유지, 대기)
     → 메시지 도착 시 즉시 응답
→ 효율적인 실시간 통신
```

**LibraryTelegramBot 설정** (`application.properties`)
```properties
# Telegram Bot Token
spring.ai.genai.api-key=${TELEGRAM_BOT_TOKEN}

# Long Polling 대기 시간 (초)
telegram.bot.poll-interval=5
```

#### 메시지 처리 우선순위

```
[Update 수신]
    │
    ▼
hasCallbackQuery()? ───YES──→ [CallbackQueryHandler 처리]
    │                              (버튼 클릭)
    NO
    │
    ▼
hasMessage() && hasText()? ─YES──→ [Command 분기]
    │                              /start, /help 등
    ▼
[검색 처리]
```

**왜 우선순위가 중요한가?**
- Callback Query는 **즉시 응답**해야 함 (사용자 경험)
- 일반 메시지는 **조금 늦게 처리**되어도 무방

### 2. Inline Keyboard와 Callback Query

#### Inline Keyboard 구조

```
┌─────────────────────────────────────────┐
│  SendMessage                            │
│  • text: 메시지 내용                     │
│  • replyMarkup: InlineKeyboardMarkup     │
│    └─ keyboard: List<List<Button>>      │
└─────────────────────────────────────────┘

InlineKeyboardMarkup
    └─ List<List<InlineKeyboardButton>>
        ├─ [Row 1] → [Button1, Button2]
        └─ [Row 2] → [Button3, Button4]

InlineKeyboardButton
    • text: 버튼 텍스트 (예: "👍 좋았음")
    • callbackData: 클릭 시 전달될 데이터
```

#### Callback 데이터 포맷

```
포맷: fb:{bookId}:{type}
예: fb:12345:GOOD

구조:
fb      → 피드백 타입 식별자
12345   → 도서 ID
GOOD    → 피드백 타입 (GOOD, BAD)
```

**제약사항:**
- `callback_data` 최대 **64 bytes** 제한
- 검색어가 길어서 포함 불가
- 해결책: `CallbackQueryHandler.recentQueries` Map에 저장

### 3. 피드백 데이터 모델

#### Entity 설계

```
SearchFeedback
├── id (PK)          : BIGINT          → 자동 생성
├── chatId          : BIGINT NOT NULL  → Telegram 사용자 ID
├── query           : VARCHAR(500)     → 검색어
├── bookId          : BIGINT          → 피드백 대상 도서 ID
├── type            : VARCHAR(20)     → GOOD, BAD
└── createdAt       : TIMESTAMP       → 생성 시간
```

**인덱스 설계:**
```sql
CREATE INDEX idx_search_feedbacks_chat_id ON search_feedbacks(chat_id);
CREATE INDEX idx_search_feedbacks_created_at ON search_feedbacks(created_at);
CREATE INDEX idx_search_feedbacks_query_book ON search_feedbacks(query, book_id);
CREATE INDEX idx_search_feedbacks_book_id ON search_feedbacks(book_id);
```

**왜 이렇게 인덱스를 걸까?**
1. `chat_id` → 사용자별 피드백 조회 (/mystats)
2. `created_at` → 최근 피드백 조회 (/recent?days=7)
3. `(query, book_id)` → 중복 체크
4. `book_id` → 도서별 통계 조회

---

## 환경 설정

### 1. Telegram Bot 설정

**telegram.properties**
```properties
# Telegram Bot Token (환경 변수로 관리 권장)
telegram.bot.token=${TELEGRAM_BOT_TOKEN}
```

**토큰 발급 방법:**
1. Telegram에서 `@BotFather` 검색
2. `/newbot` 명령어 입력
3. Bot 이름 입력 (예: `ai_library_bot`)
4. 생성된 Token 복사
5. 환경 변수 설정: `export TELEGRAM_BOT_TOKEN=your_token`

### 2. 데이터베이스 DDL

**PostgreSQL**
```sql
-- 테이블 생성
CREATE TABLE search_feedbacks (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    query VARCHAR(500) NOT NULL,
    book_id BIGINT,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_search_feedbacks_chat_id ON search_feedbacks(chat_id);
CREATE INDEX idx_search_feedbacks_created_at ON search_feedbacks(created_at);
CREATE INDEX idx_search_feedbacks_query_book ON search_feedbacks(query, book_id);
CREATE INDEX idx_search_feedbacks_book_id ON search_feedbacks(book_id);

-- 시퀀스 생성 (수동 권한 부여 필요)
CREATE SEQUENCE search_feedbacks_sequence;
```

**왜 Sequence를 별도 만드는가?**
- PostgreSQL의 `BIGSERIAL`이 자동 생성하는 시퀀스는 이름 규칙이 다름
- `search_feedbacks_id_seq` (자동 생성)
- 우리가 원하는 이름: `search_feedbacks_sequence`
- 이식성을 위해 명시적 생성

---

## 피드백 수집 흐름 상세 분석

### 시나리오 1: 검색 후 피드백

```
[Step 1] 사용자 검색
User: "해리포터"
  ↓
[Telegram 서버]
  → LibraryTelegramBot.onUpdateReceived()
  → handleSearch("해리포터")
  → BookSearchService.searchBooks()
  → 결과 포맷팅
  → sendSearchResult() + Inline Keyboard

[Step 2] 사용자에게 결과 표시
Bot: 📚 "해리포터" 검색 결과

     1. 해리포터와 마법사의 돌
        [👍 좋았음] [👎 별로였음]

     2. 해리포터와 비밀의 방
        [👍 좋았음] [👎 별로였음]

[Step 3] recentQueries Map에 저장
CallbackQueryHandler.setRecentQuery(chatId, "해리포터")
  → 나중에 피드백 저장 때 검색어 사용
```

### 시나리오 2: 피드백 저장

```
[Step 1] 버튼 클릭
User: [👍 좋았음] 클릭
  ↓
[Telegram 서버]
  → Callback Query 전달
  → callbackData: "fb:12345:GOOD"

[Step 2] Callback Query Handler 처리
CallbackQueryHandler.handleCallback()
  ↓
  [1] 데이터 파싱
      bookId = 12345
      type = GOOD
  ↓
  [2] 검색어 복원
      query = recentQueries.get(chatId)  // "해리포터"
  ↓
  [3] 중복 체크
      feedbackService.hasExistingFeedback(chatId, "해리포터", 12345)
      → 이미 있으면 "⚠️ 이미 피드백을 남기셨습니다."
      → 없으면 계속 진행
  ↓
  [4] 피드백 저장
      FeedbackService.recordFeedback(chatId, request)
      → SearchFeedbackEntity 생성
      → Repository.save()
  ↓
  [5] 감사 메시지 전송
      AnswerCallbackQuery
      text: "✅ 피드백이 저장되었습니다!"
```

---

## REST API 설계

### 관리자 API 엔드포인트

| 엔드포인트 | 메서드 | 설명 | 응답 |
|-----------|------|------|------|
| `/api/admin/feedback/book/{bookId}/stats` | GET | 도서별 피드백 통계 | FeedbackStats |
| `/api/admin/feedback/stats?query={query}` | GET | 검색어별 피드백 통계 | FeedbackStats |
| `/api/admin/feedback/recent?days={days}` | GET | 최근 N일 피드백 목록 | List\<SearchFeedback\> |
| `/api/admin/feedback/user/{chatId}` | GET | 사용자별 피드백 목록 | List\<SearchFeedback\> |
| `/api/admin/feedback/export/csv` | GET | 전체 피드백 CSV 다운로드 | text/csv |

### FeedbackStats DTO 구조

```java
{
  "goodCount": 15,        // 긍정 피드백 수
  "badCount": 2,          // 부정 피드백 수
  "totalCount": 17,       // 전체 피드백 수
  "goodRatio": 0.882,     // 긍정 비율 (15/17)
  "feedbackScore": 0.764   // 피드백 점수 ((15-2)/17)
}
```

**점수 계산 공식:**
```
feedbackScore = (goodCount - badCount) / totalCount

예: (15 - 2) / 17 = 0.764

범위: -1.0 (전부 부정) ~ +1.0 (전체 긍정)
```

---

## 구현 완료 현황

### Phase 1: 데이터 계층 ✅

| 컴포넌트 | 파일명 | 설명 |
|----------|--------|------|
| Entity | `SearchFeedback.java` | JPA Entity, DB 매핑 |
| Repository | `SearchFeedbackRepository.java` | 데이터 접근 인터페이스 |
| Enum | `FeedbackType.java` | GOOD(+1), BAD(-1) |

### Phase 2: 비즈니스 계층 ✅

| 컴포넌트 | 파일명 | 설명 |
|----------|--------|------|
| Service | `FeedbackService.java` | 인터페이스 |
| Impl | `FeedbackServiceImpl.java` | 구현체 |
| DTO | `FeedbackRequest.java` | 요청 DTO (record) |
| DTO | `FeedbackStats.java` | 통계 DTO (record) |

### Phase 3: UI 계층 ✅

| 컴포넌트 | 파일명 | 설명 |
|----------|--------|------|
| Factory | `TelegramKeyboardFactory.java` | Inline Keyboard 생성 |
| Handler | `CallbackQueryHandler.java` | Callback 처리 |
| Bot | `LibraryTelegramBot.java` | Telegram Bot 메인 클래스 |

### Phase 4: 관리자 API ✅

| 컴포넌트 | 파일명 | 설명 |
|----------|--------|------|
| Controller | `FeedbackAdminController.java` | REST API |
| Test | `FeedbackAdminControllerTest.java` | API 테스트 |

### 추가 구현 기능 ✅

| 기능 | 파일명 | 설명 |
|------|--------|------|
| 중복 방지 | `SearchFeedbackRepository.java` | `findByChatIdAndQueryAndBookId()` |
| 익명화 유틸리티 | `PrivacyUtil.java` | `hashChatId()`, `maskChatId()` |
| CSV 내보내기 | `CsvExportService.java` | `/export/csv` 엔드포인트 |

---

## 제약사항과 해결 방안

### 1. Telegram API 제약: callback_data 64 bytes

**문제:**
- 검색어가 길면 callback_data에 포함 불가
- 예: `fb:해리포터와 마법사의 돌:12345:GOOD` (초과)

**해결:**
- `CallbackQueryHandler.recentQueries` Map에 검색어 저장
- `callbackData`에는 `fb:bookId:type`만 포함
- 피드백 저장 시 Map에서 검색어 조회

### 2. Sequence 권한 문제

**문제:**
```
ERROR: permission denied for sequence search_feedbacks_sequence
```

**해결:**
```sql
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO nhn_academy;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO nhn_academy;
```

### 3. 순환 의존성

**문제:**
- `CallbackQueryHandler` → `LibraryTelegramBot` → `CallbackQueryHandler`

**해결:**
```java
public CallbackQueryHandler(
    FeedbackService feedbackService,
    @Lazy LibraryTelegramBot libraryTelegramBot  // Lazy 로딩
) {
    this.feedbackService = feedbackService;
    this.libraryTelegramBot = libraryTelegramBot;
}
```

---

## 다음 단계

### Step 5 완료 항목 ✅

1. **Telegram Bot 기본 설정** - Bot 토큰 발급, Long Polling 설정
2. **RAG 검색 연동** - AI 추천 사유 생성
3. **피드백 수집 시스템** - Inline Keyboard, Callback 처리, DB 저장
4. **관리자 API** - 통계 조회, CSV 내보내기

### Step 6: 개인화 추천 시스템

**Step 5에서 수집한 피드백 데이터를 활용하여:**
1. 도서별 피드백 점수 계산
2. 사용자 선호 벡터 생성 (좋아한 도서들의 임베딩 평균)
3. RRF 알고리즘에 피드백 가중치 반영
4. 개인화된 검색 결과 제공

---

## 참고 자료

- [Telegram Bot API Documentation](https://core.telegram.org/bots/api)
- [Spring Boot REST API Guide](https://spring.io/guides/gs/rest-service/)
- [Feedback Systems in AI](https://arxiv.org/abs/2201.02142)
- [.junie/guidelines.md](../../.junie/guidelines.md)
