package com.hznan.mamgareader.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "manga.translate.exchange";
    public static final String QUEUE = "manga.translate.queue";
    public static final String ROUTING_KEY = "manga.translate";
    public static final String DLQ = "manga.translate.dlq";

    public static final String LLM_QUEUE = "manga.translate.hq.queue";
    public static final String LLM_ROUTING_KEY = "manga.translate.hq";
    public static final String LLM_DLQ = "manga.translate.hq.dlq";

    @Bean
    public DirectExchange translateExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue translateQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding translateBinding(Queue translateQueue, DirectExchange translateExchange) {
        return BindingBuilder.bind(translateQueue).to(translateExchange).with(ROUTING_KEY);
    }

    @Bean
    public Queue llmTranslateQueue() {
        return QueueBuilder.durable(LLM_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", LLM_DLQ)
                .build();
    }

    @Bean
    public Queue llmDeadLetterQueue() {
        return QueueBuilder.durable(LLM_DLQ).build();
    }

    @Bean
    public Binding llmTranslateBinding(Queue llmTranslateQueue, DirectExchange translateExchange) {
        return BindingBuilder.bind(llmTranslateQueue).to(translateExchange).with(LLM_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
