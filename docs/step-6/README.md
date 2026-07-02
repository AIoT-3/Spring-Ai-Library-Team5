# Step 6: 개인화 추천 시스템

## 학습 개요

Step 5에서 수집한 **사용자 피드백 데이터를 활용**하여, 사용자별로 맞춤화된 도서 추천 시스템을 구축합니다. 사용자가 좋아한 도서들의 임베딩 벡터를 분석하여 선호도를 학습하고, 이를 검색 결과에 반영합니다.

## 학습 목표

1. **벡터 임베딩과 코사인 유사도 이해**
   - 도서 콘텐츠를 1024차원 벡터로 변환하는 BGE-M3 모델 이해
   - 벡터 간 유사도를 코사인 유사도로 계산하는 방법 학습
   - 벡터 평균으로 사용자 선호도 표현하는 방법 습득

2. **사용자 선호도 학습 방법**
   - GOOD 피드백이 많은 도서들의 임베딩 평균 계산
   - 평균 벡터를 사용자의 취향으로 해석
   - 콜드 스타트(Cold Start) 문제와 해결 방안 이해

3. **검색 결과 개인화 재정렬**
   - 기존 RRF 점수 + 선호도 유사도 조합
   - 가중치 파라미터(0.3)를 통한 기존 검색 결과 존중
   - 개인화된 순위로 결과 재정렬

4. **익명 사용자 환경에서의 개인화**
   - 로그인 없이 chatId만으로 개인화 구현
   - 세션 기반 개인화의 한계와 장점 이해

---

## 선행 조건

### Step 5 완료 필수

- ✅ SearchFeedback Entity
- ✅ FeedbackService
- ✅ Telegram Bot 피드백 수집
- ✅ 피드백 데이터가 일부 쌓여 있어야 함

### 기술 스택

- **BGE-M3 임베딩**: 1024차원 float[] 배열 (기존 보유)
- **코사인 유사도**: 사용자 선호 벡터 매칭
- **RRF 알고리즘**: 기존 검색 결과와 조합
- **Telegram Bot API**: chatId 기반 세션 관리

---

## 문서 구조

### Phase 1: 기초 이론 (2-3시간)

**[01. 벡터 임베딩과 코사인 유사도](./01.vector-embedding-basics.md)**
- 임베딩이란 무엇인가
- BGE-M3 모델과 1024차원 벡터
- 코사인 유사도 수학적 정의
- 코드로 구현하는 코사인 유사도

### Phase 2: 사용자 선호도 학습 (3-4시간)

**[02. 사용자 선호도 벡터 계산](./02.user-preference-vector.md)**
- GOOD 피드백 도서 추출
- 임베딩 평균 계산 방법
- 사용자 선호 벡터 생성
- BookRepository 쿼리 추가

### Phase 3: 개인화 재정렬 (1-2시간)

**[03. 개인화 검색 결과 재정렬](./03.personalized-ranking.md)**
- RRF 점수와 선호도 유사도 조합
- 가중치 파라미터(0.3)의 역할
- 재정렬 알고리즘 구현
- Telegram Bot 연동

### Phase 4: 최적화 (1-2시간)

**[04. 콜드 스타트와 성능 최적화](./04.cold-start-and-optimization.md)**
- 콜드 스타트 문제와 해결
- 선호 벡터 캐싱 전략
- 비동기 계산 방안
- 익명 사용자 환경 제약사항

### Phase 5: 통합 구현 (2-3시간)

**[05. 전체 시스템 통합 가이드](./05.integration-guide.md)**
- 전체 아키텍처 구조
- 구현 체크리스트
- 테스트 시나리오
- 트러블슈팅

---

## 전체 시스템 아키텍처

### 개인화 전 (Step 1-5)

```
[사용자 검색]
    ↓
[BookSearchService]
    ↓
[HybridSearchStrategy]
    ├─ Keyword Search (전체 텍스트 검색)
    └─ Vector Search (임베딩 유사도 검색)
    ↓
[RRF(Reciprocal Rank Fusion)]
    └─ 두 검색 결과 병합 → 순위 계산
    ↓
[검색 결과 반환]
```

### 개인화 후 (Step 6)

