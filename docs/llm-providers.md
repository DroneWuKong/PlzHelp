# LLM Providers

## Provider comparison

| Provider | Cost | Offline | Speed | Quality | Notes |
|----------|------|---------|-------|---------|-------|
| Gemini 2.0 Flash | Free (1,500/day) | No | Fast | Good | Best free option. Same API Wingman uses |
| Gemini 2.5 Flash | Free tier | No | Fast | Better | Smarter than 2.0 Flash |
| Gemini 2.5 Pro | Paid | No | Medium | Best Gemini | |
| Claude Haiku | ~$0.001/msg | No | Very fast | Good | Cheapest Claude. Great for Eng Call mode |
| Claude Sonnet | ~$0.01/msg | No | Fast | Excellent | Best overall quality |
| Ollama llama3.2 | Free | Yes | Slow (CPU) | OK | Good general questions |
| Ollama phi3.5 | Free | Yes | Faster | OK | Smaller, faster than llama3.2 |
| Ollama gemma2:2b | Free | Yes | Fastest | Lower | Best offline speed |
| Ollama mistral | Free | Yes | Medium | Better | Better reasoning than llama3.2 |
| Ollama deepseek-r1:8b | Free | Yes | Medium | Good | Best local for technical/firmware |
| Local server | Free | Same WiFi | Depends | Depends | Relay to your laptop's server.py |

---

## Gemini

### Getting a key
1. Go to [aistudio.google.com](https://aistudio.google.com)
2. Sign in with a Google account
3. Create API key → Create API key in new project
4. Copy the `AIzaSy...` key

This is the same key used by Wingman AI on Forge.

### Free tier limits
- 1,500 requests per day
- 1 million tokens per minute
- More than sufficient for daily use

### Models
| Model | Notes |
|-------|-------|
| `gemini-2.0-flash` | Default. Fast, good for most questions |
| `gemini-2.5-flash-preview-05-20` | Smarter, still fast |
| `gemini-2.5-pro-preview-05-06` | Best quality, may cost above free tier |

### API endpoint
```
https://generativelanguage.googleapis.com/v1beta/models/MODEL:generateContent?key=KEY
```

Note: Gemini uses a different request format than OpenAI — system instructions go in a separate `system_instruction` field, and assistant role is called `model` not `assistant`.

---

## Anthropic Claude

### Getting a key
1. Go to [console.anthropic.com](https://console.anthropic.com)
2. Create an account and add a payment method
3. API Keys → Create Key
4. Copy the `sk-ant-...` key

### Models
| Model | Speed | Cost/1M tokens | Notes |
|-------|-------|----------------|-------|
| `claude-haiku-4-5-20251001` | Very fast | $0.80 in / $4 out | Best for Eng Call mode |
| `claude-sonnet-4-20250514` | Fast | $3 in / $15 out | Best overall quality |

At typical usage (~500 tokens per query, 50 queries/day), Claude Haiku costs ~$0.02/day.

### Image support
Claude natively handles images. Attach a photo of your board and ask:
- "What's wrong with this wiring?"
- "Which pads are TX and RX on this FC?"
- "Is this solder joint good?"

Images are sent as base64 in the message content array.

---

## Ollama (offline)

Ollama runs LLMs locally on your CPU or GPU. No internet required for inference after model download.

### Install
```cmd
# Windows — SETUP.bat does this automatically, or manually:
winget install Ollama.Ollama
# Or download from https://ollama.com/download
```

Ollama runs as a system tray app on Windows — it starts automatically at login.

### Pull models
```cmd
ollama pull llama3.2          # 2GB — balanced
ollama pull phi3.5             # 2.2GB — faster
ollama pull gemma2:2b          # 1.6GB — fastest
ollama pull mistral            # 4GB — better reasoning
ollama pull deepseek-r1:8b     # 5GB — best for technical questions
ollama pull llava              # 4.5GB — adds image support
```

### Model recommendations by use case

| Use case | Recommended model |
|----------|-----------------|
| Quick spec lookups | gemma2:2b (fastest) |
| General troubleshooting | llama3.2 |
| Firmware / PX4 / ArduPilot | deepseek-r1:8b |
| On a phone call needing fast answers | phi3.5 |
| Best quality offline | mistral or deepseek-r1:8b |
| Image analysis | llava (requires vision support) |

### Speed notes
Without a GPU, responses take 10–30 seconds per query on a typical laptop. With an NVIDIA GPU, Ollama uses CUDA and is 5–10× faster.

### API
Ollama exposes an OpenAI-compatible API at `http://localhost:11434/v1/chat/completions`. PlzHelp uses this endpoint for all Ollama calls.

---

## LM Studio

LM Studio is a GUI app for running local models. Easier to use than Ollama for non-technical users.

1. Download from [lmstudio.ai](https://lmstudio.ai)
2. Search and download a model in the app
3. Go to Local Server tab and start the server (default port 1234)
4. In PlzHelp: provider = `lmstudio`, model = whatever model name LM Studio shows

```cmd
python server.py --provider lmstudio --model mistral-7b-instruct
```

---

## Local server relay (Android)

The Android app can relay queries to your laptop's `server.py` when on the same WiFi network.

In Android Settings:
- Provider: Local Server
- Local Server URL: `http://192.168.x.x:5000` (your laptop's local IP)

Find your laptop's IP:
```cmd
ipconfig
# Look for "IPv4 Address" under your WiFi adapter
```

This is useful for:
- Using Ollama from your phone without burning API credits
- Accessing your full local docs folder from the phone
- Air-gapped environments where you can't reach Gemini/Claude

---

## Switching providers

### Windows
The ⚙ settings drawer in the browser UI lets you switch provider and model without restarting the server. Changes take effect immediately on the next query. Conversation history is cleared when switching.

Or restart with different flags:
```cmd
python server.py --provider gemini --key AIzaSy-...
python server.py --provider anthropic --key sk-ant-...
python server.py --offline  # Ollama
```

### Android
Settings screen → select provider and model → Save Settings. Takes effect immediately.
