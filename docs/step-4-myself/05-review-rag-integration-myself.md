# 05. 현재 프로젝트 리뷰 정보를 RAG에 반영하기

이 문서는 강의자료 `docs/step-4/05.review-rag-integration.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

리뷰 요약은 검색 품질 자체를 바꾸는 정보가 아니라, RAG 답변의 근거를 풍부하게 만드는 정보다. 먼저 검색 결과가 안정적으로 나온 뒤 컨텍스트에 붙인다.

## 목표 흐름

```text
HYBRID 검색 결과
  -> BookReviewSummary LEFT JOIN 또는 별도 조회
  -> RAG 컨텍스트에 평점/리뷰 수/요약 추가
  -> LLM이 추천 이유에 독자 반응 반영
```

리뷰가 없는 도서도 검색 결과에서 빠지면 안 된다. 따라서 JOIN을 한다면 `LEFT JOIN`이 맞다.

## DTO 확장

현재 `BookSearchResponse`에는 도서 기본 정보만 있다. Step 2/3에서 `similarity`, `rrfScore`를 추가하고, Step 4에서는 리뷰 필드를 추가한다.

```text
averageRating
reviewCount
reviewSummary
```

기존 생성자는 유지한다. QueryDSL projection이 여러 곳에서 쓰이므로 생성자 순서를 바꾸면 런타임 오류가 날 수 있다.

권장:

```text
1. 기존 생성자 유지
2. 점수 포함 생성자 추가
3. 리뷰 포함 생성자 추가
4. 필요하면 withReviewSummary(...) 복사 메서드 사용
```

## category 유지

강의자료 예시에 `volumeTitle`이 있더라도 현재 프로젝트는 `category` 기준이다. 리뷰 정보를 붙일 때도 기존 `category` projection을 유지해야 한다.

```text
BookSearchResponse:
  id
  isbn
  title
  authorName
  publisherName
  price
  editionPublishDate
  imageUrl
  bookContent
  category
  similarity
  rrfScore
  averageRating
  reviewCount
  reviewSummary
```

## N+1 방지

RAG 후보가 5개라면 별도 조회도 큰 문제는 아니지만, 검색 결과 목록 전체에 리뷰를 붙이면 N+1이 생길 수 있다.

선택지는 두 가지다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| Repository LEFT JOIN | 한 번에 조회 | QueryDSL projection 복잡 |
| RAG 후보 id만 별도 batch 조회 | 구현 단순 | 조립 코드 필요 |

처음에는 RAG 후보 topK의 id 목록으로 `BookReviewSummary`를 한 번에 조회해서 Map으로 조립하는 방식이 안전하다.

```text
1. HYBRID 결과 top5 선택
2. ids = [1, 2, 3, 4, 5]
3. summaryRepository.findByBookIdIn(ids)
4. bookId -> summary Map
5. RagContextBuilder에서 리뷰 정보 포함
```

## 컨텍스트 예시

```text
### 도서 1
- ID: 101
- 제목: 주식 입문
- 저자: 김철수
- 분류: 327.8
- 소개: 주식 투자의 기본 개념을 설명...
- 독자 평점: 4.8/5.0
- 리뷰 수: 127
- 리뷰 요약: 초보자가 이해하기 쉽고 실전 예제가 많다는 평가가 많습니다.
```

리뷰 요약은 `app.ai.max-review-summary-length=100` 기준으로 자른다.

## 프롬프트 규칙 추가

```text
리뷰 정보가 있으면 추천 이유에 반영한다.
리뷰 정보가 없는 책은 리뷰가 없다고 단정하지 말고, 리뷰 근거를 언급하지 않는다.
평점이 높아도 질문과 관련성이 낮으면 추천하지 않는다.
```

리뷰는 보조 근거다. 검색 관련성보다 평점을 우선하면 사용자의 질문과 다른 인기 도서를 추천할 수 있다.

## 완료 기준

```text
1. 리뷰 없는 도서도 RAG 후보에서 빠지지 않는다.
2. 리뷰 요약은 topK 후보에만 붙인다.
3. averageRating, reviewCount, reviewSummary는 null 가능하다.
4. 컨텍스트에는 category와 리뷰 정보가 함께 들어간다.
5. LLM은 리뷰 정보가 있는 경우에만 독자 반응을 언급한다.
```
