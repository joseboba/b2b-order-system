# ── Stage 1: Build ───────────────────────────────────────────────────────────
# Full Go toolchain to compile a statically-linked binary.
# Modules are downloaded in a separate layer to exploit Docker layer caching.
FROM golang:1.26-alpine AS builder

WORKDIR /app

# Download modules first (cached unless go.mod / go.sum change)
COPY go.mod go.sum ./
RUN go mod download

# -ldflags="-s -w" strips debug symbols and DWARF info → ~30% smaller binary
# CGO_ENABLED=0 produces a fully static binary with no libc dependency
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o products-api .

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Minimal Alpine image — the Go binary is self-contained and needs no runtime.
# wget is included only for the Docker Compose healthcheck probe.
FROM alpine:3.20

RUN apk --no-cache add wget

# Run as a non-root system user to reduce the container's attack surface
RUN addgroup -S appgroup && adduser -S -G appgroup appuser

WORKDIR /app
COPY --from=builder /app/products-api .
RUN chown appuser:appgroup products-api

USER appuser
EXPOSE 8081
CMD ["./products-api"]
