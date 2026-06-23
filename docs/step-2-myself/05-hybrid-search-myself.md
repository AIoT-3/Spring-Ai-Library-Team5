# 05. 현재 프로젝트 하이브리드 검색 구현 보강

이 문서는 강의자료 `docs/step-2/05.hybrid-search.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 문서는 RRF 개념과 병합 흐름을 설명한다. 실제 구현에서는 페이징, 중복 제거, DTO 점수 보존, 검색 타입 분기 위치를 더 명확히 해야 한다.

## 목표

하이브리드 검색은 키워드 검색과 벡터 검색을 둘 다 실행한 뒤, 각각의 순위를 RRF로 합쳐 최종 순서를 만든다.

```text
keyword 검색 결과 topN
vector 검색 결과 topN
  -> book id 기준으로 병합
  -> RRF 점수 계산
  -> rrfScore desc 정렬
  -> 화면에 반환
```

중요한 점은 점수를 직접 더하지 않는 것이다. 키워드 검색 점수와 벡터 similarity는 scale이 다르기 때문에 그대로 더하면 한쪽이 과하게 유리해진다.

## Service 책임

`HYBRID` 분기는 `BookSearchService`에서 처리하는 것이 자연스럽다.

```text
BookSearchService.searchBooks(...)
  KEYWORD -> repository.search(...)
  VECTOR  -> embeddingService.embed(...) + repository.vectorSearch(...)
  HYBRID  -> keyword topN + vector topN + rrf merge
```

Repository가 RRF를 알 필요는 없다. Repository는 각 검색 방식의 후보를 가져오고, Service가 후보 병합 정책을 담당한다.

## 후보 수와 페이징

하이브리드 검색에서 가장 조심할 부분은 페이징이다.

예를 들어 화면 page size가 10이라고 해서 키워드 10개, 벡터 10개만 가져와 병합하면 2페이지 이후 품질이 흔들린다. RRF는 후보군 전체의 순위가 중요하기 때문이다.

처음 구현은 아래처럼 단순하게 간다.

```text
1. HYBRID 검색에서는 각 검색에서 top 50을 가져온다.
2. RRF로 병합한다.
3. 병합 결과를 메모리에서 pageable offset/limit으로 자른다.
```

후보 수는 설정값으로 빼는 편이 좋다.

```properties
app.ai.max-candidates=50
app.ai.rrf-k=60
```

현재 프로젝트에는 이미 `app.ai.rrf-k=60` 설정이 있다. 이 값을 사용하면 문서와 코드의 기준이 맞는다.

## RRF 계산

RRF 공식은 아래와 같다.

```text
score = 1 / (k + rank)
```

rank는 1부터 시작한다.

```java
private double rrfScore(int rank, int k) {
    return 1.0 / (k + rank);
}
```

키워드 결과와 벡터 결과에 같은 책이 있으면 점수를 누적한다.

```text
도서 A:
  keyword rank 1 -> 1 / 61
  vector rank 3  -> 1 / 63
  total          -> 두 점수 합
```

## 병합 자료구조

도서 ID를 기준으로 중복 제거한다.

병합 대상 DTO도 현재 프로젝트 필드 기준이어야 한다. 강의자료 예시처럼 `volumeTitle`을 추가하지 말고, 기존 `BookSearchResponse`의 `category`를 유지한 상태에서 `similarity`, `rrfScore`만 확장한다.

```text
Map<Long, BookSearchResponse> bookMap
Map<Long, Double> rrfScoreMap
```

처리 순서는 아래와 같다.

```text
1. keywordResults 순회
   - bookMap에 없으면 저장
   - rrfScoreMap에 점수 누적

2. vectorResults 순회
   - bookMap에 없으면 저장
   - 이미 있으면 similarity 같은 vector 점수는 보존되도록 병합
   - rrfScoreMap에 점수 누적

3. bookMap values를 rrfScore desc로 정렬
4. 각 응답 DTO에 rrfScore를 세팅하거나 새 DTO로 변환
```

현재 `BookSearchResponse`는 setter가 없고 final도 아니다. 점수를 추가할 때는 아래 중 하나를 선택해야 한다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| 점수 포함 생성자 추가 | 불변에 가까운 흐름 | 생성자 수 증가 |
| `withScores(...)` 메서드 추가 | 기존 DTO 복사 명확 | 코드 조금 늘어남 |
| setter 추가 | 구현 빠름 | DTO 상태 변경이 쉬워짐 |

현재 코드 스타일이면 점수 포함 생성자를 추가하거나 `withSimilarity`, `withRrfScore` 같은 복사 메서드를 두는 편이 낫다.

## 키워드가 강한 경우를 보존하기

하이브리드 검색은 벡터 검색만의 대체물이 아니다. 아래 검색어는 키워드 검색이 더 강하다.

```text
ISBN
저자명
정확한 책 제목
출판사명
프로그래밍 언어 이름: Java, C++, Python
```

따라서 `isbn`이 들어온 경우에는 하이브리드로 가지 말고 정확 검색을 우선하는 정책도 가능하다.

```text
if isbn exists:
  KEYWORD exact filter 우선
else:
  HYBRID
```

## 벡터 누락 데이터 처리

임베딩이 아직 생성되지 않은 책은 vector 결과에 나오지 않는다. 하지만 keyword 결과에는 나올 수 있다.

RRF 병합에서는 keyword-only 결과도 최종 결과에 포함해야 한다. 그래야 임베딩 배치가 일부 실패해도 검색 결과가 완전히 사라지지 않는다.

## UI 표시

검색 타입 선택 UI는 현재 화면에 붙일 수 있다.

```text
KEYWORD: 기본 검색
VECTOR: 의미 검색
HYBRID: 통합 검색
```

결과 카드에는 점수를 과하게 노출하지 않아도 된다. 개발 중에는 similarity와 rrfScore를 보여주면 디버깅에 도움이 되고, 최종 UI에서는 숨겨도 된다.

## 테스트 전략

RRF 자체는 DB 없이 단위 테스트가 가능하다.

```text
keyword: A, B, C
vector:  C, A, D

expected:
  A와 C가 상위
  B와 D는 그 아래
  중복 id는 하나만 존재
```

벡터 검색 자체는 PostgreSQL 기반 테스트가 필요하다. 하이브리드 테스트는 둘을 나눠서 본다.

| 테스트 | DB 필요 여부 | 검증 |
| --- | --- | --- |
| RRF 계산 단위 테스트 | 불필요 | 순위 병합, 중복 제거 |
| Service 분기 테스트 | mock 가능 | SearchType별 호출 |
| vectorSearch 통합 테스트 | PostgreSQL 필요 | pgvector 정렬 |
| 화면 테스트 | 선택 | searchType 파라미터 유지 |

## 완료 기준

```text
1. KEYWORD, VECTOR, HYBRID 검색 타입이 Service에서 분기된다.
2. HYBRID는 keyword topN과 vector topN을 각각 가져온다.
3. 도서 ID 기준으로 중복 제거한다.
4. RRF 점수로 최종 정렬한다.
5. keyword-only, vector-only 결과가 모두 살아남는다.
6. 기존 KEYWORD 검색과 페이징이 깨지지 않는다.
```

Step 2의 최종 목표는 “항상 벡터 검색이 최고”가 아니다. 정확한 단어 검색은 키워드가 맡고, 자연어 의미 검색은 벡터가 맡고, 사용자가 애매하게 검색했을 때 하이브리드가 둘 사이의 균형을 잡게 만드는 것이다.
