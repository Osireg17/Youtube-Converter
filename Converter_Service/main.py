import logging
import os
import threading
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from dotenv import load_dotenv

import converter

load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Converter Service")
    thread = threading.Thread(target=converter.start_rabbitmq_consumer, daemon=True)
    thread.start()
    logger.info("RabbitMQ consumer thread started (thread_id=%s)", thread.ident)
    yield
    logger.info("Converter Service shutting down")


app = FastAPI(title="Converter Service", lifespan=lifespan)


@app.get("/health")
def health():
    logger.debug("Health check called")
    return {"status": "ok"}


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
