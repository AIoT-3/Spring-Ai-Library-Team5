# 03. 현재 프로젝트 임베딩 생성 보강

이 문서는 강의자료 `docs/step-2/03.embedding-generation.md`를 현재 프로젝트 기준으로 적용하는 가이드다.

원본 문서는 Spring AI로 임베딩을 호출하는 방법을 설명한다. 실제 구현에서는 어떤 텍스트를 임베딩할지, 대량 데이터를 어떻게 재시작 가능하게 처리할지, 실패한 책을 어떻게 남길지가 더 중요하다.

## 현재 설정

`application.properties`에는 이미 OpenAI 호환 임베딩 API 설정이 있다.

```properties
spring.ai.openai.base-url=https://emb.java21.net
spring.ai.openai.api-key=${OPENAI_API_KEY:dummy}
spring.ai.openai.embedding.options.model=bge-m3
```

`pom.xml`에도 `spring-ai-starter-model-openai`가 들어가 있다. 따라서 별도 설정 클래스를 먼저 만들기보다 Spring Boot auto configuration으로 `EmbeddingModel`이 주입되는지 확인하는 방향이 좋다.

## 임베딩 대상 텍스트

도서 검색에서 의미를 만들 필드는 ISBN이나 가격이 아니라 제목, 부제, 저자, 출판사, 카테고리, 소개글이다.

강의자료 예시에 `volumeTitle`이 나오더라도 현재 프로젝트에서는 사용하지 않는다. 현재 `Book` 엔티티의 분류 정보는 `category`이므로 임베딩 텍스트에도 `category`를 넣는다.

권장 조합은 아래와 같다.

```text
[제목] {title}
[부제] {subtitle}
[저자] {authorName}
[출판사] {publisherName}
[분류] {category}
[소개] {bookContent}
```

라벨을 붙이는 이유는 모델이 필드의 의미를 구분하기 쉽게 하기 위해서다. 단순히 문자열만 이어 붙이면 저자명과 본문이 섞여 검색 품질을 해칠 수 있다.

## 전처리 기준

전처리는 과하게 하지 않는 편이 좋다. 특히 한글 검색에서는 의미 있는 기호나 영문 대소문자를 전부 제거하면 품질이 떨어질 수 있다.

최소 전처리 기준은 아래 정도다.

```text
1. null을 빈 문자열로 처리
2. HTML entity decode
3. HTML tag 제거
4. 연속 공백을 하나로 정규화
5. 너무 긴 텍스트는 최대 길이로 자르기
```

특수문자를 모두 제거하는 정규식은 조심해야 한다. `C++`, `C#`, `Node.js`, `GPT-4` 같은 표현이 망가질 수 있다.

## 패키지 구조 제안

현재 프로젝트 구조에 맞춰 아래처럼 나누는 것을 추천한다.

```text
com.nhnacademy.ailibrarymyself.core.embedding
  EmbeddingService.java
  BookEmbeddingTextBuilder.java
  TextPreprocessor.java

com.nhnacademy.ailibrarymyself.batch.embedding
  BookEmbeddingJobRunner.java
  BookEmbeddingBatchService.java
```

처음부터 Spring Batch Job으로 만들 수도 있지만, Step 2 초반에는 재시작 가능한 Service부터 만들고 나중에 Batch로 옮겨도 된다.

## EmbeddingService 책임

`EmbeddingService`는 외부 모델 호출만 담당하게 한다.

```java
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
```

여기에 DB 조회나 Book 전처리까지 넣으면 테스트와 재시도가 어려워진다.

## BookEmbeddingTextBuilder 책임

`Book`을 임베딩용 문자열로 바꾸는 책임은 별도 클래스로 뺀다.

```java
public String build(Book book) {
    return """
            [제목] %s
            [부제] %s
            [저자] %s
            [출판사] %s
            [분류] %s
            [소개] %s
            """.formatted(
            safe(book.getTitle()),
            safe(book.getSubtitle()),
            safe(book.getAuthorName()),
            safe(book.getPublisherName()),
            safe(book.getCategory()),
            safe(book.getBookContent())
    );
}
```

이렇게 분리하면 나중에 “본문을 빼면 검색 품질이 좋아지는가” 같은 실험을 코드 변경 범위 작게 할 수 있다.

## 대량 임베딩 처리 원칙

도서 전체를 한 번에 읽어 메모리에 올리지 않는다. `embedding IS NULL`인 책만 페이지 단위로 가져와 처리한다.

```text
1. embedding이 null인 책을 id 오름차순으로 N개 조회
2. 각 책의 임베딩 텍스트 생성
3. EmbeddingModel 호출
4. Book.updateEmbedding(vector)
5. chunk 단위로 flush/commit
6. 실패한 책 id와 이유를 로그로 남김
```

중요한 점은 “처리 대상 기준”이다. offset pagination은 저장 도중 대상 집합이 바뀌면 누락이 생길 수 있다. 가능하면 `id > lastId` 방식이나 `embedding IS NULL ORDER BY id LIMIT N` 반복 방식을 쓴다.

## 실패 처리

임베딩 API는 네트워크 호출이므로 실패할 수 있다.

| 실패 유형 | 처리 |
| --- | --- |
| 일시적 timeout | 1-3회 재시도 |
| 빈 텍스트 | embedding 저장하지 않고 skip |
| 차원 불일치 | 즉시 실패 처리, 설정 확인 |
| API 응답 오류 | book id와 에러 로그 남김 |
| DB 저장 실패 | 트랜잭션 rollback 후 재실행 가능하게 유지 |

차원 검증은 반드시 넣는 편이 좋다.

```java
if (embedding.length != 1024) {
    throw new IllegalStateException("Unexpected embedding dimension: " + embedding.length);
}
```

## 저장 방식 주의

현재 `Book`에는 아래 메서드가 있다.

```java
public void updateEmbedding(float[] embedding) {
    this.embedding = embedding;
}
```

JPA dirty checking으로 저장하려면 트랜잭션 안에서 영속 상태의 `Book`을 조회한 뒤 `updateEmbedding`을 호출해야 한다.

대량 업데이트 성능이 부족하면 나중에 JDBC batch update나 native query로 바꿀 수 있다. 처음부터 성능 최적화를 넣기보다 정상 저장과 재시작 가능성을 먼저 확보한다.

## 완료 기준

이 챕터는 아래가 되면 완료로 본다.

```text
1. 임베딩 텍스트 생성 규칙이 문서화되어 있다.
2. embedding IS NULL인 도서만 처리한다.
3. 실패해도 재실행하면 남은 도서만 이어서 처리된다.
4. 저장 전 1024차원 검증을 한다.
5. 처리 완료 후 COUNT(embedding)으로 결과를 확인할 수 있다.
```

Step 2에서 가장 흔한 문제는 “벡터 검색 쿼리”가 아니라 “좋지 않은 텍스트를 대량으로 임베딩해서 품질이 낮아지는 것”이다. 임베딩 생성 규칙을 먼저 안정화해야 검색 품질을 해석할 수 있다.
