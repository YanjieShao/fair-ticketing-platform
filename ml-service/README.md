# Demand model

XGBoost service that scores upcoming shows. Checkout never calls it. The
Java backend sends closed events as training rows, stores the predictions
in `demand_forecasts`, and only then opens waiting rooms for HIGH-risk
shows.

Needs Python 3.12+ (the macOS system Python is 3.9).

```bash
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8090
pytest
```

After seeding the backend ([docs/seed-data.md](../docs/seed-data.md)):

```bash
export FT_FORECAST_ON_START=true
# or POST /api/admin/forecasts/run as admin@fairticketing.local / password123
# Insights follow on that same startup pass, or POST /api/admin/insights/run
```

`GET /health` and `POST /forecast` are the only routes. If this process is
down, ticket sales are unaffected; the event page simply has no new
forecast.
