import os
import threading
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from dotenv import load_dotenv

import converter

load_dotenv()


@asynccontextmanager
async def lifespan(app: FastAPI):
    thread = threading.Thread(target=converter.start_rabbitmq_consumer, daemon=True)
    thread.start()
    yield


app = FastAPI(title="Converter Service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port)
