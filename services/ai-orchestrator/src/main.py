"""
TikTok Shop AI Video — AI Orchestrator

FastAPI + Temporal Worker 服务。
V1: 两个 Temporal Workflow — 商品分析 + 选方案后生成。
"""

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from src.api.health import router as health_router
from src.api.workflows import router as workflows_router
from src.config import settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup/shutdown: init Temporal client."""
    # TODO: Phase 4 — start Temporal worker
    yield


app = FastAPI(
    title="TK AI Video Orchestrator",
    version="1.0.0",
    description="Python AI 编排服务 — Product Analysis + Video Generation Workflows",
    lifespan=lifespan,
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Internal only; gated by network policy
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routers
app.include_router(health_router)
app.include_router(workflows_router, prefix="/ai")


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={
            "error": "internal_error",
            "message": str(exc),
        },
    )
