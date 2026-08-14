from app.model import ForecastRequest, PredictRow, TrainRow, forecast, risk_level


def _train(popularity: int, capacity: int, sold: int) -> TrainRow:
    return TrainRow(
        popularityScore=popularity,
        venueCapacity=capacity,
        avgPriceCents=8000,
        weekend=True,
        leadTimeDays=60,
        genre="Pop",
        city="Dublin",
        category="Concert",
        soldQuantity=sold,
    )


def _predict(event_id: int, popularity: int, capacity: int) -> PredictRow:
    return PredictRow(
        eventId=event_id,
        popularityScore=popularity,
        venueCapacity=capacity,
        avgPriceCents=8000,
        weekend=True,
        leadTimeDays=60,
        genre="Pop",
        city="Dublin",
        category="Concert",
    )


def test_oversubscribed_events_are_high_risk():
    assert risk_level(1.25) == "HIGH"
    assert risk_level(0.8) == "MEDIUM"
    assert risk_level(0.2) == "LOW"


def test_model_ranks_headliners_above_unknown_acts():
    train = []
    for i in range(30):
        popularity = 20 + (i % 8) * 10
        capacity = 8_000 + (i % 5) * 2_000
        sold = min(capacity, int(capacity * (0.25 + popularity / 120)))
        train.append(_train(popularity, capacity, sold))

    result = forecast(
        ForecastRequest(
            train=train,
            predict=[
                _predict(1, 95, 20_000),
                _predict(2, 15, 20_000),
            ],
        )
    )

    by_id = {item.eventId: item for item in result.predictions}
    assert by_id[1].expectedDemand > by_id[2].expectedDemand
    assert by_id[1].riskLevel in {"MEDIUM", "HIGH"}
