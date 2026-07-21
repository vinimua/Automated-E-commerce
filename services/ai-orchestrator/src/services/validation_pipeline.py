"""JSON extraction + validation + repair + retry pipeline.LLM 输出的 JSON 经常有小毛病，它负责修好再校验"""

import json
import logging
import re
from typing import Any, Type
from pydantic import BaseModel, ValidationError

log = logging.getLogger(__name__)
MAX_REPAIR_RETRIES = 3


def extract_json(raw_text: str) -> str:
    """Strip markdown fences, find the first JSON object or array."""
    text = raw_text.strip()
    # Remove markdown code fences
    if "```" in text:
        # Extract content between code fences if present
        fence_match = re.search(r"```(?:\w+)?\s*\n(.*?)\n\s*```", text, re.DOTALL)
        if fence_match:
            text = fence_match.group(1).strip()
        else:
            lines = text.split("\n")
            lines = [l for l in lines if not l.lstrip().startswith("```")]
            text = "\n".join(lines).strip()
    # Find first { or [
    match = re.search(r"[\{\[]", text)
    if match:
        text = text[match.start():]
    # Trim trailing content after the last } or ]
    if text and text[0] == "{":
        last_brace = text.rfind("}")
        if last_brace != -1:
            text = text[:last_brace + 1]
    elif text and text[0] == "[":
        last_bracket = text.rfind("]")
        if last_bracket != -1:
            text = text[:last_bracket + 1]
    return text


def try_parse_json(text: str) -> dict:
    """Try to parse JSON, return dict or raise."""
    return json.loads(text)


def validate_pydantic(data: dict, model_class: Type[BaseModel]) -> BaseModel:
    """Validate data against a Pydantic model. Raises ValidationError on failure."""
    return model_class.model_validate(data)


def repair_json(raw_text: str) -> str:
    """Attempt to repair common JSON issues."""
    text = raw_text.strip()
    # Remove trailing commas
    text = re.sub(r",\s*}", "}", text)
    text = re.sub(r",\s*\]", "]", text)
    # Fix single quotes
    # (keep it simple — LLMs usually output valid JSON with format:json)
    return text


def validate_and_repair(
    raw_text: str | dict[str, Any] | list[Any],
    model_class: Type[BaseModel],
    max_retries: int = MAX_REPAIR_RETRIES,
) -> BaseModel:
    """Full validation pipeline: extract → parse → validate → repair → retry."""
    if isinstance(raw_text, (dict, list)):
        return validate_pydantic(raw_text, model_class)

    text = extract_json(raw_text)
    last_error = None

    for attempt in range(1, max_retries + 1):
        try:
            data = try_parse_json(text)
            return validate_pydantic(data, model_class)
        except (json.JSONDecodeError, ValidationError) as e:
            last_error = e
            log.warning("Validation attempt %d/%d failed: %s", attempt, max_retries, e)
            if attempt < max_retries:
                text = repair_json(text)

    raise ValueError(f"Validation failed after {max_retries} attempts: {last_error}")
