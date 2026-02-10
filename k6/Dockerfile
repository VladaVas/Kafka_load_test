# Stage 1: Build k6 with xk6-kafka
FROM golang:1.25 as builder

# Install xk6
RUN go install go.k6.io/xk6/cmd/xk6@latest

# Build k6 with Kafka extension
RUN xk6 build --output /k6 \
  --with github.com/mostafa/xk6-kafka@latest

# Stage 2: Final lightweight image
FROM debian:bookworm-slim

COPY --from=builder /k6 /usr/bin/k6

RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /scripts
COPY script.js /scripts/script.js

ENTRYPOINT ["sleep","infinity"]
