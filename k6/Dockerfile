FROM golang:1.25 AS builder
RUN go install go.k6.io/xk6/cmd/xk6@latest
RUN xk6 build --output /k6 \
  --with github.com/mostafa/xk6-kafka@latest
FROM debian:bookworm-slim
COPY --from=builder /k6 /usr/bin/k6
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /scripts
COPY script.js /scripts/script.js
ENTRYPOINT ["sleep", "infinity"]