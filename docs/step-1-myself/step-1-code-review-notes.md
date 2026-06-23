# Step 1 코드 구조 문제 정리

이 문서는 Step 1 CSV 초기 적재 코드에서 강사님이 지적했을 가능성이 높은 설계 문제를 정리한 문서다.

핵심은 "Spring Batch를 쓰지 않아서 문제"가 아니라, 현재 코드가 이벤트를 사용하면서 오히려 데이터 처리 흐름을 복잡하게 만들었다는 점이다.

## 현재 구조

현재 CSV 초기 적재 흐름은 아래와 같다.

```text
CsvBookParser
  -> CSV 한 줄 파싱
  -> BookParsedEvent 발행
  -> BookParsingEventListener가 buffer에 저장
  -> 마지막에 BookParsingCompleteEvent 발행
  -> BookParsingEventListener가 buffer 전체를 BookBatchService에 전달
  -> BookBatchService가 batchSize만큼 잘라 DB 저장
```

겉으로 보면 이벤트 기반 구조이고, `batchSize`만큼 잘라 저장하므로 배치 처리처럼 보인다. 하지만 실제로는 여러 설계 문제가 있다.

## 문제 1. 이벤트를 쓸 이유가 약하다

이벤트는 보통 "어떤 일이 발생했으니 관심 있는 다른 기능들이 반응하라"는 용도에 적합하다.

예를 들어 리뷰 등록은 이벤트에 잘 맞는다.

```text
리뷰 작성됨
  -> 리뷰 요약 생성
  -> 알림 발송
  -> 통계 갱신
```

이 경우에는 "리뷰 작성"이라는 업무 사건이 있고, 그 사건에 여러 후속 작업이 반응할 수 있다.

하지만 CSV 초기 적재는 본질적으로 순차 작업이다.

```text
읽기
  -> 변환
  -> 검증
  -> 저장
```

이 흐름은 이벤트보다 명시적인 메서드 호출이나 배치 파이프라인이 더 자연스럽다.

## 문제 2. 코드 흐름이 숨겨진다

`CsvBookParser`에서는 실제 저장 코드가 보이지 않는다.

```java
applicationEventPublisher.publishEvent(new BookParsedEvent(book));
```

이 코드만 보면 `book`이 어디에 저장되는지 알 수 없다. 실제 저장 흐름을 이해하려면 `BookParsedEvent`를 누가 구독하는지 다시 찾아야 한다.

작은 프로젝트에서는 이런 이벤트 구조가 분리보다 복잡도를 더 크게 만들 수 있다.

명시적인 호출이었다면 흐름이 더 바로 보인다.

```java
bookImportService.add(book);
```

또는 청크 단위 저장이라면 아래처럼 의도가 더 분명하다.

```java
bookImportService.saveChunk(chunk);
```

## 문제 3. Listener가 상태를 들고 있다

현재 `BookParsingEventListener`는 내부에 `buffer`를 가지고 있다.

```java
private final List<BookRawData> buffer = new ArrayList<>();
```

Spring Bean은 기본적으로 singleton이다. 즉, 이 `buffer`는 애플리케이션 전체에서 공유되는 상태다.

이 방식은 아래 상황에서 문제가 생길 수 있다.

- 초기 적재가 두 번 실행될 때
- 중간에 예외가 발생했을 때
- 테스트가 반복 실행될 때
- 비동기 이벤트로 바뀌었을 때
- 여러 스레드에서 이벤트가 처리될 때

특히 `ArrayList`는 thread-safe하지 않다. 지금은 Spring 기본 이벤트가 동기 처리라서 당장 문제가 덜 보일 수 있지만, 이벤트 구조는 나중에 비동기로 바뀌기 쉽다. 그때는 동시성 문제도 생길 수 있다.

## 문제 4. 진짜 배치 처리라고 보기 어렵다

현재 코드는 마지막에 `batchSize`만큼 잘라 저장한다.

```text
전체 CSV 읽기
  -> 전체 데이터를 buffer에 저장
  -> 파싱 완료 이벤트 발행
  -> buffer 전체를 batchSize만큼 잘라 저장
```

하지만 좋은 배치 처리는 보통 아래처럼 동작한다.

```text
1000개 읽기
  -> 1000개 저장
  -> 메모리 비우기
  -> 다음 1000개 읽기
  -> 다음 1000개 저장
```

현재 구조는 DB 저장만 나눠서 할 뿐, 메모리에는 전체 데이터를 들고 있다.

게다가 `CsvBookParser`에서 `parser.getRecords()`를 사용하면 CSV 전체를 메모리에 올릴 수 있다.

```java
for (CSVRecord record : parser.getRecords()) {
    ...
}
```

그리고 다시 listener의 `buffer`에 전체 데이터를 쌓는다. 즉, 대용량 CSV에서는 메모리 사용량이 커질 수 있다.

배치 처리의 핵심은 단순히 `saveAll()`을 여러 번 호출하는 것이 아니다. 읽기, 처리, 저장을 일정 단위로 끊어서 메모리와 트랜잭션을 관리하는 것이다.

## 문제 5. 완료 이벤트에 너무 의존한다

현재 구조에서는 마지막에 이 이벤트가 발행되어야 저장이 시작된다.

```java
applicationEventPublisher.publishEvent(new BookParsingCompleteEvent());
```

