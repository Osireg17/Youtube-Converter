import os
import uvicorn
from fastapi import FastAPI
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="Converter Service")


@app.get("/health")
def health():
    return {"status": "ok"}


def start_rabbitmq_consumer():
    # TODO: Implement RabbitMQ consumer loop (BE-07)
    # Connect to RABBITMQ_URL, declare the jobs queue, and begin consuming
    # messages. For each message, trigger the conversion pipeline.
    pass


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
