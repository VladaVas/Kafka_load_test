package org.example.loadtestingvtb;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"vtb-topic"})
public class LoadTestingVtbApplicationTests {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    // Буфер для полученных сообщений
    private static BlockingQueue<String> records = new LinkedBlockingQueue<>();

    @KafkaListener(topics = "vtb-topic", groupId = "vtb-group")
    public void listen(String message) {
        records.add(message);
    }

    @Test
    void testKafkaLoad() throws InterruptedException {
        int messageCount = 1000; // количество сообщений для нагрузки

        // Отправляем N сообщений
        for (int i = 0; i < messageCount; i++) {
            kafkaTemplate.send("vtb-topic", "message-" + i);
        }

        // Проверяем, что все сообщения дошли
        for (int i = 0; i < messageCount; i++) {
            String received = records.poll(5, TimeUnit.SECONDS);
            Assertions.assertNotNull(received, "Сообщение не получено: " + i);
        }
    }
}
