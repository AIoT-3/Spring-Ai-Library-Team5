# 03. 현재 프로젝트 캐싱 전략

이 문서는 강의자료 `docs/step-4/03.semantic-caching.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 문서는 시맨틱 캐싱을 바로 설명하지만, 현재 프로젝트에서는 단계를 나누는 편이 안전하다.

## 1단계: 동일 질문 캐시

처음에는 정확히 같은 질문에 대한 RAG 결과를 캐시한다.

```text
cache key:
  rag:v1:{normalizedQuery}:{searchType}:{ragK}

value:
  BookRagResult JSON

ttl:
  cache.book-search.ttl.minutes=30
```

이 단계는 임베딩 없이도 가능하고, 구현 난이도가 낮다.

## 2단계: 시맨틱 캐시

동일 질문 캐시가 안정화되면 시맨틱 캐시로 확장한다.

```text
1. 질문을 embedding으로 변환
2. 캐시된 queryEmbedding과 cosine similarity 비교
3. threshold 이상이면 캐시 hit
4. 아니면 RAG 실행 후 캐시 저장
```

캐시 임베딩도 BGE-M3 기준 `vector(1024)`로 맞춘다.

## Redis와 Caffeine 선택

현재 프로젝트에는 Redis와 Caffeine 의존성이 모두 있다.

| 저장소 | 용도 |
| --- | --- |
| Caffeine | 단일 애플리케이션 로컬 캐시, 구현 쉬움 |
| Redis | 여러 서버 공유 캐시, TTL 관리, 운영 적합 |
| PostgreSQL + pgvector | 시맨틱 캐시 유사도 검색에 적합 |

시맨틱 캐시는 “유사도 검색”이 필요하므로 PostgreSQL `vector(1024)` 테이블로 구현하는 편이 자연스럽다. Redis는 결과 JSON 캐시 저장소로 쓰고, query embedding 검색은 DB에서 하는 구조도 가능하다.

## 캐시 엔티티 제안

```text
book_rag_cache
  id
  normalized_query
  query_embedding vector(1024)
  search_type
  rag_k
  result_json text
  created_at
  expires_at
  access_count
```

캐시 결과에는 `category`, `similarity`, `rrfScore`, 추천 이유가 포함될 수 있다. 다만 도서 정보가 변경되면 오래된 결과가 될 수 있으므로 TTL을 짧게 유지한다.

## 무효화 기준

| 변경 | 캐시 영향 |
| --- | --- |
| 도서 기본 정보 변경 | 관련 캐시가 오래될 수 있음 |
| embedding 재생성 | 검색 결과가 달라질 수 있음 |
| 리뷰 요약 변경 | Step 4-5 이후 RAG 답변이 달라질 수 있음 |
| 프롬프트 변경 | 캐시 key version 증가 |

프롬프트나 컨텍스트 형식이 바뀌면 key prefix를 `rag:v2`처럼 올린다.

## threshold 주의

시맨틱 캐시 threshold는 너무 낮으면 다른 질문에 같은 답을 줄 위험이 있다.

| threshold | 성향 |
| --- | --- |
| `0.98` | 매우 보수적 |
| `0.95` | 일반적 시작점 |
| `0.90` 이하 | 오답 캐시 위험 증가 |

처음에는 `0.98`로 시작하고 로그를 보며 조정한다.

## 완료 기준

```text
1. 동일 질문 캐시를 먼저 구현한다.
2. 캐시 key에는 version, query, searchType, ragK가 포함된다.
3. 시맨틱 캐시는 queryEmbedding vector(1024)를 사용한다.
4. TTL과 prompt version 변경 전략이 있다.
5. 캐시 hit/miss가 로그로 남는다.
```
