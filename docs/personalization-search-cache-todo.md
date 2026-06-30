# 개인화 검색 가중치 및 캐싱 연결 TODO

## 목표

현재 검색 흐름은 `BookSearchService`가 키워드/벡터/하이브리드 후보를 만들고, `PersonalizationService`가 별도로 결과 리스트를 받아 개인화 점수를 더해 재정렬할 수 있는 상태다. 다음 작업의 목표는 개인화가 실제 검색 결과 순위에 영향을 주도록 연결하고, 개인화가 적용된 검색 결과를 임베딩 기반 캐시에 저장해서 비슷한 요청을 재사용하는 것이다.

## 현재 코드 기준 흐름

- 검색 진입점: `BookSearchController.index()`
- 일반 검색 분기: `BookSearchService.searchBooks()`
- 하이브리드 후보 생성: `BookSearchService.searchHybridCandidates()`
- 개인화 점수 계산: `PersonalizationService.personalizedSearch()`
- 사용자 선호 벡터 계산: `PersonalizationService.calculateUserPreferenceVector()`
- RAG 의미 캐시 참고 구현: `SematicRagCacheService`
- RAG 캐시 엔트리 참고 DTO: `SemanticRagCacheEntry`

## 설계 방향

개인화는 DB 검색 조건에 바로 섞기보다, 기존 검색 후보를 충분히 가져온 뒤 애플리케이션 레벨에서 점수를 더해 재정렬한다. 이미 하이브리드 검색이 RRF 점수를 만들고 있으므로, 최종 점수는 아래 형태를 기본값으로 둔다.

```text
finalScore = baseScore + personalizationSimilarity * personalizationWeight
```

권장 기본값:

- `baseScore`: `rrfScore`가 있으면 `rrfScore`, 없으면 검색 타입별 기본 점수
- `personalizationSimilarity`: 사용자 선호 벡터와 책 임베딩의 cosine similarity
- `personalizationWeight`: 설정값으로 분리, 기본 `0.3`

캐시는 단순 키워드 문자열 캐시가 아니라 요청 임베딩과 사용자 선호 벡터를 함께 고려해야 한다. 같은 검색어라도 사용자 선호가 다르면 결과가 달라질 수 있기 때문이다.

## TODO 1. 요청에 사용자 식별자 연결

- [ ] `BookSearchRequest`에 `userId`를 추가할지, 컨트롤러에서 별도 파라미터로 받을지 결정한다.
- [ ] 현재 화면 기반이면 임시로 `@RequestParam(required = false) String userId`를 `BookSearchController.index()`에 추가한다.
- [ ] 로그인/세션이 붙어 있다면 컨트롤러 파라미터가 아니라 인증 컨텍스트에서 `userId`를 얻도록 한다.
- [ ] `userId`가 없으면 개인화와 개인화 캐시는 모두 스킵한다.

주의:

- 캐시 키나 엔트리에 원문 `userId`를 그대로 넣지 말고 해시 또는 별도 namespace 처리를 한다.
- 테스트/데모용 `userId` 파라미터는 운영 전 제거하거나 인증 기반으로 교체한다.

## TODO 2. 개인화 적용 지점 결정

권장 연결 위치는 `BookSearchService.searchHybridCandidates()`의 RRF merge 직후다.

현재 흐름:

```java
List<BookSearchResponse> rrf = rrfMerge(keywordResults, vectorResult, RRF_K);
List<BookSearchResponse> content = pageContent(rrf, pageable);
```

변경 방향:

```java
List<BookSearchResponse> rrf = rrfMerge(keywordResults, vectorResult, RRF_K);
List<BookSearchResponse> ranked = personalizationService.personalizedSearchIfPossible(rrf, userId);
List<BookSearchResponse> content = pageContent(ranked, pageable);
```

작업:

- [ ] `BookSearchService`에 `PersonalizationService` 의존성을 추가한다.
- [ ] `searchHybridCandidates()`가 개인화 여부를 알 수 있도록 `userId` 전달 경로를 만든다.
- [ ] 개인화는 페이징 전에 적용한다. 페이징 후 적용하면 현재 페이지 안에서만 순서가 바뀌어 전체 순위가 틀어진다.
- [ ] 후보 크기는 현재 `MIN_HYBRID_CANDIDATES = 100`을 유지하되, 개인화 효과가 약하면 설정값으로 분리한다.

