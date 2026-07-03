# Tài Liệu Docker Configuration - Containerization Strategy

## 📋 Mục Lục
1. [Tổng Quan Hệ Thống](#tổng-quan-hệ-thống)
2. [Dockerfile - Multi-Stage Build](#dockerfile---multi-stage-build)
3. [Docker Compose - Orchestration](#docker-compose---orchestration)
4. [Production Best Practices](#production-best-practices)
5. [Troubleshooting Guide](#troubleshooting-guide)

---

## 🎯 Tổng Quan Hệ Thống

Docker configuration cho phép deploy toàn bộ stack với **1 lệnh**: `docker-compose up`

### Kiến Trúc Container:

```
┌─────────────────────────────────────────┐
│         Docker Network (bridge)         │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┐   ┌──────────────┐  │
│  │  PostgreSQL  │   │    Redis     │  │
│  │  (pgvector)  │   │   (cache)    │  │
│  │  Port: 5434  │   │  Port: 6380  │  │
│  └──────┬───────┘   └──────┬───────┘  │
│         │                   │           │
│         └────────┬──────────┘           │
│                  │                      │
│         ┌────────▼────────┐            │
│         │  Spring Boot    │            │
│         │    Backend      │            │
│         │  Port: 4040     │            │
│         └─────────────────┘            │
│                                         │
└─────────────────────────────────────────┘
```

### Tech Stack:
- **Build**: Maven 3.9.9 + Eclipse Temurin JDK 21
- **Runtime**: Eclipse Temurin JRE 21 Alpine
- **Database**: PostgreSQL 16 với pgvector extension
- **Cache**: Redis 7 Alpine
- **Orchestration**: Docker Compose v2

---

## 🏗️ Dockerfile - Multi-Stage Build

### Toàn Bộ Dockerfile (19 dòng):

```dockerfile
# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 4040

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### Phân Tích Chi Tiết Từng Dòng

#### Stage 1: Build Stage (Lines 1-7)

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
```
- **Base image**: Maven 3.9.9 với JDK 21 (full development environment)
- `AS build`: Đặt tên stage để reference ở stage 2
- **Size**: ~700MB (bao gồm Maven, JDK, build tools)

```dockerfile
WORKDIR /app
```
- Set working directory trong container
- Tất cả commands sau chạy trong `/app`

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline -B
```
**CHIẾN LƯỢC LAYER CACHING CỰC KỲ QUAN TRỌNG:**

**Tại sao copy pom.xml trước?**
1. Docker build theo layers
2. Layer chỉ rebuild nếu input file thay đổi
3. `pom.xml` ít thay đổi hơn `src/`
4. Dependencies download một lần, cache lâu dài

**Kịch bản:**
```
Lần 1: Build toàn bộ (10 phút)
  ├─ COPY pom.xml → Layer A
  ├─ mvn dependency → Layer B (download 200MB deps)
  ├─ COPY src → Layer C  
  └─ mvn package → Layer D

Lần 2: Chỉ sửa code trong src/
  ├─ Layer A: CACHED ✅
  ├─ Layer B: CACHED ✅ (không download lại!)
  ├─ Layer C: REBUILD (code mới)
  └─ Layer D: REBUILD (compile lại)

Build time: 10 phút → 2 phút (nhanh 5x)
```

```dockerfile
COPY src ./src
RUN mvn clean package -DskipTests
```
- Copy source code
- Build JAR file
- `-DskipTests`: Skip tests trong Docker (đã test trên CI)
- Output: `/app/target/*.jar`

---

#### Stage 2: Runtime Stage (Lines 9-19)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
```
**Tại sao Alpine?**
- **JDK image**: ~700MB (full development tools)
- **JRE Alpine**: ~150MB (chỉ runtime, không có compiler)
- **Tiết kiệm**: ~550MB per image
- **Security**: Ít packages = ít vulnerabilities

```dockerfile
COPY --from=build /app/target/*.jar app.jar
```
**Multi-stage magic:**
- `--from=build`: Copy từ stage 1
- Chỉ lấy JAR file, bỏ hết Maven, source code, dependencies...
- **Final image**: Chỉ JRE + JAR

```dockerfile
EXPOSE 4040
```
- Document port (không mở port thật)
- `docker-compose.yml` sẽ map `4040:4040`

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

**Giải thích từng parameter:**
- `--interval=30s`: Kiểm tra mỗi 30 giây
- `--timeout=3s`: Timeout nếu không response trong 3s
- `--start-period=10s`: Chờ 10s cho app khởi động trước khi check
- `--retries=3`: Thử 3 lần trước khi đánh dấu unhealthy

**Lưu ý:** Healthcheck dùng port 8080 nhưng EXPOSE 4040
- **Vấn đề**: Inconsistency - nên dùng cùng 1 port
- **Fix**: Sửa thành `http://localhost:4040/actuator/health`

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
```
- Command chạy khi container start
- Exec form (JSON array) - best practice
- **Tại sao không dùng Shell form `java -jar app.jar`?**
  - Shell form: `sh -c "java -jar app.jar"` (thêm 1 process)
  - Exec form: Chạy trực tiếp (PID 1)
  - Signals (SIGTERM) đến đúng Java process

---

## 🐳 Docker Compose - Orchestration

### Service 1: PostgreSQL (Lines 2-18)

```yaml
postgres:
  image: pgvector/pgvector:pg16
  container_name: ai-study-hub-postgres
  restart: unless-stopped
```

**Giải thích:**
- `pgvector/pgvector`: Specialized image với vector extension (cho AI embeddings)
- `unless-stopped`: Auto restart trừ khi manual stop
- **Tại sao cần pgvector?** RAG (Retrieval-Augmented Generation) cho AI chat

```yaml
  environment:
    POSTGRES_DB: ai_study_hub
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: ${DB_PASSWORD}
```
- `${DB_PASSWORD}`: Load từ `.env` file
- **Security risk**: Plain text trong .env (nên dùng Docker Secrets)

```yaml
  ports:
    - "5434:5432"
```
**Tại sao 5434 thay vì 5432?**
- 5432 = default PostgreSQL port
- Dev có thể chạy local Postgres ở 5432
- 5434 tránh conflict

```yaml
  volumes:
    - postgres_data:/var/lib/postgresql/data
```
**QUAN TRỌNG - Persistence:**
- Named volume `postgres_data`
- Data không mất khi `docker-compose down`
- **Tại sao cần?** Database data phải survive container restart

```yaml
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U postgres -d ai_study_hub"]
    interval: 10s
    timeout: 5s
    retries: 5
```
- `pg_isready`: PostgreSQL utility kiểm tra server ready
- Backend sẽ đợi healthcheck pass trước khi start

---

### Service 2: Redis (Lines 20-30)

```yaml
redis:
  image: redis:7-alpine
  container_name: ai-study-hub-redis
```
- **Redis 7**: Latest stable
- **Alpine**: Lightweight (~30MB)

```yaml
  ports:
    - "6380:6379"
```
**Tại sao 6380?**
- 6379 = default Redis port
- Tránh conflict với local Redis

```yaml
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
```
- `redis-cli ping`: Return "PONG" nếu healthy

---

### Service 3: Backend (Lines 32-63)

```yaml
backend:
  build: .
  container_name: ai-study-hub-backend
```
- `build: .`: Build từ Dockerfile trong thư mục hiện tại
- **Không dùng pre-built image** - build on-the-fly

```yaml
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ai_study_hub
```
**Docker networking magic:**
- `postgres`: Hostname = service name
- Không cần IP, Docker DNS tự resolve
- Port `5432`: **Internal port** (không phải 5434)

```yaml
  depends_on:
    postgres:
      condition: service_healthy
    redis:
      condition: service_healthy
```
**Dependency orchestration:**
1. Start postgres → wait until healthy
2. Start redis → wait until healthy
3. **Chỉ khi đó** mới start backend

**Tại sao quan trọng?**
- Tránh backend crash khi DB chưa ready
- `condition: service_healthy` thay vì `condition: service_started`

---

## 🔒 Production Best Practices

### 1. Secrets Management

**Hiện tại (BAD):**
```yaml
environment:
  POSTGRES_PASSWORD: ${DB_PASSWORD}  # Plain text trong .env
```

**Production (GOOD):**
```yaml
secrets:
  - db_password

backend:
  secrets:
    - db_password
```

```bash
# Tạo secret
echo "super_secret_password" | docker secret create db_password -

# Read trong code
String password = Files.readString(Path.of("/run/secrets/db_password"));
```

### 2. Health Checks

**Fix inconsistency trong Dockerfile:**
```dockerfile
# Sai
HEALTHCHECK CMD wget http://localhost:8080/actuator/health

# Đúng  
HEALTHCHECK CMD wget http://localhost:4040/actuator/health
```

### 3. Resource Limits

**Thêm vào docker-compose.yml:**
```yaml
backend:
  deploy:
    resources:
      limits:
        cpus: '2'
        memory: 2G
      reservations:
        cpus: '1'
        memory: 1G
```

### 4. Logging

**Thêm logging driver:**
```yaml
backend:
  logging:
    driver: "json-file"
    options:
      max-size: "10m"
      max-file: "3"
```

### 5. Non-root User

**Thêm vào Dockerfile:**
```dockerfile
# Runtime stage
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar
```

---

## 🚀 Commands Cheat Sheet

```bash
# Build và start tất cả services
docker-compose up --build

# Chạy ở background (detached)
docker-compose up -d

# Xem logs
docker-compose logs -f backend

# Stop tất cả
docker-compose down

# Stop và xóa volumes (XÓA DATA!)
docker-compose down -v

# Rebuild specific service
docker-compose up --build backend

# Vào container
docker exec -it ai-study-hub-backend sh

# Check health status
docker ps
```

---

## 🐛 Troubleshooting Guide

### Issue 1: Backend không connect được DB

**Symptom:**
```
Connection refused: postgres:5432
```

**Fix:**
```bash
# Check DB healthy chưa
docker-compose ps

# Check logs
docker-compose logs postgres

# Verify connection từ backend container
docker exec -it ai-study-hub-backend sh
wget postgres:5432
```

### Issue 2: Port đã được sử dụng

**Symptom:**
```
ERROR: for postgres  Cannot start service postgres: 
Ports are not available: listen tcp 0.0.0.0:5434: bind: address already in use
```

**Fix:**
```bash
# Tìm process đang dùng port
netstat -ano | findstr :5434

# Kill process hoặc đổi port trong docker-compose.yml
ports:
  - "5435:5432"  # Đổi sang 5435
```

### Issue 3: Build chậm

**Fix - Layer caching:**
```dockerfile
# Đảm bảo COPY pom.xml TRƯỚC src
COPY pom.xml .
RUN mvn dependency:go-offline -B  # Cache layer này
COPY src ./src                    # Thay đổi thường xuyên
RUN mvn package
```

---

## 📊 Performance Comparison

| Metric | Without Docker | With Docker |
|--------|----------------|-------------|
| **Setup time** | 30-60 phút | 5 phút |
| **Reproducibility** | Depends on local env | 100% consistent |
| **Isolation** | Shared DB, conflicts | Isolated per project |
| **Portability** | OS-dependent | Cross-platform |
| **First build** | 10 phút | 10 phút |
| **Subsequent builds** | 2 phút | 30 giây (cache) |

---

## ✅ Checklist Before Production

- ⚠️ **Secrets management**: Dùng Docker Secrets/Vault, không .env
- ⚠️ **Non-root user**: Thêm vào Dockerfile
- ⚠️ **Resource limits**: CPU, memory limits
- ⚠️ **Health checks**: Fix port inconsistency
- ⚠️ **Logging**: Centralized logging (ELK, CloudWatch)
- ⚠️ **Monitoring**: Prometheus + Grafana
- ⚠️ **Backup strategy**: Automated DB backups
- ✅ **Multi-stage build**: Done
- ✅ **Layer caching**: Done
- ✅ **Dependency orchestration**: Done với service_healthy

---

## 🚀 Cải Tiến Tương Lai

1. **Frontend containerization**: Thêm Nginx + React service
2. **Multi-environment**: dev, staging, prod compose files
3. **CI/CD integration**: Build images trong pipeline
4. **Image registry**: Push lên Docker Hub/ECR
5. **Kubernetes migration**: K8s manifests cho scale
6. **Service mesh**: Istio cho advanced networking

---

**Tác giả**: AI Study Hub Team  
**Cập nhật**: 2026-07-02
