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

Install `ffmpeg` locally before starting `Converter_Service`.
Example: `brew install ffmpeg` on macOS or `sudo apt-get install ffmpeg` on Debian/Ubuntu.

### 4. Frontend

```bash
cd frontend
npm install
npm run dev
```

If `VITE_API_BASE_URL` is not set, the frontend dev server proxies `/api/*` to `http://localhost:8080`.

## Environment Variables

### Job_Service

| Variable | Description |
|---|---|
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/converter` |
| `RABBITMQ_URL` | AMQP URL, e.g. `amqp://user:pass@localhost:5672/` |
| `ACCESS_KEY_ID` or `AWS_ACCESS_KEY_ID` | S3 access key |
| `SECRET_ACCESS_KEY` or `AWS_SECRET_ACCESS_KEY` | S3 secret key |
| `REGION` or `AWS_DEFAULT_REGION` | S3 region |
| `ENDPOINT` or `AWS_ENDPOINT_URL` | S3 endpoint override (for Railway Object Storage or MinIO) |
| `BUCKET_NAME`, `BUCKET`, or `AWS_S3_BUCKET_NAME` | S3 bucket name |
| `ALLOWED_ORIGINS` or `CORS_ALLOWED_ORIGINS` | Comma-separated CORS origins, e.g. `https://my-app.railway.app` |

### Converter_Service

| Variable | Description |
|---|---|
| `DATABASE_URL` | PostgreSQL URL, e.g. `postgresql://user:pass@localhost:5432/converter` |
| `RABBITMQ_URL` | AMQP URL |
| `ACCESS_KEY_ID` or `AWS_ACCESS_KEY_ID` | S3 access key |
| `SECRET_ACCESS_KEY` or `AWS_SECRET_ACCESS_KEY` | S3 secret key |
| `REGION` or `AWS_DEFAULT_REGION` | S3 region |
| `ENDPOINT` or `AWS_ENDPOINT_URL` | S3 endpoint override |
| `BUCKET_NAME`, `BUCKET`, or `AWS_S3_BUCKET_NAME` | S3 bucket name |

## Deploying to Railway

Each directory (`Job_Service`, `Converter_Service`, `frontend`) is a separate Railway service. Required services:

- PostgreSQL (Railway plugin)
- RabbitMQ (Railway plugin or self-hosted)
- Object Storage (Railway plugin)

Set the environment variables listed above on each service. For the frontend, set `VITE_API_BASE_URL` if you are not using a reverse proxy to route `/api/*` requests.
`Converter_Service` is deployed from a Dockerfile that installs `ffmpeg`, which is required for MP3 extraction and MP4 muxing in production.
For this repository's Railway deployment, the frontend should call the Job Service directly by setting:

```bash
VITE_API_BASE_URL=https://job-production-ce60.up.railway.app
```

Because the browser calls `Job_Service` directly in production, `Job_Service` must include every deployed frontend origin in `ALLOWED_ORIGINS` or `CORS_ALLOWED_ORIGINS`. For the current production frontend, set:

```bash
CORS_ALLOWED_ORIGINS=https://youtube-converter.up.railway.app
```

If you add preview or staging frontends, append them as a comma-separated list:

```bash
CORS_ALLOWED_ORIGINS=https://youtube-converter.up.railway.app,https://youtube-converter-preview.up.railway.app
```

After updating Railway env vars and redeploying `Job_Service`, verify the browser preflight succeeds before testing the UI:

```bash
curl -i -X OPTIONS 'https://job-production-ce60.up.railway.app/api/jobs' \
  -H 'Origin: https://youtube-converter.up.railway.app' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

Expected result:

- No `403 Invalid CORS request`
- `Access-Control-Allow-Origin: https://youtube-converter.up.railway.app`

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
