The demand model for Fair Ticketing. Checkout never calls it; the Java backend
sends closed events as training rows, stores the predictions, and only then
opens waiting rooms for HIGH-risk shows.

```bash
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8090
pytest
```
