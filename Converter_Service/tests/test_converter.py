import glob
import json
import os
import shutil
import tempfile
from unittest.mock import MagicMock, call, patch

import pytest
import yt_dlp

import converter


# ---------------------------------------------------------------------------
# build_ydl_opts
# ---------------------------------------------------------------------------

def test_build_ydl_opts_mp3():
    opts = converter.build_ydl_opts("MP3", "/tmp/test")
    assert opts["format"] == "bestaudio/best"
    assert opts["outtmpl"].startswith("/tmp/test/")
    assert len(opts["postprocessors"]) == 1
    pp = opts["postprocessors"][0]
    assert pp["key"] == "FFmpegExtractAudio"
    assert pp["preferredcodec"] == "mp3"
    assert pp["preferredquality"] == converter.AUDIO_BITRATE


def test_build_ydl_opts_mp4():
    opts = converter.build_ydl_opts("MP4", "/tmp/test")
    assert opts["format"] == "bestvideo*+bestaudio/best"
    assert opts["merge_output_format"] == "mp4"
    assert "postprocessors" not in opts


# ---------------------------------------------------------------------------
# download_media
# ---------------------------------------------------------------------------

def test_download_media_returns_path(tmp_path):
    fake_file = tmp_path / "dQw4w9WgXcQ.mp3"
    fake_file.write_text("fake audio")

    mock_ydl = MagicMock()
    mock_ydl.__enter__ = lambda s: s
    mock_ydl.__exit__ = MagicMock(return_value=False)

    with patch("yt_dlp.YoutubeDL", return_value=mock_ydl):
        result = converter.download_media("https://youtube.com/watch?v=x", "MP3", str(tmp_path))

    assert result.endswith(".mp3")


def test_download_media_raises_file_not_found(tmp_path):
    mock_ydl = MagicMock()
    mock_ydl.__enter__ = lambda s: s
    mock_ydl.__exit__ = MagicMock(return_value=False)

    with patch("yt_dlp.YoutubeDL", return_value=mock_ydl):
        with pytest.raises(FileNotFoundError):
            converter.download_media("https://youtube.com/watch?v=x", "MP4", str(tmp_path))


def test_download_media_propagates_download_error(tmp_path):
    mock_ydl = MagicMock()
    mock_ydl.__enter__ = lambda s: s
    mock_ydl.__exit__ = MagicMock(return_value=False)
    mock_ydl.download.side_effect = yt_dlp.utils.DownloadError("network error")

    with patch("yt_dlp.YoutubeDL", return_value=mock_ydl):
        with pytest.raises(yt_dlp.utils.DownloadError):
            converter.download_media("https://youtube.com/watch?v=x", "MP3", str(tmp_path))


# ---------------------------------------------------------------------------
# upload_to_s3
# ---------------------------------------------------------------------------

@patch.dict(os.environ, {
    "BUCKET": "test-bucket",
    "ACCESS_KEY_ID": "key",
    "SECRET_ACCESS_KEY": "secret",
    "REGION": "us-east-1",
    "ENDPOINT": "",
})
def test_upload_to_s3_mp4_key_and_content_type():
    mock_s3 = MagicMock()
    with patch("boto3.client", return_value=mock_s3):
        key = converter.upload_to_s3("/tmp/fake.mp4", "test-uuid", "MP4")

    assert key == "conversions/test-uuid.mp4"
    mock_s3.upload_file.assert_called_once_with(
        "/tmp/fake.mp4",
        "test-bucket",
        "conversions/test-uuid.mp4",
        ExtraArgs={"ContentType": "video/mp4"},
    )


@patch.dict(os.environ, {
    "BUCKET": "test-bucket",
    "ACCESS_KEY_ID": "key",
    "SECRET_ACCESS_KEY": "secret",
    "REGION": "us-east-1",
    "ENDPOINT": "",
})
def test_upload_to_s3_mp3_key_and_content_type():
    mock_s3 = MagicMock()
    with patch("boto3.client", return_value=mock_s3):
        key = converter.upload_to_s3("/tmp/fake.mp3", "test-uuid", "MP3")

    assert key == "conversions/test-uuid.mp3"
    mock_s3.upload_file.assert_called_once_with(
        "/tmp/fake.mp3",
        "test-bucket",
        "conversions/test-uuid.mp3",
        ExtraArgs={"ContentType": "audio/mpeg"},
    )


@patch.dict(os.environ, {
    "AWS_S3_BUCKET_NAME": "aws-test-bucket",
    "AWS_ACCESS_KEY_ID": "aws-key",
    "AWS_SECRET_ACCESS_KEY": "aws-secret",
    "AWS_DEFAULT_REGION": "auto",
    "AWS_ENDPOINT_URL": "https://t3.storageapi.dev",
}, clear=True)
def test_upload_to_s3_uses_aws_prefixed_env_names():
    mock_s3 = MagicMock()
    with patch("boto3.client", return_value=mock_s3):
        key = converter.upload_to_s3("/tmp/fake.mp3", "aws-uuid", "MP3")

    assert key == "conversions/aws-uuid.mp3"
    mock_s3.upload_file.assert_called_once_with(
        "/tmp/fake.mp3",
        "aws-test-bucket",
        "conversions/aws-uuid.mp3",
        ExtraArgs={"ContentType": "audio/mpeg"},
    )