```
[사용자 검색]
    ↓
[PersonalizationService]
    ├─ chatId로 피드백 조회
    ├─ GOOD 피드백 도서 ID 추출
    ├─ BookRepository에서 임베딩 조회
    └─ 평균 벡터 계산 (사용자 선호도)
    ↓
[HybridSearchStrategy]
    ├─ Keyword Search
    └─ Vector Search
    ↓
[RRF]
    └─ 기존 검색 결과 병합
    ↓
[개인화 재정렬]
    ├─ 각 도서별 선호 벡터와의 유사도 계산
    ├─ 최종 점수 = RRF 점수 + (유사도 × 0.3)
    └─ 재정렬
    ↓
[개인화된 검색 결과 반환]
```

---

## 예상 소요 시간

- **Phase 1 (기초 이론)**: 2-3시간
- **Phase 2 (선호도 학습)**: 3-4시간
- **Phase 3 (재정렬)**: 1-2시간
- **Phase 4 (최적화)**: 1-2시간
- **Phase 5 (통합)**: 2-3시간
- **총계**: 9-14시간

---

## 시작하기 전 체크리스트

### 환경 점검

- [ ] PostgreSQL에 `pgvector` 확장 설치 완료
- [ ] `books` 테이블에 `embedding` 컬럼 존재 (float[1024])
- [ ] `search_feedbacks` 테이블에 데이터 존재 (최소 10건 이상 권장)
- [ ] Telegram Bot이 정상 작동 중

### 피드백 데이터 확인

```sql
-- 1. 전체 피드백 수 확인
SELECT COUNT(*) FROM search_feedbacks;

-- 2. 도서별 피드백 분포 확인
SELECT
    book_id,
    type,
    COUNT(*) as count
FROM search_feedbacks
GROUP BY book_id, type
ORDER BY book_id, type;

-- 3. 사용자별 피드백 수 확인
SELECT
    chat_id,
    COUNT(*) as feedback_count,
    SUM(CASE WHEN type = 'GOOD' THEN 1 ELSE 0 END) as good_count
FROM search_feedbacks
GROUP BY chat_id
ORDER BY feedback_count DESC
LIMIT 10;
```

---

## 학습 체크리스트

### 핵심 개념 이해

- [ ] 벡터 임베딩이 무엇인지 설명할 수 있다
- [ ] 코사인 유사도를 계산할 수 있다
- [ ] 벡터 평균으로 사용자 선호도를 표현할 수 있다
- [ ] RRF 알고리즘을 이해한다

### 구현 능력

- [ ] 사용자별 GOOD 피드백 도서 추출 가능
- [ ] 임베딩 평균 계산 가능
- [ ] 코사인 유사도 기반 재정렬 가능
- [ ] 가중치 파라미터 조절 가능

### 제약사항 이해

- [ ] 콜드 스타트 문제와 해결 방안 안다
- [ ] Telegram chatId 한계와 이해
- [ ] 성능 최적화 방법 (캐싱, 비동기)

---

## 제약사항과 주의사항

### ⚠️ 로그인 시스템 없음

```
사용자 식별: Telegram chatId (익명)
데이터 원천: 최근 피드백 기록만
개인화 범위: 세션 기반 단기 개인화
```

### 콜드 스타트 문제

- 신규 사용자는 피드백이 없어 개인화 불가
- 해결: 피드백 3개 이상 시 개인화 시작

### Telegram chatId 한계

- Bot 삭제 후 재설치 → chatId 변경
- 해결: 단기 세션으로만 활용, 주기적 정리

### 성능 고려사항

- 선호 벡터 계산은 캐싱 필수
- 피드백 데이터 많아지면 비동기 처리 필요

---

## 다음 단계

**Step 6 완료 시 예상 결과:**

1. **도서별 피드백 점수** - 전체 사용자 피드백 반영
2. **사용자 선호도 학습** - 좋아한 도서들의 임베딩 평균
3. **개인화 검색 결과** - RRF + 선호도 유사도 조합

**구현 후 테스트 시나리오:**

```
[Before]
검색: "자바"
결과: 일반적인 인기 자바 도서들

[After]
검색: "자바" (사용자가 "웹 개발" 좋아함)
결과: 스프링, 웹 프레임워크 관련 자바 도서 상위 노출
```

---

## 참고 자료

- [Vector Embeddings for Recommendations](https://www.pinecone.io/learn/vector-recommendations/)
- [Collaborative Filtering vs Content-Based](https://www.datasciencecentral.com/collaborative-filtering-vs-content-based-recommendations/)
- [Cold Start Problem in Recommender Systems](https://arxiv.org/abs/1907.01155)
- [Cosine Similarity for Recommendations](https://www.sciencedirect.com/science/article/pii/S1877050920316842)

---

**시작하기**: [01. 벡터 임베딩과 코사인 유사도](./01.vector-embedding-basics.md) →
