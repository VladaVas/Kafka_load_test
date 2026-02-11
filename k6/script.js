import { check } from "k6";
import {
  Writer,
  SchemaRegistry,
  SCHEMA_TYPE_STRING,
} from "k6/x/kafka";

// безопасно читаем __ENV
const env = (typeof __ENV !== "undefined" && __ENV) || {};

// брокеры и топик: либо из env, либо дефолты
const brokers = String(env.KAFKA_BROKERS || "kafka:29092").split(",");
const topic = env.KAFKA_TOPIC || "load-topic";

// продюсер для Kafka
const writer = new Writer({
  brokers,
  topic,
});

// клиент для сериализации строк
const schemaRegistry = new SchemaRegistry();

export let options = {
  stages: [
    { duration: "1m", target: 5 },
    { duration: "1m", target: 10 },
  ],
};

export default function () {
  const message = {
    id: Math.floor(Math.random() * 1_000_000),
    about: "keep calm and do testing",
  };

  // сериализуем строку в байты так, как ожидает xk6-kafka
  const value = schemaRegistry.serialize({
    data: JSON.stringify(message),
    schemaType: SCHEMA_TYPE_STRING,
  });

  writer.produce({
    messages: [
      {
        value,
      },
    ],
  });

  check(message, {
    "id exists": (m) => m.id !== undefined,
  });
}