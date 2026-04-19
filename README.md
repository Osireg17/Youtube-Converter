# YouTube Converter

Convert any YouTube video to MP4 or MP3.

## Architecture

```
Browser → Job_Service (Spring Boot) → RabbitMQ → Converter_Service (Python/FastAPI)
                  ↕                                        ↕
             PostgreSQL                               S3-compatible storage
```

- **Job_Service** — REST API. Accepts conversion requests, persists jobs to Postgres, publishes to RabbitMQ, and generates presigned S3 download URLs.
- **Converter_Service** — Background worker. Consumes from RabbitMQ, downloads via yt-dlp, transcodes via FFmpeg, uploads to S3, updates job status in Postgres.
- **Frontend** — React/TypeScript SPA. Submits jobs and polls for status.

## Local Development

### Prerequisites

- Docker & Docker Compose
- Java 21
- Python 3.12
- Node.js 20+

### 1. Start infrastructure

```bash
cd infra
cp .env.example .env  # fill in values
docker compose up -d
```

### 2. Job_Service

```bash
cd Job_Service
# Set env vars (see Environment Variables below)
./mvnw spring-boot:run
```

### 3. Converter_Service

```bash
cd Converter_Service
cp .env.example .env  # fill in values
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python main.py
```

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend dev server proxies `/api/*` to `http://localhost:8080`.

## Environment Variables

### Job_Service

| Variable | Description |
|---|---|
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/converter` |
| `RABBITMQ_URL` | AMQP URL, e.g. `amqp://user:pass@localhost:5672/` |
| `ACCESS_KEY_ID` | S3 access key |
| `SECRET_ACCESS_KEY` | S3 secret key |
| `REGION` | S3 region |
| `ENDPOINT` | S3 endpoint override (for Railway Object Storage or MinIO) |
| `BUCKET_NAME` | S3 bucket name |
| `ALLOWED_ORIGINS` | Comma-separated CORS origins, e.g. `https://my-app.railway.app` |

### Converter_Service

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL URL, e.g. `postgresql://user:pass@localhost:5432/converter` |
| `RABBITMQ_URL` | AMQP URL |
| `ACCESS_KEY_ID` | S3 access key |
| `SECRET_ACCESS_KEY` | S3 secret key |
| `REGION` | S3 region |
| `ENDPOINT` | S3 endpoint override |
| `BUCKET_NAME` | S3 bucket name |

## Deploying to Railway

Each directory (`Job_Service`, `Converter_Service`, `frontend`) is a separate Railway service. Required services:

- PostgreSQL (Railway plugin)
- RabbitMQ (Railway plugin or self-hosted)
- Object Storage (Railway plugin)

Set the environment variables listed above on each service. For the frontend, set `VITE_API_BASE_URL` if you are not using a reverse proxy to route `/api/*` requests.

## API

### `POST /api/jobs`

Create a conversion job.

**Request**
```json
{ "youtubeUrl": "https://www.youtube.com/watch?v=...", "outputFormat": "MP4" }
```

**Response** `201 Created`
```json
{ "jobId": "uuid" }
```

### `GET /api/jobs/{id}/status`

Poll job status.

**Response** `200 OK`
```json
{ "jobId": "uuid", "status": "DONE", "downloadUrl": "https://..." }
```

Possible statuses: `PENDING`, `PROCESSING`, `DONE`, `FAILED`.
