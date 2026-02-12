Тестовое задание по нагрузочному тестированию Kafka с использованием:
- Docker Compose
- k6 + xk6-kafka
- Spring Boot stub-сервиса
- Prometheus + Grafana + InfluxDB
___

## Архитектура стенда

Весь стенд описан в `docker-compose.yml` и поднимается одной командой.

### Сервисы

- **zookeeper:**
  - Координатор для Kafka.
  - Порт: `2181`.

- **kafka:**
  - Брокер Apache Kafka;
  - Внешний порт: `9092` (для клиентов с хост-машины);
  - Внутренний порт: `29092` (для контейнеров по адресу `kafka:29092`);
  - Настройки listeners/advertised listeners позволяют одинаково работать как из Docker-сети, так и с хоста.

- **kafka-init:**
  - Временный контейнер, который ждёт готовности Kafka и создаёт два топика:
    - `load-topic` — входной топик (куда пишет k6),
    - `reply-topic` — выходной топик (куда пишет stub).
  - После создания топиков завершает работу.

- **kafka-ui:**
  - Веб-интерфейс для Kafka;
  - Доступ: `http://localhost:8088`;
  - Позволяет смотреть список топиков, сообщения, consumer groups.

- **stub:**
  - Spring Boot сервис;
  - Слушает Kafka и обрабатывает сообщения;
  - Читает сообщения из `load-topic`, модифицирует их и пишет в `reply-topic`;
  - Отдаёт метрики по адресу `/actuator/prometheus`.

- **prometheus:**
  - Система сбора метрик;
  - Конфигурация лежит в `prometheus/prometheus.yml`;
  - Периодически опрашивает `stub:8080/actuator/prometheus` и сохраняет метрики приложения.

- **influxdb:**
  - Хранилище метрик от k6.
  - При запуске теста с флагом `--out influxdb=http://influxdb:8086/k6` k6 пишет свои метрики в БД `k6`.

- **grafana:**
  - Визуализация метрик;
  - Доступ: `http://localhost:3000` (логин/пароль: `admin` / `admin`);
  - Datasources настраиваются через файлы:
    - `grafana/provisioning/datasources/datasources.yml` — подключение Prometheus и InfluxDB;
    - `grafana/provisioning/dashboards/dashboards.yml` — автоподключение дашбордов (можно положить JSON-дешборды в эту папку).

- **k6:**
  - Контейнер с k6 и расширением `xk6-kafka`;
  - Использует сценарий `k6/script.js` для генерации нагрузки (отправки сообщений в Kafka).

---

## Поток данных (end‑to‑end сценарий)

1. **k6** выполняет скрипт `k6/script.js`:
   - создаёт сообщения (JSON с полями `id`, `about` и т.д.);
   - сериализует их в формат, который ожидает `xk6-kafka`;
   - отправляет их в Kafka в топик `load-topic` на брокер `kafka:29092`.

2. **stub**:
   - с помощью `@KafkaListener` читает сообщения из топика `load-topic`;
   - парсит JSON, модифицирует данные (например, изменяет поле `id`);
   - с помощью `KafkaTemplate` отправляет результат в топик `reply-topic`;
   - вручную подтверждает обработку (`Acknowledgment.acknowledge()`).

3. **Kafka UI**:
   - позволяет убедиться, что:
     - в `load-topic` действительно приходят сообщения от k6;
     - в `reply-topic` появляются изменённые сообщения от stub.

4. **Метрики**:
   - Prometheus собирает метрики Spring Boot stub (через `/actuator/prometheus`).
   - k6 по желанию отправляет свои метрики в InfluxDB (`--out influxdb=http://influxdb:8086/k6`).
   - Grafana читает данные из Prometheus и InfluxDB и рисует графики.

---

## Конфигурация приложения (stub)

Основная конфигурация находится в `stub/src/main/resources/application.yaml`:

**Kafka**:
  - `spring.kafka.bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:kafka:29092}`
  - `spring.kafka.consumer.group-id: stub-group`
  - `spring.kafka.consumer.auto-offset-reset: earliest`
  - `spring.kafka.consumer.enable-auto-commit: false`
  - `spring.kafka.producer.acks: all`

  **Сервер**:
  - `server.port: 8080`
  - `server.address: 0.0.0.0` (доступен внутри Docker-сети)

  **Бизнес-настройки**:
  - `app.topics.in: load-topic` — входной топик обработчика.
  - `app.topics.out: reply-topic` — выходной топик обработчика.

  **Actuator / Prometheus**:
  - Открыты эндпоинты `health`, `info`, `metrics`, `prometheus`.
  - Включён export в Prometheus.

