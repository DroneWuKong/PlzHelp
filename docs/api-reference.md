# API Reference (Windows Server)

The Windows server exposes a REST API at `http://localhost:5000`. The browser UI uses this API, but you can also call it directly from scripts or other tools.

---

## GET /status

Returns current index state and configuration.

**Response:**
```json
{
  "ready": true,
  "provider": "gemini",
  "model": "gemini-2.0-flash",
  "total_files": 47,
  "total_chunks": 1823,
  "folders": [
    {
      "label": "docs",
      "path": "C:\\orqa-chat\\docs",
      "files": 47,
      "chunks": 1823
    }
  ]
}
```

`ready` is `false` while the initial index is being built. Poll until `true` before sending chat requests.

---

## POST /chat

Send a message and get a response from the LLM with retrieved doc context.

**Request body:**
```json
{
  "messages": [
    {"role": "user", "content": "What are the IMU rotation values for QuadCore H7?"}
  ],
  "mode": "troubleshoot",
  "image": {
    "data": "base64encodedimagedata...",
    "media_type": "image/jpeg"
  }
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `messages` | array | Yes | Full conversation history. Each item has `role` (user/assistant) and `content` (string) |
| `mode` | string | No | `troubleshoot` (default) or `engcall` |
| `image` | object | No | Attach an image to the last user message |
| `image.data` | string | No | Base64-encoded image bytes |
| `image.media_type` | string | No | MIME type, e.g. `image/jpeg`, `image/png` |

**Response:**
```json
{
  "reply": "SPI1 IMU uses ROTATION_PITCH_180. SPI4 IMU uses ROTATION_ROLL_180_YAW_90...",
  "sources": [
    "[docs] orqa-px4/SESSION_CONTEXT.md",
    "[docs] wingman/orqa_kb.md"
  ],
  "category": "orqa"
}
```

| Field | Notes |
|-------|-------|
| `reply` | The LLM's answer |
| `sources` | Up to 8 source labels from the top matching chunks |
| `category` | Detected query category, or empty string |

**Error response (HTTP 4xx/5xx):**
```json
{
  "error": "No Anthropic API key set."
}
```

---

## POST /config

Switch LLM provider and model without restarting the server.

**Request body:**
```json
{
  "provider": "anthropic",
  "model": "claude-haiku-4-5-20251001",
  "api_key": "sk-ant-..."
}
```

| Field | Notes |
|-------|-------|
| `provider` | `anthropic`, `gemini`, `ollama`, `lmstudio`, `openai-compat` |
| `model` | Model name. Uses provider default if omitted |
| `api_key` | API key. Keeps existing key if omitted |

**Response:**
```json
{
  "ok": true,
  "provider": "anthropic",
  "model": "claude-haiku-4-5-20251001"
}
```

---

## GET /config

Returns current provider and model configuration.

**Response:**
```json
{
  "provider": "gemini",
  "model": "gemini-2.0-flash",
  "has_key": true
}
```

---

## Example: query from a script

```python
import requests

# Check server is ready
status = requests.get("http://localhost:5000/status").json()
assert status["ready"], "Server not ready yet"

# Send a query
response = requests.post("http://localhost:5000/chat", json={
    "messages": [
        {"role": "user", "content": "What UART is used for GPS on the QuadCore H7?"}
    ],
    "mode": "engcall"
}).json()

print(response["reply"])
print("Sources:", response["sources"])
print("Category:", response["category"])
```

---

## Example: send an image

```python
import base64, requests

with open("board_photo.jpg", "rb") as f:
    image_b64 = base64.b64encode(f.read()).decode()

response = requests.post("http://localhost:5000/chat", json={
    "messages": [
        {"role": "user", "content": "Can you identify this flight controller and tell me what the UART pads are?"}
    ],
    "mode": "troubleshoot",
    "image": {
        "data": image_b64,
        "media_type": "image/jpeg"
    }
}).json()

print(response["reply"])
```
