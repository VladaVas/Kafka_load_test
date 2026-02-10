package org.example.loadtestingvtb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class KafkaProcessor {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.topics.out}")
    private String outTopic;

    public KafkaProcessor(KafkaTemplate<String, String> kafkaTemplate,
                          ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.topics.in}",
            groupId = "stub-group",
            concurrency = "2"
    )
    public void listen(String message, Acknowledgment ack) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(message);

        String id = node.get("id").asText();
        node.put("id", id + "123");

        kafkaTemplate.send(outTopic, id, objectMapper.writeValueAsString(node));
        ack.acknowledge();
    }
}
