# 01. 현재 프로젝트 RAG 성능과 비용 기준 잡기

이 문서는 강의자료 `docs/step-4/01.performance-cost.md`를 현재 프로젝트 기준으로 보강한 내용이다.

Step 4에서 최적화를 시작하기 전에 먼저 측정 기준을 남겨야 한다. 측정 없이 캐시나 Top-K를 넣으면 무엇이 좋아졌는지 알 수 없다.

## 현재 병목 후보

현재 RAG 흐름에서 비용과 시간이 드는 지점은 아래다.

```text
1. 검색어 임베딩 생성
2. HYBRID 검색
3. RAG 후보 topK 선택
4. 컨텍스트 문자열 생성
5. ChatModel 호출
6. JSON 파싱
```

가장 큰 병목은 보통 ChatModel 호출이다. 그 다음은 컨텍스트 길이와 벡터 검색이다.

## 현재 설정값

`application.properties`에는 이미 운영 기준으로 쓸 수 있는 값이 있다.

```properties
cache.ttl.minutes=30
cache.book-search.ttl.minutes=30
cache.book-search.max-size=500

app.search.default-limit=10
app.ai.max-candidates=5
app.ai.fallback-candidates=3
app.ai.min-relevance-score=50
app.ai.rrf-k=60
app.ai.max-review-summary-length=100
```

Step 4 문서와 구현은 이 값들을 기준으로 맞춘다. 상수를 코드에 흩뿌리지 말고 `@ConfigurationProperties`나 `@Value`로 읽게 한다.

## 로그로 남길 지표

처음에는 별도 모니터링 도구 없이 로그만으로 충분하다.

| 지표 | 예시 |
| --- | --- |
| 검색 타입 | `RAG` |
| 질문 길이 | `23 chars` |
| retrieval 후보 수 | `50` |
| RAG 컨텍스트 후보 수 | `5` |
| 컨텍스트 길이 | `3200 chars` |
| 임베딩 시간 | `180ms` |
| 검색 시간 | `220ms` |
| LLM 시간 | `2400ms` |
| 전체 시간 | `2900ms` |
| 캐시 hit 여부 | `true/false` |

프롬프트 전문을 info 로그로 남기지 않는다. 디버깅할 때만 일부를 제한적으로 남긴다.

## 품질과 비용의 경계

Top-K를 줄이면 비용은 내려가지만 근거가 부족해질 수 있다. 현재 프로젝트에서는 아래 기준으로 시작한다.

| 항목 | 시작값 | 이유 |
| --- | --- | --- |
| HYBRID 후보 | 50 | RRF 병합 후보 확보 |
| RAG 전달 후보 | 5 | LLM 컨텍스트 비용 제한 |
| bookContent 길이 | 300자 | 도서별 토큰 제한 |
| 리뷰 요약 길이 | 100자 | Step 4-5에서 추가 |

## 완료 기준

```text
1. RAG 요청마다 주요 단계 시간이 로그로 남는다.
2. 후보 수와 컨텍스트 길이가 로그로 남는다.
3. 설정값은 application.properties 기준으로 읽는다.
4. category는 컨텍스트에 유지하고 volumeTitle은 사용하지 않는다.
5. 최적화 전/후를 비교할 기준 검색어를 5개 이상 정한다.
```
