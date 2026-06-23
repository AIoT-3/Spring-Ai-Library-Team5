# 04. 현재 프로젝트 리뷰 요약 시스템 보강

이 문서는 강의자료 `docs/step-4/04.review-summarization.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

현재 템플릿에는 리뷰 표시 영역이 일부 있지만, Java 도메인과 서비스는 아직 없다. 따라서 먼저 리뷰 저장과 통계를 안정화한 뒤 AI 요약을 붙이는 순서가 좋다.

## 구현 순서

```text
1. BookReview 엔티티와 Repository
2. BookReviewSummary 엔티티와 Repository
3. 리뷰 작성 API/Controller
4. 리뷰 통계 동기 업데이트
5. 리뷰 요약 필요 여부 표시(dirty flag)
6. 비동기 요약 처리
7. 상세 화면에 요약 표시
```

AI 요약부터 만들면 리뷰 저장/통계 문제와 LLM 문제가 섞인다.

## 엔티티 제안

```text
BookReview
  id
  book_id
  rating
  content
  created_at

BookReviewSummary
  book_id
  review_count
  average_rating
  rating_1_count ... rating_5_count
  review_summary
  summary_dirty
  summarized_at
  version
```

`BookReviewSummary`는 `Book`과 1:1 관계로 둘 수 있지만, 처음에는 Repository에서 `bookId`로 명시 조회하는 편이 단순하다.

## 요약 트리거

리뷰가 하나 생길 때마다 LLM을 호출하지 않는다. 비용이 너무 커진다.

권장 기준:

```properties
app.review.min-count-for-summary=5
app.ai.max-review-summary-length=100
```

```text
리뷰 수가 5개 미만:
  통계만 업데이트, AI 요약 없음

리뷰 수가 5개 이상:
  summary_dirty=true
  비동기 요약 대상
```

## 비동기 처리

현재 프로젝트에는 RabbitMQ 설정이 있다.

```properties
rabbitmq.queue.review-summary=nhnacademy-library-review
spring.rabbitmq.listener.simple.concurrency=3
spring.rabbitmq.listener.simple.max-concurrency=5
```

처음 구현은 `@Async`로 시작할 수 있지만, 중복 실행과 서버 재시작 손실을 피하려면 RabbitMQ 기반으로 가는 편이 낫다. 다만 Step 4 문서에서는 아래 경계를 유지한다.

```text
ReviewService:
  리뷰 저장과 통계 업데이트

ReviewSummaryRequestPublisher:
  요약 요청 메시지 발행

ReviewSummaryConsumer:
  메시지를 받아 AI 요약 실행

ReviewSummaryService:
  실제 LLM 호출과 summary 저장
```

## 중복 처리

같은 책에 대해 요약 요청이 여러 번 들어올 수 있다.

대응:

```text
1. BookReviewSummary.summary_dirty로 요약 필요 여부 판단
2. processing 플래그나 version으로 동시 처리 방어
3. 요약 완료 후 summary_dirty=false
4. 실패 시 dirty를 유지해서 재시도 가능하게 함
```

## 요약 프롬프트

리뷰 요약은 짧고 구조화된 결과가 좋다.

```text
다음 리뷰들을 바탕으로 이 책에 대한 독자 반응을 한국어로 요약하세요.

규칙:
- 3문장 이내
- 장점과 주의점을 균형 있게 작성
- 리뷰에 없는 내용을 만들지 않음
- 100자 이내
```

## 완료 기준

```text
1. 리뷰 저장과 통계 업데이트가 LLM 없이 동작한다.
2. 리뷰 5개 이상일 때만 요약 대상이 된다.
3. 요약은 비동기로 처리된다.
4. 실패해도 재시도 가능한 상태가 남는다.
5. 상세 화면에서 reviewSummary가 없을 때도 깨지지 않는다.
```