## TODO 3. 개인화 점수 계산 안전 처리

현재 `PersonalizationService.personalizedSearch()`는 `result.getEmbedding()`이 null이거나 `result.getRrfScore()`가 null이면 예외 가능성이 있다.

작업:

- [ ] `bookEmbedding == null || bookEmbedding.length == 0`이면 개인화 점수 계산을 건너뛴다.
- [ ] `rrfScore == null`이면 `0.0` 또는 검색 타입별 기본값을 사용한다.
- [ ] 사용자 선호 벡터와 책 임베딩 차원이 다르면 해당 책은 개인화 점수 없이 유지한다.
- [ ] `PERSONALIZATION_WEIGHT`를 `application.properties` 설정으로 이동한다.

권장 설정:

```properties
app.personalization.enabled=true
app.personalization.weight=0.3
app.personalization.min-history-count=3
app.personalization.recent-history-limit=20
```

## TODO 4. 개인화 검색 캐시 DTO 추가

RAG 캐시의 `SemanticRagCacheEntry`를 참고해서 개인화 검색 전용 엔트리를 만든다. RAG 추천 결과와 일반 검색 결과는 목적이 다르므로 DTO를 분리하는 편이 안전하다.

예상 위치:

```text
src/main/java/com/nhnacademy/ailibraryteam5/core/book/cache/PersonalizedSearchCacheEntry.java
```

필드 초안:

```java
public record PersonalizedSearchCacheEntry(
        String normalizedKeyword,
        String isbn,
        String userKey,
        float[] queryEmbedding,
        float[] preferenceVector,
        List<Long> rankedBookIds,
        long createdAt
) {
}
```

권장:

- 캐시에는 `BookSearchResponse` 전체보다 `rankedBookIds`를 저장한다.
- 캐시 히트 시 `bookRepository.findAllById()`로 최신 책 데이터를 다시 hydrate한다.
- 검색 결과 카드에 필요한 `similarity`, `rrfScore`, `personalizationScore`, `score`를 보여줘야 하면 별도 score DTO를 같이 저장한다.

예시:

```java
public record CachedBookScore(
        Long bookId,
        Double similarity,
        Double rrfScore,
        Double personalizationScore,
        Double finalScore
) {
}
```

## TODO 5. 개인화 의미 캐시 서비스 추가

RAG의 `SematicRagCacheService`와 비슷한 구조로 만든다. 다만 매칭 기준은 query embedding만 보면 부족하고, preference vector도 같이 봐야 한다.

예상 위치:

```text
src/main/java/com/nhnacademy/ailibraryteam5/core/book/cache/PersonalizedSearchCacheService.java
```

캐시 히트 기준 초안:

```text
querySimilarity >= queryThreshold
AND preferenceSimilarity >= preferenceThreshold
AND isbn namespace 동일
AND userKey 동일 또는 user bucket 동일
```

권장 기본값:

```properties
app.personalization.cache.enabled=true
app.personalization.cache.ttl-minutes=30
app.personalization.cache.query-similarity-threshold=0.85
app.personalization.cache.preference-similarity-threshold=0.90
app.personalization.cache.max-candidates-to-compare=200
app.personalization.cache.max-index-size=2000
app.personalization.cache.version=personalized-search:v1
```

작업:

- [ ] `findSimilar(request, queryEmbedding, preferenceVector, userKey)` 구현
- [ ] `save(request, queryEmbedding, preferenceVector, rankedResults, userKey)` 구현
- [ ] Redis index key는 `version:index:isbn:{isbn}:user:{userKey}` 형태로 시작한다.
- [ ] entry key는 UUID를 붙여 충돌을 피한다.
- [ ] TTL과 index trim을 적용한다.

주의:

- 사용자별 캐시는 메모리 사용량이 커질 수 있다.
- 처음에는 userKey 단위로 엄격하게 나누고, 성능 문제가 확인되면 선호 벡터 bucket 공유를 검토한다.