### Основные классы

- `StubApplication` — основной класс Spring Boot (`@SpringBootApplication`).
- `AppConfig` — конфиг с бином `ObjectMapper` для JSON.
- `KafkaProcessor`:
  - `@KafkaListener(topics = "${app.topics.in}", groupId = "stub-group", concurrency = "2")`
  - полученное сообщение парсится через `ObjectMapper`;
  - модифицируется содержимое (например, меняется `id`);
  - записывается в `reply-topic` через `KafkaTemplate<String, String>`;
  - подтверждается offset через `Acknowledgment`.

---

## Конфигурация k6 и xk6-kafka

Файл `k6/script.js` описывает сценарий нагрузки.

### Главные моменты:

- **Импорт:**
  `import { Writer, SchemaRegistry, SCHEMA_TYPE_STRING } from "k6/x/kafka"`;

- **Чтение окружения:**
 
 - `const env = (typeof __ENV !== "undefined" && __ENV) || {}`;
 - `const brokers = String(env.KAFKA_BROKERS || "kafka:29092").split(",")`;
 - `const topic = env.KAFKA_TOPIC || "load-topic"`;

- **Создание продюсера:**

 - `const writer = new Writer({    brokers,    topic,  })`;

- **Сценарий нагрузки:**

 - `export let options = {    stages: [      { duration: "1m", target: 5 },      { duration: "1m", target: 10 },    ],  }`;
 
### Основная функция:

- Строит объект message с нужными полями;
- Сериализует его в строку и далее в байты через `SchemaRegistry.serialize` с `SCHEMA_TYPE_STRING`;
- Отправляет через `writer.produce({ messages: [...] })`.

### xk6-kafka интегрируется с k6 и предоставляет:

- Объекты `Writer`, `Reader`, `Connection`, `SchemaRegistry`;
- Собственные метрики Kafka (ошибки продюсера/консьюмера и т.п.).

___

## Prometheus и Grafana:

**Prometheus:**

Файл prometheus/prometheus.yml:

  `global:
    scrape_interval: 15s
   scrape_configs:
    - job_name: "stub"
      metrics_path: /actuator/prometheus
      static_configs:
        - targets: ["stub:8080"]`

**Grafana:**

`grafana/provisioning/datasources/datasources.yml`:

 - `datasource Prometheus (http://prometheus:9090)`;
 - `datasource InfluxDB-k6 (http://influxdb:8086, база k6)`.

`grafana/provisioning/dashboards/dashboards.yml`:

- Провайдер для дашбордов (можно добавлять JSON-файлы дашбордов в эту директорию).

___

## Как запустить стенд и тест:

1. **Собрать stub**
 - `cd stub`
 - `mvn clean package -DskipTests`
 - `cd ..`

2. **Поднять все сервисы**
 Из корня проекта:
 - `docker compose build`
 - `docker compose up -d`
 - `docker compose ps`
Kafka и stub должны быть в состоянии Up.

3. **Запустить тест k6**
Простой запуск без InfluxDB:
 - `docker compose exec k6 k6 run /scripts/script.js`
Запуск с записью метрик в InfluxDB:
 - `docker compose exec k6 k6 run /scripts/script.js --out influxdb=http://influxdb:8086/k6`
При высокой нагрузке InfluxDB может вернуть `413 Request Entity Too Large` — для демонстрации достаточно уменьшить длительность (`--duration 10s`) и количество VUs.

4. **Проверка работы**
Логи stub:
 - `docker compose logs -f stub`
Kafka UI:
 - `http://localhost:8088`

### Проверить, что:

 - в `load-topic` приходят сообщения от k6;
 - в `reply-topic` появляются обработанные сообщения от stub.

Prometheus:
`http://localhost:9090`

Grafana:
`http://localhost:3000`
(логин/пароль: admin / admin).

___

## Итог

Этот репозиторий — готовый мини-стенд для нагрузочного тестирования Kafka и потребляющего сервиса:
 - k6 генерирует нагрузку;
 - Kafka принимает и маршрутизирует сообщения;
 - Spring Boot stub обрабатывает поток;
 - Prometheus и Grafana позволяют наблюдать за состоянием сервиса;
 - InfluxDB хранит метрики k6.
На базе этого проекта можно экспериментировать с профилями нагрузки, бизнес-логикой обработчика и визуализацией метрик.
  
