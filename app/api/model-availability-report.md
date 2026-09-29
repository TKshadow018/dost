# Dost Model Availability Report

**Test date:** September 27, 2026  
**API tested:** `https://bisque-bear-900175.hostingersite.com/api/ai.php`  
**Scope:** All 15 free-model entries in the app catalog were sent a live completion request. Responses were checked for HTTP/provider errors and, on successful checks, the JSON structure expected by the app. The requests used the non-archiving completion operation and did not add test messages to conversation archives.

These results describe availability during the test, not a guarantee of future availability. Free-model providers can become overloaded, rate-limited, or unavailable without notice.

## Working

These returned HTTP 200 and valid app-shaped JSON during verification:

| Model | Result |
| --- | --- |
| `openrouter/free` | Worked. This is a dynamic router, not a fixed model; it selected different Nvidia models during separate calls. |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | Worked with valid app-shaped JSON. |
| `inclusionai/ling-3.0-flash-sante:free` | Worked with valid app-shaped JSON. |

## May Work Later

These model IDs are listed as free by OpenRouter, but current calls returned provider errors. They may work when provider capacity or availability changes.

| Model | Result |
| --- | --- |
| `google/gemma-4-31b-it:free` | HTTP 429 on both attempts; provider returned an error. |
| `google/gemma-4-26b-a4b-it:free` | HTTP 429 on both attempts; provider returned an error. |
| `qwen/qwen3.8-27b:free` | HTTP 429 on both attempts; provider returned an error. |
| `poolside/laguna-s-2.1:free` | Intermittent: HTTP 200, then HTTP 429; on September 28 it returned HTTP 200 with valid app-shaped JSON and the reply `Enjoy your meal!`. |

September 28 retest: both Gemma models and Qwen 3.8 again returned HTTP 429 with a provider error. Poolside produced a nonempty reply in the app's expected JSON structure.

## Won't Work as Listed

These free variants returned HTTP 404 and were absent from OpenRouter's current public model catalog at test time. Do not offer these IDs as working free models unless their availability is confirmed again. For `z-ai/glm-5.2:free`, the API error specifically suggested the paid ID `z-ai/glm-5.2`.

| Model | Result |
| --- | --- |
| `z-ai/glm-5.2:free` | HTTP 404; free version unavailable. |
| `deepseek/deepseek-r1:free` | HTTP 404; not listed in the current catalog. |
| `deepseek/deepseek-chat-v3-0324:free` | HTTP 404; not listed in the current catalog. |
| `meta-llama/llama-3.3-70b-instruct:free` | HTTP 404; not listed in the current catalog. |
| `mistralai/mistral-small-3.1-24b-instruct:free` | HTTP 404; not listed in the current catalog. |
| `google/gemini-2.0-flash-exp:free` | HTTP 404; not listed in the current catalog. |
| `qwen/qwen3-235b-a22b:free` | HTTP 404; not listed in the current catalog. |
| `microsoft/mai-ds-r1:free` | HTTP 404; not listed in the current catalog. |

## Paid Models Not Tested

The paid entries in the app catalog were not tested against their model providers because no personal OpenRouter API key was configured for this test. A probe using `openai/gpt-4o-mini` returned HTTP 400, `Requested model is not allowed`; the Dost API rejected it before forwarding the request to OpenRouter. This is an API-key/access limitation, not evidence that the model itself is unavailable.

The paid catalog entries are:

- `openai/gpt-4o`, `openai/gpt-4o-mini`, `openai/o3-mini`
- `anthropic/claude-sonnet-4`, `anthropic/claude-3.7-sonnet`
- `google/gemini-2.5-pro`, `google/gemini-2.5-flash`, `google/gemini-2.0-flash-001`
- `deepseek/deepseek-r1`, `deepseek/deepseek-chat-v3-0324`
- `meta-llama/llama-4-maverick`, `meta-llama/llama-4-scout`
- `mistralai/mistral-medium-3`, `mistralai/mistral-small-3.1-24b-instruct`
- `x-ai/grok-3-mini`, `perplexity/sonar`, `cohere/command-a`

## Summary

- 3 free entries returned valid app-shaped JSON during verification.
- 4 free entries returned provider errors or intermittent results and may work later.
- 8 free IDs returned HTTP 404 and were absent from the current model catalog.
- Paid entries remain unverified until tested with a valid personal OpenRouter key.

The free-model list is maintained in `app/src/main/java/com/snigtus/bondhu/OpenRouterClient.kt` and the server-side free-model allowlist is maintained in `app/api/ai.php`. Recheck both against OpenRouter before relying on a model ID or changing the catalog.