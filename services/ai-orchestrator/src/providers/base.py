"""Abstract base classes for image and video generation providers."""

from abc import ABC, abstractmethod


class ImageGenerationProvider(ABC):
    """Abstract interface for image generation services."""

    @abstractmethod
    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a single image. Returns dict with url, provider, model."""
        ...

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Human-readable provider identifier (e.g. 'openai', 'fake')."""
        ...


class VideoGenerationProvider(ABC):
    """Abstract interface for video generation services."""

    @abstractmethod
    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a video clip. Returns dict with url, provider, model."""
        ...

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Human-readable provider identifier (e.g. 'volcengine', 'fake')."""
        ...
