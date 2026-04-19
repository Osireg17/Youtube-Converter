package com.youtube.converter.jobservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CONVERSION_QUEUE    = "conversion.queue";
    public static final String CONVERSION_EXCHANGE = "conversion.exchange";
    public static final String ROUTING_KEY         = "conversion.key";

    @Bean
    public Queue queue() {
        return new Queue(CONVERSION_QUEUE, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(CONVERSION_EXCHANGE);
    }

    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
