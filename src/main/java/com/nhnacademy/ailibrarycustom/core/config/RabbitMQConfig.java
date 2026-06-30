package com.nhnacademy.ailibrarycustom.core.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Value("${rabbitmq.queue.review-summary}")
    private String reviewSummaryQueueName;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key.review-summary}")
    private String routingKey;

    /**
     * AI 요약 일감이 줄을 서서 대기할 큐(Queue) 선언
     */
    @Bean
    public Queue reviewSummaryQueue(){
        return QueueBuilder.durable(reviewSummaryQueueName).build();
    }

    /**
     * 일감을 주소에 맞게 정확히 분류해 줄 Direct Exchange 선언
     */
    @Bean
    public DirectExchange exchange(){
        return new DirectExchange(exchange, true, false);
    }

    /**
     * 분류기(Exchange)와 편지봉투 주소("review.summary")를 통해 큐를
     바인딩(연결)
     */
    @Bean
    public Binding binding(Queue reviewSummaryQueue, DirectExchange exchange){
        return BindingBuilder.bind(reviewSummaryQueue).to(exchange).with(routingKey);
    }

    /**
     * 자바 객체를 JSON 텍스트로 바꾸어 던질 수 있게 돕는 번역기 빈
     */
    @Bean
    public MessageConverter jsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 번역기(JSON 변환)가 주입된 스프링 큐 발송 도구(RabbitTemplate)
     정의
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory){
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
