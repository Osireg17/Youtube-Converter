package com.youtube.converter.jobservice.messaging;

import com.youtube.converter.jobservice.config.RabbitMQConfig;
import com.youtube.converter.jobservice.dto.ConversionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobPublisher {

    private static final Logger log = LoggerFactory.getLogger(JobPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public JobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ConversionMessage message) {
        log.debug("Publishing message to exchange={} routingKey={} jobId={}",
                RabbitMQConfig.CONVERSION_EXCHANGE, RabbitMQConfig.ROUTING_KEY, message.jobId());

        rabbitTemplate.convertAndSend(
            RabbitMQConfig.CONVERSION_EXCHANGE,
            RabbitMQConfig.ROUTING_KEY,
            message
        );
    }
}
