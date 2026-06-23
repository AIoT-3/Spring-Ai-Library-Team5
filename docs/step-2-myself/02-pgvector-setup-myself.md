# 02. 현재 프로젝트 pgvector 설정 보강

이 문서는 강의자료 `docs/step-2/02.pgvector-setup.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 문서는 `CREATE EXTENSION`, `ALTER TABLE` 중심으로 설명한다. 실제 프로젝트에서는 JPA 매핑, H2 테스트, Hibernate validate, vector 차원 고정까지 같이 맞춰야 한다.

## 현재 프로젝트 상태

`Book` 엔티티에는 이미 `embedding` 필드가 있다.

```java
@Column(name = "embedding", columnDefinition = "vector(1024)")
@JdbcTypeCode(SqlTypes.VARCHAR)
private float[] embedding;
```

강의자료에는 엔티티 기준 설명이 부족할 수 있는데, 현재 프로젝트에서는 벡터 컬럼을 `Book` 엔티티에 두고 진행한다.

주의할 점은 `columnDefinition = "vector"`만으로는 차원이 고정되지 않는다는 것이다. 실습 모델이 BGE-M3이고 1024차원 벡터를 저장할 예정이므로 엔티티와 DB 컬럼 모두 `vector(1024)`로 맞추는 편이 좋다.

또 하나의 차이는 도서 분류 필드다. 강의자료 예시에서 `volumeTitle` 같은 필드가 나오더라도 현재 프로젝트 엔티티에는 없다. 현재 프로젝트에서는 아래 필드를 기준으로 한다.

| 강의자료에서 나올 수 있는 필드 | 현재 프로젝트 기준 |
| --- | --- |
| `volumeTitle` | 사용하지 않음 |
| 권명/분류 표시용 필드 | `category` |
| 벡터 저장 필드 | `embedding` |

따라서 Step 2의 DDL, DTO projection, 임베딩 텍스트 생성, 검색 SQL에서는 `volumeTitle`이 아니라 `category`를 사용해야 한다.

## 운영 PostgreSQL DDL

PostgreSQL에서 먼저 pgvector 확장을 활성화한다.

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

컬럼이 없다면 추가한다.

```sql
ALTER TABLE books
ADD COLUMN embedding vector(1024);
```

이미 `embedding vector`로 만들어져 있다면 차원을 고정하는 쪽을 검토한다.

```sql
ALTER TABLE books
ALTER COLUMN embedding TYPE vector(1024);
```

기존 데이터에 잘못된 차원의 값이 들어가 있으면 실패할 수 있다. Step 2 초반에는 임베딩이 아직 없을 가능성이 높으므로 이때 고정하는 것이 가장 쉽다.

## Hibernate validate와 컬럼 정의

현재 설정은 아래처럼 되어 있다.

```properties
spring.jpa.hibernate.ddl-auto=validate
```

`validate`는 애플리케이션 시작 시 엔티티와 실제 DB schema가 맞는지 확인한다. 따라서 애플리케이션을 띄우기 전에 DB에 `books.embedding` 컬럼이 있어야 한다.

엔티티의 `columnDefinition`도 DB와 맞추는 편이 명확하다.

```java
@Column(name = "embedding", columnDefinition = "vector(1024)")
@JdbcTypeCode(SqlTypes.VARCHAR)
private float[] embedding;
```

다만 Hibernate가 pgvector 타입을 완전히 이해하는 것은 별도 문제다. 단순 저장/조회보다 벡터 연산 검색을 할 때는 native SQL이나 `stringTemplate` 기반 접근이 더 예측 가능하다.

## H2 테스트와 PostgreSQL 기능 분리

H2는 pgvector를 제공하지 않는다. 현재 Step 1 문서처럼 H2 테스트에서 임시 domain을 만들 수 있다.

```properties
spring.datasource.url=jdbc:h2:mem:book_repository_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS vector AS VARBINARY
```

이 설정은 “엔티티 로딩과 기본 QueryDSL 테스트를 H2에서 통과시키기 위한 우회”일 뿐이다. 아래 기능은 H2에서 검증하면 안 된다.

| 기능 | H2 테스트 가능 여부 | 이유 |
| --- | --- | --- |
| 기본 키워드 검색 | 가능 | 일반 SQL/LIKE 중심 |
| `embedding` 컬럼 존재 | 제한적으로 가능 | domain 우회 가능 |
| pgvector `<=>` 연산자 | 불가능 | H2에 없음 |
| vector index | 불가능 | PostgreSQL 전용 |
| 실제 유사도 정렬 | 불가능 | pgvector 연산 필요 |

벡터 검색은 PostgreSQL DB나 Testcontainers PostgreSQL로 따로 검증해야 한다.

## 인덱스는 언제 만들까

처음에는 인덱스 없이 정확한 결과부터 확인한다.

```sql
SELECT id, title
FROM books
WHERE embedding IS NOT NULL
ORDER BY embedding <=> '[...]'::vector
LIMIT 10;
```

데이터가 많아져 느려지면 pgvector index를 추가한다.

```sql
CREATE INDEX idx_books_embedding_hnsw
ON books
USING hnsw (embedding vector_cosine_ops);
```

HNSW 인덱스는 빠르지만, 정확도와 속도 사이의 trade-off가 있다. Step 2 첫 구현에서는 인덱스 최적화보다 결과 정확성을 먼저 확인하는 편이 좋다.

## 점검 SQL

아래 SQL로 DB 상태를 확인한다.

```sql
SELECT extname
FROM pg_extension
WHERE extname = 'vector';
```

```sql
SELECT column_name, udt_name, data_type
FROM information_schema.columns
WHERE table_name = 'books'
  AND column_name = 'embedding';
```

```sql
SELECT COUNT(*) AS total,
       COUNT(embedding) AS embedded,
       COUNT(*) - COUNT(embedding) AS missing_embedding
FROM books;
```

마지막 쿼리는 Step 3 임베딩 배치가 정상적으로 채웠는지 확인하는 기준이 된다.

## 현재 단계에서 정할 것

| 항목 | 권장값 | 이유 |
| --- | --- | --- |
| 모델 | `bge-m3` | 원본 실습과 현재 설정이 일치 |
| 차원 | `1024` | BGE-M3 출력 차원 |
| 컬럼 타입 | `vector(1024)` | 잘못된 차원 저장 방지 |
| 기본 검색 | `KEYWORD` | 기존 기능 안정성 유지 |
| 벡터 검증 DB | PostgreSQL | pgvector 연산 검증 필요 |

이 단계의 완료 기준은 “애플리케이션이 PostgreSQL에서 `validate`를 통과하고, `books.embedding`에 1024차원 벡터를 저장할 준비가 되어 있는가”이다.
