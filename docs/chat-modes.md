# Chat Modes

## Troubleshoot mode (default)

For detailed diagnostic help at the bench.

The LLM is instructed to:
- Give step-by-step diagnostic answers
- Reference specific source files by name
- Include CLI commands and config values where relevant
- Ask for more info (FC model, firmware version, symptoms) when needed

Best for: bench debugging, following a repair process, learning what's wrong.

---

## Engineer Call mode

For quick verbal answers while on a phone call with engineers.

The LLM is instructed to:
- Cap answers at 6 lines maximum
- Lead immediately with the direct answer — no preamble
- Use plain numbered or dash lists (easy to read aloud)
- Give exact numbers and specs when asked

Best for: confirming specs mid-call, quick "what's the value for X" lookups, relaying answers verbally.

Every response in this mode also gets a **Copy** button so you can paste the answer into Slack or a doc mid-call.

---

## Switching modes

**Windows:** Toggle in the header bar (Troubleshoot / Eng Call)

**Android:** Toggle in the header bar (DIAGNOSE / ENG CALL)

Switching mode clears the current conversation history.

---

## Image analysis

Both modes support image attachment. Photo your board, wiring, or crash damage and ask a question.

Examples:
- "What's wrong with this solder joint?"
- "Which UART pads are TX/RX on this FC?"
- "Is this ESC burnt out?"
- "Can you identify this flight controller?"

Image support by provider:
| Provider | Images supported |
|----------|----------------|
| Gemini 2.0 Flash | Yes |
| Gemini 2.5 Flash/Pro | Yes |
| Claude Haiku | Yes |
| Claude Sonnet | Yes |
| Ollama llama3.2 | No |
| Ollama llava | Yes |
| Ollama phi3.5 | No |

For offline image analysis, pull the `llava` model: `ollama pull llava`
