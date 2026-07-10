"""Abstract base class for image generation providers."""

from abc import ABC, abstractmethod


class ImageGenerationProvider(ABC):
    """Abstract interface for image generation services.

    All image generation providers must implement this interface.
    The factory function `get_image_provider()` selects the active provider
    based on the ENABLE_IMAGE_GENERATION feature flag.
    """

    @abstractmethod
    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a single image.

        Args:
            prompt: The image generation prompt.
            negative_prompt: What to avoid in the generated image.
            **kwargs: Provider-specific extra parameters.

        Returns:
            dict with keys: url (str), provider (str), model (str).
        """
        ...

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Human-readable provider identifier (e.g. 'openai', 'fake')."""
        ...
