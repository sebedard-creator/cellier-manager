from __future__ import annotations

from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field, HttpUrl, field_validator


Source = Literal["VIVINO", "UNTAPPD", "SAQ"]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ProductIdentity(StrictModel):
    type: Literal["VIN", "BIERE"]
    producer: str = Field(min_length=1, max_length=200)
    name: str = Field(min_length=1, max_length=200)
    vintage: str | None = Field(default=None, max_length=20)


class CreateRequest(StrictModel):
    protocolVersion: Literal[1]
    requestId: str
    datasetId: str
    itemUuid: str
    source: Source
    identityRevision: int = Field(ge=0)
    identity: ProductIdentity
    createdAt: str


class OpenRequest(StrictModel):
    requestId: str


class PageCapture(StrictModel):
    url: str = Field(max_length=2048)
    canonicalUrl: str | None = Field(default=None, max_length=2048)
    title: str = Field(max_length=500)
    capturedAt: str
    language: str | None = Field(default=None, max_length=20)


class CapturedContent(StrictModel):
    jsonLdBlocks: list[Any] = Field(default_factory=list, max_length=20)
    productHtml: str = Field(max_length=1_048_576)
    visibleText: str = Field(max_length=200_000)
    userSelectedText: str | None = Field(default=None, max_length=200_000)
    captureStrategy: str = Field(max_length=50)
    truncated: bool = False


class CaptureEnvelope(StrictModel):
    protocolVersion: Literal[1]
    captureId: str
    requestId: str
    leaseToken: str
    source: Source
    page: PageCapture
    content: CapturedContent
    extensionVersion: str = Field(max_length=50)


class PairRequest(StrictModel):
    pairingId: str
    pairingSecret: str
    deviceId: str
    name: str = Field(min_length=1, max_length=100)
    extensionId: str | None = Field(default=None, max_length=100)


class AckRequest(StrictModel):
    resolution: Literal["RECEIVED", "APPLIED", "REJECTED"]


class ClaimRequest(StrictModel):
    extensionVersion: str = Field(max_length=50)


class JobProgress(StrictModel):
    state: Literal["SEARCHING", "NAVIGATING", "CAPTURING", "NEEDS_USER", "FAILED"]


class ErrorBody(StrictModel):
    code: str
    message: str
    retryable: bool = False
    requestId: str | None = None


class ApiError(StrictModel):
    error: ErrorBody
