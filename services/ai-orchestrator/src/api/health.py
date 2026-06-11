"""Health check endpoint."""

from fastapi import APIRouter

router = APIRouter()


@router.get("/ai/health")
async def health_check():
    return {
        "status": "ok",
        "service": "ai-orchestrator",
        "version": "1.0.0",
        "schema_version": "1.0.0",
    }
