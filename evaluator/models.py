"""HuggingFace model helpers for the AI parsing evaluator."""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, MutableMapping, Optional

try:
    import torch
except ImportError as exc:  # pragma: no cover - surfaced during runtime
    raise RuntimeError("torch is required. Install via requirements.txt.") from exc

try:
    from transformers import AutoModelForCausalLM, AutoTokenizer
except ImportError as exc:  # pragma: no cover - surfaced during runtime
    raise RuntimeError("transformers is required. Install via requirements.txt.") from exc

try:
    from transformers import BitsAndBytesConfig
except ImportError:  # pragma: no cover - optional dependency
    BitsAndBytesConfig = None  # type: ignore[assignment]

SUPPORTED_MODELS: tuple[str, ...] = (
    "google/gemma-3-1b-it",
    "google/gemma-3n-E2B-it",
)


@dataclass(frozen=True)
class ModelSettings:
    model_name: str
    max_new_tokens: int = 256
    temperature: float = 0.0

    @classmethod
    def validate(cls, model_name: str) -> str:
        if model_name not in SUPPORTED_MODELS:
            raise ValueError(
                f"Unsupported model '{model_name}'. Supported: {', '.join(SUPPORTED_MODELS)}"
            )
        return model_name


class ModelInference:
    """Wrapper that loads Gemma chat models with 8-bit quantization."""

    def __init__(
        self,
        model_name: str,
        *,
        device: Optional[str] = None,
        max_new_tokens: int = 256,
        temperature: float = 0.0,
    ) -> None:
        ModelSettings.validate(model_name)
        self.settings = ModelSettings(
            model_name=model_name,
            max_new_tokens=max_new_tokens,
            temperature=temperature,
        )
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.tokenizer = AutoTokenizer.from_pretrained(model_name)
        if self.tokenizer.pad_token is None:
            self.tokenizer.pad_token = self.tokenizer.eos_token
        quantization = None
        if self.device != "cpu":
            if BitsAndBytesConfig is None:
                raise RuntimeError(
                    "bitsandbytes is required for 8-bit quantization; install it via requirements.txt."
                )
            quantization = BitsAndBytesConfig(load_in_8bit=True)
        self.model = AutoModelForCausalLM.from_pretrained(
            model_name,
            device_map="auto" if self.device != "cpu" else None,
            quantization_config=quantization if self.device != "cpu" else None,
            torch_dtype=torch.float16 if self.device != "cpu" else torch.float32,
        )
        if self.device == "cpu":
            self.model = self.model.to(self.device)

    def generate(self, prompt: str, *, system_prompt: Optional[str] = None) -> str:
        """Generate a deterministic response for the supplied prompt."""
        chat = self._build_messages(prompt, system_prompt)
        template = self.tokenizer.apply_chat_template(
            chat,
            tokenize=False,
            add_generation_prompt=True,
        )
        inputs = self.tokenizer(
            template,
            return_tensors="pt",
            add_special_tokens=True,
        )
        inputs = {k: v.to(self.device) for k, v in inputs.items()}
        self.model.eval()
        with torch.inference_mode():
            generation = self.model.generate(
                **inputs,
                max_new_tokens=self.settings.max_new_tokens,
                temperature=self.settings.temperature,
                do_sample=False,
                pad_token_id=self.tokenizer.eos_token_id,
            )
        output_tokens = generation[0][inputs["input_ids"].shape[-1] :]
        response = self.tokenizer.decode(
            output_tokens,
            skip_special_tokens=True,
        )
        return response.strip()

    def _build_messages(
        self,
        user_prompt: str,
        system_prompt: Optional[str],
    ) -> List[MutableMapping[str, str]]:
        messages: List[MutableMapping[str, str]] = []
        if system_prompt:
            messages.append({"role": "system", "content": system_prompt})
        messages.append({"role": "user", "content": user_prompt})
        return messages