## TODO 6. 검색 흐름에 캐시 연결

권장 순서:

1. `userId`가 없으면 기존 검색 실행
2. `queryEmbedding` 생성
3. `preferenceVector` 계산
4. 개인화 캐시 조회
5. 캐시 히트면 `rankedBookIds` hydrate 후 Page 반환
6. 캐시 미스면 기존 하이브리드 검색 실행
7. 개인화 점수 적용 및 재정렬
8. 개인화된 순위를 캐시에 저장
9. 페이징 후 반환

중요:

- query embedding은 하이브리드 검색의 벡터 검색에도 필요하다. 중복 임베딩 호출을 줄이려면 `searchHybridCandidates()` 내부에서 한 번 만든 embedding을 캐시 조회와 벡터 검색에 같이 쓰도록 구조를 정리한다.
- 캐시 저장은 실패해도 검색 응답을 실패시키면 안 된다. RAG 캐시처럼 `try/catch`로 감싼다.
- 캐시 히트 결과도 `PageImpl`의 `totalElements`를 일관되게 넣어야 한다.

## TODO 7. 히스토리 저장 연결 확인

개인화 품질은 `book_view_history` 데이터에 의존한다.

작업:

- [ ] `/books/{id}` 상세 진입 시 `BookViewHistory` 저장 로직이 있는지 확인한다.
- [ ] 없으면 `BookSearchController.detail()` 또는 별도 `BookViewHistoryService`에서 저장한다.
- [ ] 같은 사용자가 같은 책을 반복 조회할 때 정책을 정한다.
- [ ] 최근 N개 기준이면 중복 포함 여부를 결정한다.

권장:

- 상세 조회 이벤트 저장은 컨트롤러에 직접 넣기보다 `BookViewHistoryService.recordView(userId, bookId)`로 분리한다.
- 중복 조회를 모두 저장할지, 같은 책은 최신 1건만 유지할지는 추천 품질을 보고 결정한다.

## TODO 8. 화면 표시

현재 `BookSearchResponse`에는 `personalizationScore`와 `score`가 있다. 개인화가 켜진 검색에서만 배지를 보여주면 디버깅이 쉽다.

작업:

- [ ] `index.html`에 `book.personalizationScore != null`일 때 개인화 점수 배지를 추가한다.
- [ ] 개발 중에는 최종 점수 `score`도 표시한다.
- [ ] 운영 UI에서는 점수 노출이 불필요하면 숨긴다.

## TODO 9. 테스트 항목

- [ ] `userId`가 없으면 기존 검색 순위와 동일해야 한다.
- [ ] 히스토리가 3개 미만이면 개인화가 스킵되어야 한다.
- [ ] 책 임베딩이 null인 결과가 있어도 검색이 실패하면 안 된다.
- [ ] 같은 검색어, 같은 userKey, 비슷한 preference vector면 캐시 히트해야 한다.
- [ ] 같은 검색어라도 다른 userKey면 캐시가 섞이면 안 된다.
- [ ] 캐시 서버 장애 시 검색 응답은 정상 반환되어야 한다.
- [ ] 페이징 전에 개인화 정렬이 적용되어야 한다.

## 작업 순서 제안

1. `PersonalizationService` null-safe 처리 및 설정값 분리
2. `BookSearchService.searchHybridCandidates()`에 개인화 재정렬 연결
3. 상세 조회 히스토리 저장 연결
4. 개인화 점수 UI 배지 추가
5. 개인화 캐시 DTO와 서비스 추가
6. 캐시 조회/저장을 하이브리드 검색 흐름에 연결
7. 임베딩 중복 호출 제거
8. 테스트 추가

## 완료 기준

- 개인화 가능한 사용자는 같은 검색어에서도 조회 이력에 따라 검색 결과 순서가 달라진다.
- 개인화 불가능한 사용자는 기존 검색 결과와 동일하게 동작한다.
- 개인화 캐시 히트 시 DB/임베딩/재정렬 비용이 줄어든다.
- 캐시 장애나 개인화 계산 실패가 전체 검색 실패로 전파되지 않는다.