# ---------------------------------------------------------------------------
# update_job_status
# ---------------------------------------------------------------------------

def test_update_job_status_processing():
    mock_conn = MagicMock()
    mock_cur = MagicMock()
    mock_conn.cursor.return_value.__enter__ = lambda s: mock_cur
    mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

    with patch("converter.get_db_connection", return_value=mock_conn):
        converter.update_job_status("some-uuid", converter.STATUS_PROCESSING)

    sql = mock_cur.execute.call_args[0][0]
    params = mock_cur.execute.call_args[0][1]
    assert "storage_object_key" not in sql
    assert params == (converter.STATUS_PROCESSING, "some-uuid")
    mock_conn.commit.assert_called_once()


def test_update_job_status_done_with_key():
    mock_conn = MagicMock()
    mock_cur = MagicMock()
    mock_conn.cursor.return_value.__enter__ = lambda s: mock_cur
    mock_conn.cursor.return_value.__exit__ = MagicMock(return_value=False)

    with patch("converter.get_db_connection", return_value=mock_conn):
        converter.update_job_status("some-uuid", converter.STATUS_DONE, storage_object_key="conversions/x.mp4")

    sql = mock_cur.execute.call_args[0][0]
    params = mock_cur.execute.call_args[0][1]
    assert "storage_object_key" in sql
    assert params == (converter.STATUS_DONE, "conversions/x.mp4", "some-uuid")
    mock_conn.commit.assert_called_once()


# ---------------------------------------------------------------------------
# process_job
# ---------------------------------------------------------------------------

@patch("converter.shutil.rmtree")
@patch("converter.tempfile.mkdtemp", return_value="/tmp/fake_dir")
@patch("converter.upload_to_s3", return_value="conversions/uuid.mp4")
@patch("converter.download_media", return_value="/tmp/fake_dir/vid.mp4")
@patch("converter.update_job_status")
def test_process_job_happy_path(mock_update, mock_download, mock_upload, mock_mkdtemp, mock_rmtree):
    converter.process_job("uuid", "https://youtube.com/watch?v=x", "MP4")

    assert mock_update.call_count == 2
    mock_update.assert_any_call("uuid", converter.STATUS_PROCESSING)
    mock_update.assert_any_call("uuid", converter.STATUS_DONE, storage_object_key="conversions/uuid.mp4")
    mock_rmtree.assert_called_once_with("/tmp/fake_dir", ignore_errors=True)


@patch("converter.shutil.rmtree")
@patch("converter.tempfile.mkdtemp", return_value="/tmp/fake_dir")
@patch("converter.download_media", side_effect=yt_dlp.utils.DownloadError("fail"))
@patch("converter.update_job_status")
def test_process_job_marks_failed_on_download_error(mock_update, mock_download, mock_mkdtemp, mock_rmtree):
    converter.process_job("uuid", "https://youtube.com/watch?v=x", "MP3")

    mock_update.assert_any_call("uuid", converter.STATUS_FAILED)
    mock_rmtree.assert_called_once_with("/tmp/fake_dir", ignore_errors=True)


# ---------------------------------------------------------------------------
# _on_message
# ---------------------------------------------------------------------------

def _make_method():
    method = MagicMock()
    method.delivery_tag = 1
    return method


def test_on_message_nacks_malformed_json():
    channel = MagicMock()
    with patch("converter.process_job") as mock_process:
        converter._on_message(channel, _make_method(), MagicMock(), b"not-json")

    channel.basic_nack.assert_called_once_with(delivery_tag=1, requeue=False)
    mock_process.assert_not_called()


def test_on_message_nacks_invalid_format():
    channel = MagicMock()
    body = json.dumps({"jobId": "uuid", "youtubeUrl": "https://y.com", "outputFormat": "WEBM"}).encode()
    with patch("converter.process_job") as mock_process:
        converter._on_message(channel, _make_method(), MagicMock(), body)

    channel.basic_nack.assert_called_once_with(delivery_tag=1, requeue=False)
    mock_process.assert_not_called()


def test_on_message_acks_on_success():
    channel = MagicMock()
    body = json.dumps({"jobId": "uuid", "youtubeUrl": "https://y.com", "outputFormat": "MP4"}).encode()
    with patch("converter.process_job"):
        converter._on_message(channel, _make_method(), MagicMock(), body)

    channel.basic_ack.assert_called_once_with(delivery_tag=1)


def test_on_message_nacks_on_process_failure():
    channel = MagicMock()
    body = json.dumps({"jobId": "uuid", "youtubeUrl": "https://y.com", "outputFormat": "MP3"}).encode()
    with patch("converter.process_job", side_effect=Exception("boom")):
        converter._on_message(channel, _make_method(), MagicMock(), body)

    channel.basic_nack.assert_called_once_with(delivery_tag=1, requeue=False)
