import glob
import json
import logging
import os
import shutil
import tempfile
import time

import boto3
import pika
import psycopg2
import yt_dlp

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

QUEUE_NAME    = "conversion.queue"
EXCHANGE_NAME = "conversion.exchange"
ROUTING_KEY   = "conversion.key"

STATUS_PROCESSING = 1
STATUS_DONE       = 2
STATUS_FAILED     = 3

AUDIO_BITRATE = "192"


def get_env(*names: str, default: str | None = None) -> str:
    for name in names:
        value = os.environ.get(name)
        if value:
            return value

    if default is not None:
        return default

    raise KeyError(names[0])


def get_db_connection():
    return psycopg2.connect(os.environ["DATABASE_URL"])


def get_s3_client():
    kwargs = {
        "aws_access_key_id": get_env("ACCESS_KEY_ID", "AWS_ACCESS_KEY_ID"),
        "aws_secret_access_key": get_env("SECRET_ACCESS_KEY", "AWS_SECRET_ACCESS_KEY"),
        "region_name": get_env("REGION", "AWS_DEFAULT_REGION"),
    }
    endpoint = get_env("ENDPOINT", "AWS_ENDPOINT_URL", default="")
    if endpoint:
        kwargs["endpoint_url"] = endpoint
    return boto3.client("s3", **kwargs)


def update_job_status(job_id: str, status: int, storage_object_key: str = None) -> None:
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            if storage_object_key is not None:
                cur.execute(
                    "UPDATE jobs SET status = %s, storage_object_key = %s, updated_at = NOW() WHERE id = %s::uuid",
                    (status, storage_object_key, job_id),
                )
            else:
                cur.execute(
                    "UPDATE jobs SET status = %s, updated_at = NOW() WHERE id = %s::uuid",
                    (status, job_id),
                )
        conn.commit()
        logger.info("Updated job %s to status %s", job_id, status)
    finally:
        conn.close()


def build_ydl_opts(output_format: str, output_dir: str) -> dict:
    outtmpl = os.path.join(output_dir, "%(id)s.%(ext)s")
    if output_format == "MP3":
        return {
            "format": "bestaudio/best",
            "outtmpl": outtmpl,
            "postprocessors": [{
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": AUDIO_BITRATE,
            }],
            "quiet": True,
            "no_warnings": True,
        }
    return {
        "format": "bestvideo*+bestaudio/best",
        "outtmpl": outtmpl,
        "merge_output_format": "mp4",
        "quiet": True,
        "no_warnings": True,
    }


def download_media(youtube_url: str, output_format: str, output_dir: str) -> str:
    opts = build_ydl_opts(output_format, output_dir)
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([youtube_url])

    ext = "mp3" if output_format == "MP3" else "mp4"
    matches = glob.glob(os.path.join(output_dir, f"*.{ext}"))
    if not matches:
        raise FileNotFoundError(f"yt-dlp produced no .{ext} file in {output_dir}")
    return matches[0]


def upload_to_s3(local_path: str, job_id: str, output_format: str) -> str:
    ext = "mp3" if output_format == "MP3" else "mp4"
    object_key = f"conversions/{job_id}.{ext}"
    content_type = "audio/mpeg" if output_format == "MP3" else "video/mp4"
    bucket = get_env("BUCKET_NAME", "BUCKET", "AWS_S3_BUCKET_NAME")
    s3 = get_s3_client()
    s3.upload_file(local_path, bucket, object_key, ExtraArgs={"ContentType": content_type})
    logger.info("Uploaded job %s to s3://%s/%s", job_id, bucket, object_key)
    return object_key


def process_job(job_id: str, youtube_url: str, output_format: str) -> None:
    logger.info("Starting job %s url=%s format=%s", job_id, youtube_url, output_format)
    update_job_status(job_id, STATUS_PROCESSING)
    tmp_dir = tempfile.mkdtemp(prefix=f"converter_{job_id}_")
    try:
        local_path = download_media(youtube_url, output_format, tmp_dir)
        object_key = upload_to_s3(local_path, job_id, output_format)
        update_job_status(job_id, STATUS_DONE, storage_object_key=object_key)
        logger.info("Completed job %s object_key=%s", job_id, object_key)
    except yt_dlp.utils.DownloadError as e:
        logger.error("yt-dlp failed for job %s: %s", job_id, e)
        update_job_status(job_id, STATUS_FAILED)
    except Exception as e:
        logger.error("Unexpected error for job %s: %s", job_id, e)
        update_job_status(job_id, STATUS_FAILED)
    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)
        logger.info("Cleaned up %s", tmp_dir)


def _on_message(channel, method, properties, body) -> None:
    try:
        payload = json.loads(body)
        job_id        = payload["jobId"]
        youtube_url   = payload["youtubeUrl"]
        output_format = payload["outputFormat"]
        if output_format not in ("MP3", "MP4"):
            raise ValueError(f"Unknown outputFormat: {output_format}")
        logger.info("Received message job_id=%s", job_id)
    except (json.JSONDecodeError, KeyError, ValueError) as e:
        logger.error("Malformed message, discarding: %s", e)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        return

    try:
        process_job(job_id, youtube_url, output_format)
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as e:
        logger.error("process_job raised unhandled exception for job_id=%s: %s", job_id, e)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def start_rabbitmq_consumer() -> None:
    while True:
        try:
            rabbitmq_url = os.environ["RABBITMQ_URL"]
            params = pika.URLParameters(rabbitmq_url)
            connection = pika.BlockingConnection(params)
            channel = connection.channel()

            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type="direct", durable=True)
            channel.queue_declare(queue=QUEUE_NAME, durable=True)
            channel.queue_bind(queue=QUEUE_NAME, exchange=EXCHANGE_NAME, routing_key=ROUTING_KEY)

            channel.basic_qos(prefetch_count=1)
            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=_on_message)
            logger.info("Consumer ready, waiting for messages on %s", QUEUE_NAME)
            channel.start_consuming()
        except pika.exceptions.AMQPConnectionError as e:
            logger.warning("RabbitMQ connection lost: %s. Retrying in 5s...", e)
            time.sleep(5)
        except Exception as e:
            logger.error("Consumer crashed: %s. Retrying in 5s...", e)
            time.sleep(5)
