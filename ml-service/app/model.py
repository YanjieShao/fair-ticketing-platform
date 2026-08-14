"""Demand forecast for upcoming concerts.

The Java backend sends closed events as training rows and upcoming events as
the prediction set. Checkout never calls this service; a scheduled job writes
the numbers to MySQL and the API only reads that table.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import xgboost as xgb
from pydantic import BaseModel, Field

MODEL_VERSION = "xgb-0.1"
MIN_TRAIN_ROWS = 20


class TrainRow(BaseModel):
    popularityScore: int
    venueCapacity: int
    avgPriceCents: int
    weekend: bool
    leadTimeDays: int
    genre: str
    city: str
    category: str
    soldQuantity: int


class PredictRow(BaseModel):
    eventId: int
    popularityScore: int
    venueCapacity: int
    avgPriceCents: int
    weekend: bool
    leadTimeDays: int
    genre: str
    city: str
    category: str


class ForecastRequest(BaseModel):
    train: list[TrainRow]
    predict: list[PredictRow]


class Prediction(BaseModel):
    eventId: int
    expectedDemand: int
    capacity: int
    demandRatio: float
    riskLevel: str
    modelVersion: str


class ForecastResponse(BaseModel):
    modelVersion: str
    predictions: list[Prediction]


class InsufficientHistory(Exception):
    pass


def risk_level(ratio: float) -> str:
    if ratio >= 1.0:
        return "HIGH"
    if ratio >= 0.7:
        return "MEDIUM"
    return "LOW"


def _frame(rows: list[TrainRow] | list[PredictRow]) -> pd.DataFrame:
    records = [row.model_dump() for row in rows]
    df = pd.DataFrame.from_records(records)
    df["weekend"] = df["weekend"].astype(int)
    cats = pd.get_dummies(df[["genre", "city", "category"]], prefix=["genre", "city", "category"])
    nums = df[["popularityScore", "venueCapacity", "avgPriceCents", "weekend", "leadTimeDays"]].astype(float)
    return pd.concat([nums, cats], axis=1)


def forecast(request: ForecastRequest) -> ForecastResponse:
    if len(request.train) < MIN_TRAIN_ROWS:
        raise InsufficientHistory(
            f"Need at least {MIN_TRAIN_ROWS} closed events to train, got {len(request.train)}"
        )
    if not request.predict:
        return ForecastResponse(modelVersion=MODEL_VERSION, predictions=[])

    x_train = _frame(request.train)
    y_train = np.array([row.soldQuantity for row in request.train], dtype=float)
    x_pred = _frame(request.predict)
    x_pred = x_pred.reindex(columns=x_train.columns, fill_value=0)

    model = xgb.XGBRegressor(
        n_estimators=80,
        max_depth=4,
        learning_rate=0.1,
        subsample=0.9,
        objective="reg:squarederror",
        n_jobs=1,
        random_state=20260814,
    )
    model.fit(x_train, y_train)
    raw = np.maximum(model.predict(x_pred), 0.0)

    predictions: list[Prediction] = []
    for row, demand in zip(request.predict, raw, strict=True):
        expected = int(round(float(demand)))
        capacity = max(row.venueCapacity, 1)
        ratio = expected / capacity
        predictions.append(
            Prediction(
                eventId=row.eventId,
                expectedDemand=expected,
                capacity=capacity,
                demandRatio=round(ratio, 3),
                riskLevel=risk_level(ratio),
                modelVersion=MODEL_VERSION,
            )
        )
    return ForecastResponse(modelVersion=MODEL_VERSION, predictions=predictions)
