package org.example.loadtestingvtb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    @KafkaListener(topics = "${app.topics.out}", groupId = "stub-debug-consumer")
    public void listen(String message) {
        log.info("Получено сообщение из reply-topic: {}", message);
    }
}