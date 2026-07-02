package com.nhnacademy.ailibraryteam5.core.review.service;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ReviewAiSummarizer {
    @Value("${app.review.map-chunk-size}")
    private int mapChunkSize;

    @Value("${app.review.reduce-threshold}")
    private int reduceThreshold;

    private final ChatClient chatClient;

    private final Executor taskExecutor;

    public ReviewAiSummarizer(ChatClient chatClient, @Qualifier("taskExecutor") Executor taskExecutor) {
        this.chatClient = chatClient;
        this.taskExecutor = taskExecutor;
    }


    // Map-Reduce - 비동기
    public String summarizeReviews(List<String> reviews){
        if(reviews == null || reviews.isEmpty()){
            return "리뷰가 없어 요약할 수 없습니다.";
        }

        List<List<String>> chunks = Lists.partition(reviews, mapChunkSize);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Map 모든 청크의 AI 요약 요청을 동시에 비동기로 발송
        for(int i = 0; i<chunks.size(); i++){
            List<String> chunk = chunks.get(i);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                String mapPrompt = createMapPrompt(chunk);
                return chatClient.prompt(mapPrompt).call().content();
            },taskExecutor);
            futures.add(future);
        }

        // 모든 스레드가 요약 다 할때까지 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> partialSummarizes = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        String reducePrompt = createReducePrompt(partialSummarizes);
        String finalSummary = chatClient.prompt(reducePrompt).call().content();

        return finalSummary;
    }


    // 누적 요약 - 비동기 병합
    public String summarizeIncremental(List<String> newReviews, String existingSummary){
        if(newReviews == null || newReviews.isEmpty()){
            return existingSummary != null ? existingSummary : "리뷰가 없어 요약할 수 없습니다.";
        }

        List<List<String>> chunks = Lists.partition(newReviews, mapChunkSize);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for(int i = 0; i<chunks.size(); i++){
            List<String> chunk = chunks.get(i);
            CompletableFuture<String> future = CompletableFuture.supplyAsync(()->{
                String mapPrompt = createMapPrompt(chunk);
                return chatClient.prompt(mapPrompt).call().content();
            }, taskExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<String> partialSummaries = futures.stream()
                .map(CompletableFuture::join)
                        .toList();


        String mergePrompt = createMergePrompt(existingSummary, partialSummaries);
        String finalSummary = chatClient.prompt(mergePrompt).call().content();
        return finalSummary;
    }




    private String createMergePrompt(String existingSummary, List<String> newPartialSummarize){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i< newPartialSummarize.size(); i++){
            sb.append("[새 요약]").append(i+1).append("]\n");
            sb.append(newPartialSummarize.get(i)).append("\n\n");
        }
        return String.format("""
                당신은 도서 리뷰 분석 전문가입니다.
                
                [기존 요약]
                %s
                
                [새로운 리뷰 요약본]
                %s
                
                위 두 내용을 통합해서 전체 리뷰를 종합한 '최종 요약'을 작성해주세요.
                기존 요약의 핵심 내용은 유지하면서, 새로운 정보를 추가해주세요.
                항상 [장점], [단점], [총평]의 형식을 유지하여 답변하세요.
                답변은 반드시 공손하고 친절한 어조(~입니다, ~하세요)를 사용하세요.
                """, existingSummary != null ? existingSummary : "없음",sb.toString());
    }

    // Map
    private String createMapPrompt(List<String> reviews){
        StringBuilder sb = new StringBuilder();
        for(String review : reviews){
            sb.append("- ").append(review).append("\n");
        }
        return String.format("""
                다음은 특정 도서에 대한 독자 리뷰들입니다.
                이 리뷰들에서 공통적으로 언급되는 [장점], [단점], [추천대상]을 각각 한 문장씩 요약하세요.
                
                리뷰 리스트:
                %s
                """, sb.toString());
    }

    // reduce
    private String createReducePrompt(List<String> partialSummarizes){
        StringBuilder sb = new StringBuilder();
        for(String summary : partialSummarizes){
            sb.append(summary).append("\n\n");
        }
        return String.format("""
                다음은 도서 리뷰들을 부분적으로 요약한 내용들입니다.
                이 내용들을 종합하여 사용자가 이 책을 살지 말지 결정하는데 도움이 되는 '최종 평판 요약'을 작성하세요.
                항상 [장점], [단점], [총평]의 형식을 유지하여 답변하세요.
                답변은 반드시 공손하고 친절한 어조(~입니다, ~하세요)를 사용하세요.
                
                요약 데이터:
                %s
                """, sb.toString());
    }

    public int getReduceThreshold(){
        return reduceThreshold;
    }


}


















// map - reduce 비동기 x
//    public String summarizeReviews(List<String> reviews){
//        if(reviews == null || reviews.isEmpty()){
//            return "리뷰가 없어 요약할 수 없습니다.";
//        }
//
//        StopWatch stopWatch = new StopWatch("AI 요약 성능 측정");
//        stopWatch.start("Map-Reduce 요약");

//        List<String> partialSummarizes = new ArrayList<>();
//        List<List<String>> chunks = Lists.partition(reviews, mapChunkSize);
//
//        for(int i = 0; i<chunks.size(); i++){
//            log.info("Map 단계 실행 중 : 청크 {}/{}", i +1, chunks.size());
//            String mapPrompt = createMapPrompt(chunks.get(i));
//            String summary = chatClient.call(mapPrompt);
//            partialSummarizes.add(summary);
//        }

//
//        // reduce 단계 -> 요약본 병합
//        String reducePrompt = createReducePrompt(partialSummarizes);
//        String finalSummary = chatClient.call(reducePrompt);


//        stopWatch.stop();
//        log.info("AI 요약 총 소요 시간: {} ms (약 {} 초)", stopWatch.getTotalTimeMillis(), String.format("%.2f", stopWatch.getTotalTimeSeconds()));

//        return finalSummary;
//    }








//    // 누적 요약 - 동기식 순차 병합
//    // 새 리뷰만 요약해서 기존 요약본과 병합
//    public String summarizeIncremental(List<String> newReviews, String existingSummary){
//        if(newReviews == null || newReviews.isEmpty()){
//            return existingSummary != null ? existingSummary : "리뷰가 없어 요약할 수 없습니다.";
//        }
//        StopWatch stopWatch = new StopWatch("AI 요약 성능 측정");
//        stopWatch.start("Map-Reduce 요약");
//
//        //[Map 단계] : 새 리뷰들을 10개씩 쪼개서 요약
//        List<String> partialSummaries = new ArrayList<>();
//        List<List<String>> chunks = Lists.partition(newReviews, mapChunkSize);
//
//        for(int i = 0; i<chunks.size(); i++){
//            log.info("Map 단계 실행 중 : 청크 {}/{}", i + 1, chunks.size());
//            String mapPrompt = createMapPrompt(chunks.get(i));
//            String summary = chatClient.call(mapPrompt);
//            partialSummaries.add(summary);
//        }
//
//        // Reduce 요약본 병합
//        String mergePrompt = createMergePrompt(existingSummary, partialSummaries);
//        String finalSummary = chatClient.call(mergePrompt);
//        stopWatch.stop();
//        log.info("AI 요약 총 소요 시간: {} ms (약 {} 초)", stopWatch.getTotalTimeMillis(), String.format("%.2f", stopWatch.getTotalTimeSeconds()));
//        return finalSummary;
//    }







