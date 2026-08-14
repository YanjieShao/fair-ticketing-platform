from fastapi import FastAPI, HTTPException

from app.model import ForecastRequest, ForecastResponse, InsufficientHistory, forecast

app = FastAPI(title="Fair Ticketing demand model", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/forecast", response_model=ForecastResponse)
def run_forecast(request: ForecastRequest) -> ForecastResponse:
    try:
        return forecast(request)
    except InsufficientHistory as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
