# LibraryInfoNaruApiClient Refactoring Notes

## Overview
The `LibraryInfoNaruApiClient` class is **1470 lines** and handles multiple responsibilities, violating the Single Responsibility Principle. This document outlines the refactoring strategy and progress.

## Current State
- **File**: `src/main/java/com/nhnacademy/library/external/opennaru/client/LibraryInfoNaruApiClient.java`
- **Size**: 1470 lines
- **Responsibilities**:
  - Book search APIs (5 methods)
  - Library search APIs (4 methods)
  - Loan information APIs (3 methods)
  - Book detail APIs (4 methods)
  - Popular/trend APIs (7 methods)
  - Extended APIs (4 methods)
  - Response parsing (10+ methods)
  - URL encoding, error handling, RestClient management

## Refactoring Strategy
The large class will be split into focused, single-responsibility classes:

### 1. Base Class (✅ Created)
**`LibraryApiBaseClient`** - Abstract base class with common functionality
- RestClient creation and management
- URL encoding
- Error response handling
- Logging utilities

**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/client/base/LibraryApiBaseClient.java`

### 2. Specialized Clients (✅ Created)

#### LibraryBookSearchClient
**Responsibility**: Book search APIs
- `searchBooksByTitle(String title)`
- `searchBooksByIsbn(String isbn13)`
- `searchBooksByAuthor(String author)`
- `searchBooksByIsbn10(String isbn)`
- `searchBooksByPublisher(String publisher)`

**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/client/book/LibraryBookSearchClient.java`

#### LibraryLibrarySearchClient
**Responsibility**: Library search APIs
- `searchLibraries(String libraryName, String region)`
- `searchAllLibraries()`
- `searchLibrariesByRegion(String region)`
- `searchLibrariesByName(String name)`

**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/client/library/LibraryLibrarySearchClient.java`

#### LibraryLoanInfoClient
**Responsibility**: Loan information APIs
- `searchLoanItemsByIsbn(String isbn13, String region)`
- `searchLoanItemsByIsbn(String isbn13)`
- `searchLoanItemsByLibrary(String isbn13, String libraryName)`

**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/client/loan/LibraryLoanInfoClient.java`

### 3. Response Parsers (✅ Created)

#### BookSearchResponseParser
**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/response/BookSearchResponseParser.java`

#### LibrarySearchResponseParser
**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/response/LibrarySearchResponseParser.java`

#### LoanInfoResponseParser
**Location**: `src/main/java/com/nhnacademy/library/external/opennaru/response/LoanInfoResponseParser.java`

## Future Work

### Remaining Specialized Clients to Create

1. **LibraryBookDetailClient** - Book detail and analysis APIs
   - `getBookDetail(String isbn13, boolean includeLoanInfo)`
   - `getBookKeywords(String isbn13)`
   - `getUsageAnalysis(String isbn13)`

2. **LibraryPopularTrendClient** - Popular book and trend APIs
   - `getPopularBooks(String startDt, String endDt, String region)`
   - `getHotTrendBooks(String searchDate)`
   - `getMonthlyKeywords(String month)`
   - `getReadingQuantity(String region, String dtlRegion)`

3. **LibraryRecommendationClient** - Recommendation APIs
   - `getRecommendedBooks(String isbn13, String type)`
   - `getRecommendedBooksForMania(String isbn13)`
   - `getRecommendedBooksForReader(String isbn13)`

4. **LibraryExtendedApiClient** - Extended API integrations
   - `getLibraryExtendedInfo(int pageNo, int pageSize)`
   - `getExtendedPopularBooks(String libCode)`
   - `getUsageTrend(String libCode, String type)`

5. **LibraryExistClient** - Book existence check APIs
   - `checkBookExists(String libCode, String isbn13)`

## Migration Plan

### Phase 1: ✅ Completed
- Create base class and specialized clients for high-traffic APIs (book search, library search, loan info)
- Create response parsers for basic APIs
- Verify pattern works

### Phase 2: Future Work
- Create remaining specialized clients (detail, popular, recommendation, extended, exist)
- Create corresponding response parsers
- Add comprehensive unit tests for new clients

### Phase 3: Future Work
- Update existing code to use new specialized clients
- Keep `LibraryInfoNaruApiClient` as a facade for backward compatibility
- Add `@Deprecated` annotation to `LibraryInfoNaruApiClient`

### Phase 4: Future Work
- Remove deprecated `LibraryInfoNaruApiClient`
- All code uses specialized clients directly

## Benefits of Refactoring

1. **Single Responsibility Principle**: Each class handles one API domain
2. **Easier Testing**: Smaller classes are easier to unit test
3. **Better Maintainability**: Changes to one API domain don't affect others
4. **Clearer Code Organization**: Related functionality is grouped together
5. **Reduced Cognitive Load**: Developers only need to understand the specific client they're working with
6. **Parallel Development**: Multiple developers can work on different clients without conflicts

## Example Usage

### Current (Using Monolithic Client)
```java
@Autowired
private LibraryInfoNaruApiClient apiClient;

public List<LibraryBookInfo> searchBooks(String title) {
    return apiClient.searchBooksByTitle(title);
}
```

### Future (Using Specialized Client)
```java
@Autowired
private LibraryBookSearchClient bookSearchClient;

public List<LibraryBookInfo> searchBooks(String title) {
    return bookSearchClient.searchBooksByTitle(title);
}
```

## Design Patterns Used

1. **Template Method Pattern**: `LibraryApiBaseClient` provides common functionality
2. **Strategy Pattern**: Different response parsers for different API responses
3. **Facade Pattern**: Future `LibraryInfoNaruFacade` for backward compatibility

## Notes

- The refactoring was started but not completed due to the large size and complexity
- The pattern has been established and proven to work
- Specialized clients are ready to use for new features
- Future work can continue incrementally, API domain by API domain
