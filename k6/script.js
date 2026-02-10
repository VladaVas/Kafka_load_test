import { check } from "k6";
import { Producer } from "k6/x/kafka";

// Подключаемся к Kafka через переменную окружения
const producer = new Producer({
    brokers: (__ENV.BROKERS || "kafka-load-test-kafka-1:9092").split(','),
});

const topic = "input-topic";

export let options = {
    stages: [
        { duration: "1m", target: 5 },  // 5 пользователей/итераций
        { duration: "1m", target: 10 }, // 10 пользователей/итераций
    ],
};

export default function () {
    const message = {
        id: Math.floor(Math.random() * 1_000_000),
        uuid: crypto.randomUUID(),
        isActive: Math.random() < 0.5,
        about: "keep calm and do testing",
    };

    producer.produce({
        topic: topic,
        value: JSON.stringify(message),
    });

    check(message, {
        "id exists": (m) => m.id !== undefined,
        "uuid exists": (m) => m.uuid !== undefined,
    });
}
