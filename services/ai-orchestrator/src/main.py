"""
TikTok Shop AI Video — AI Orchestrator

FastAPI + Temporal Worker 服务。
V1: 两个 Temporal Workflow — ProductAnalysis + SelectedPlanGeneration。
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from temporalio.client import Client
from temporalio.worker import Worker

from src.api.health import router as health_router
from src.api.workflows import router as workflows_router
from src.config import settings
from src.activities import ALL_ACTIVITIES
from src.workflows import ALL_WORKFLOWS

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup: init Temporal client + worker. Shutdown: graceful stop."""
    temporal_client: Client | None = None
    worker: Worker | None = None

    try:
        temporal_client = await Client.connect(
            settings.temporal_host,
            namespace=settings.temporal_namespace,
        )
        log.info("Temporal client connected: %s (namespace=%s)",
                 settings.temporal_host, settings.temporal_namespace)

        worker = Worker(
            temporal_client,
            task_queue=settings.temporal_task_queue,
            workflows=ALL_WORKFLOWS,
            activities=ALL_ACTIVITIES,
        )
        # Start worker as a background task (non-blocking)
        import asyncio
        worker_task = asyncio.create_task(worker.run())
        log.info("Temporal worker started: task_queue=%s", settings.temporal_task_queue)

        # Store client in app state for endpoints
        app.state.temporal_client = temporal_client
        app.state._worker = worker
        app.state._worker_task = worker_task

    except Exception as e:
        log.warning("Temporal unavailable, running in degraded mode: %s", e)
        app.state.temporal_client = None

    yield

    # Shutdown
    if worker:
        try:
            await worker.shutdown()
        except Exception:
            pass
    app.state.temporal_client = None
    log.info("Temporal worker shut down")


app = FastAPI(
    title="TK AI Video Orchestrator",
    version="1.0.0",
    description="Python AI 编排服务 — Product Analysis + Video Generation Workflows",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(health_router)
app.include_router(workflows_router, prefix="/ai")


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    log.error("Unhandled error: %s", exc, exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"error": "internal_error", "message": str(exc)},
    )
