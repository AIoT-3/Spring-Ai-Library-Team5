# 02. 현재 프로젝트 Top-K와 컨텍스트 최적화

이 문서는 강의자료 `docs/step-4/02.topk-optimization.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

Top-K는 하나가 아니다. 검색 후보를 넓게 가져오는 K와 LLM에 전달하는 K를 분리해야 한다.

## K 값 분리

```text
Retrieval K:
  HYBRID 검색에서 가져올 후보 수
  예: 50

RAG K:
  LLM 컨텍스트에 넣을 최종 도서 수
  예: 5
```

현재 설정의 `app.ai.max-candidates=5`는 RAG K로 쓰는 것이 자연스럽다. Retrieval K는 별도 설정을 추가하거나 `app.search.default-limit`와 구분하는 편이 좋다.

```properties
app.ai.retrieval-candidates=50
app.ai.max-candidates=5
```

## 후보 선택 기준

HYBRID 검색 결과는 이미 RRF 점수 기준으로 정렬되어 있어야 한다. RAG에서는 그중 상위 K개만 컨텍스트에 넣는다.

```text
1. HYBRID top 50 조회
2. relevance가 너무 낮은 후보 제거
3. rrfScore desc 유지
4. 상위 5개만 컨텍스트 생성
```

`BookSearchResponse`에 `rrfScore`가 없다면 Step 2-5에서 먼저 추가해야 한다.

## 컨텍스트 길이 제한

RAG 컨텍스트에는 현재 프로젝트 필드만 넣는다.

```text
ID
제목
저자
출판사
분류(category)
출간일
소개(bookContent 일부)
```

`volumeTitle`은 넣지 않는다. 현재 엔티티에는 없고, 강의자료 예시를 그대로 따라가면 projection이 깨진다.

## 긴 bookContent 자르기

도서 소개글은 길이가 제각각이다. 도서별 최대 길이를 둔다.

```java
private String truncate(String value, int maxLength) {
    if (value == null || value.isBlank()) {
        return "-";
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= maxLength) {
        return normalized;
    }
    return normalized.substring(0, maxLength) + "...";
}
```

처음 값은 300자로 시작하고, 품질이 부족하면 500자로 늘린다.

## 빈 결과 처리

Top-K 최적화에서 자주 놓치는 부분은 빈 결과다.

| 상황 | 처리 |
| --- | --- |
| HYBRID 결과 0건 | LLM 호출하지 않음 |
| 필터링 후 0건 | fallback 후보 3개 사용 |
| bookContent가 모두 비어 있음 | 제목/저자/category 중심으로 컨텍스트 생성 |
| RAG K보다 결과가 적음 | 있는 만큼만 사용 |

현재 설정에는 `app.ai.fallback-candidates=3`이 있으므로 필터링이 너무 엄격할 때 사용할 수 있다.

## 완료 기준

```text
1. Retrieval K와 RAG K를 구분한다.
2. RAG 컨텍스트는 max-candidates 이하로 제한한다.
3. bookContent 길이를 제한한다.
4. category를 포함하고 volumeTitle은 사용하지 않는다.
5. 필터링 후 빈 결과 fallback이 있다.
```