문제는 중간에 예외가 발생하면 완료 이벤트가 발행되지 않는다는 점이다. 그러면 listener의 `buffer`에 쌓인 데이터는 저장되지 않는다.

흐름을 단순화하면 이런 의존을 줄일 수 있다.

```java
if (chunk.size() == batchSize) {
    bookSaveService.saveAll(chunk);
    chunk.clear();
}
```

이 방식은 저장 시점이 코드에 직접 드러난다.

## 문제 6. 책임 분리가 애매하다

현재 `CsvBookParser`는 너무 많은 일을 한다.

```text
파일 열기
CSV 파싱
문자열 읽기
날짜 변환
가격 변환
BookRawData 생성
이벤트 발행
```

그리고 `BookParsingEventListener`는 이름상 이벤트 listener지만 실제로는 아래 책임도 함께 가진다.

```text
buffer 관리
파싱 시작 트리거
파싱 완료 후 저장 실행
```

책임이 더 명확한 구조는 아래와 같다.

```text
CsvBookReader 또는 CsvBookParser
  -> CSV 한 줄을 BookRawData로 변환

BookProcessor
  -> 필수값 검증
  -> 빈 문자열 정리
  -> 날짜, 가격 정제

BookWriter 또는 BookImportService
  -> 일정 개수씩 DB 저장
```

이렇게 나누면 각 클래스가 왜 존재하는지 명확해진다.

## 더 나은 구조 1. Spring Batch 없이 단순 청크 처리

Spring Batch를 쓰지 않아도 현재 구조보다 단순하고 명확하게 만들 수 있다.

```text
BookImportService
  -> CSV를 한 줄씩 읽음
  -> BookRawData로 변환
  -> chunk 리스트에 담음
  -> batchSize가 되면 저장
  -> chunk 비움
  -> 마지막 남은 데이터 저장
```

예시 코드는 아래와 같다.

```java
public void importBooks() {
    List<BookRawData> chunk = new ArrayList<>();

    for (BookRawData rawData : csvReader.read()) {
        chunk.add(rawData);

        if (chunk.size() == batchSize) {
            bookSaveService.saveAll(chunk);
            chunk.clear();
        }
    }

    if (!chunk.isEmpty()) {
        bookSaveService.saveAll(chunk);
    }
}
```

이 구조의 장점은 아래와 같다.

- 이벤트가 없어 흐름이 바로 보인다.
- 전체 데이터를 메모리에 쌓지 않는다.
- 저장 시점이 명확하다.
- 테스트하기 쉽다.
- 클래스 책임이 단순해진다.

## 더 나은 구조 2. Spring Batch 사용

데이터가 많고, 실행 이력 관리나 재시작, 실패 처리까지 필요하다면 Spring Batch가 더 적합하다.

Spring Batch 구조는 아래와 같다.

```text
Job
  -> Step
      -> ItemReader
      -> ItemProcessor
      -> ItemWriter
```

도서 초기 적재에 적용하면 아래처럼 볼 수 있다.

```text
ItemReader
  -> CSV 한 줄 읽기

ItemProcessor
  -> BookRawData 검증 및 정제

ItemWriter
  -> chunk 단위로 DB 저장
```

이 방식은 현재 이벤트 구조가 직접 처리하고 있는 buffer, 완료 이벤트, 저장 타이밍, 트랜잭션 경계를 프레임워크가 더 명확하게 관리해준다.

다만 강사님이 말한 핵심은 "반드시 Spring Batch를 써야 한다"가 아닐 수 있다. 더 중요한 것은 현재 이벤트 구조가 문제를 해결하기보다 흐름을 더 복잡하게 만들었다는 점이다.

## 핵심 결론

현재 코드의 문제는 아래 한 문장으로 정리할 수 있다.

```text
단순한 CSV 초기 적재 작업을 이벤트 기반으로 만들면서
오히려 흐름이 숨고,
상태 관리가 위험해지고,
메모리 사용이 커지고,
책임 분리가 애매해졌다.
```

따라서 개선 방향은 아래 둘 중 하나다.

```text
작고 단순한 프로젝트
  -> 이벤트 제거
  -> 명시적인 청크 처리 서비스로 변경

대용량 데이터, 재시작, 실패 이력 관리가 필요한 프로젝트
  -> Spring Batch 사용
  -> Reader / Processor / Writer 구조로 변경
```

## 면담이나 발표 때 말할 수 있는 정리

강사님께 설명한다면 이렇게 말할 수 있다.

```text
처음에는 파서와 저장 로직을 분리하려고 이벤트를 사용했지만,
CSV 초기 적재는 도메인 이벤트라기보다 순차적인 데이터 처리 작업이라서
이벤트를 쓰는 것이 오히려 흐름을 숨기는 문제가 있었습니다.

또 listener가 singleton bean 상태로 buffer를 들고 있고,
CSV 전체를 메모리에 쌓은 뒤 마지막 완료 이벤트에서 저장하기 때문에
진짜 chunk 기반 배치 처리라고 보기 어렵습니다.

그래서 개선한다면 이벤트를 제거하고,
CSV를 스트리밍으로 읽으면서 batchSize만큼 모일 때마다 저장하는 구조로 바꾸거나,
더 정석적으로는 Spring Batch의 Reader, Processor, Writer 구조를 사용하는 것이 맞다고 생각합니다.
```
