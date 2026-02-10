package org.example.loadtestingvtb;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    @KafkaListener(topics = "request-topic", groupId = "vtb-group")
    public void listen(String message) {
        System.out.println("Получено сообщение от k6: " + message);
    }
}

