# Java Chatbot

A simple rule-based, text-based chatbot built with **plain Java only** — no
Spring, no Flask, no external frameworks or libraries. Uses the standard
JDK's built-in `com.sun.net.httpserver.HttpServer` to serve a small JSON API
and a static chat UI.

## How it works

```
 Browser (public/)                 ChatbotServer               ChatEngine / AiClient
 ┌────────────────┐   POST /api/chat   ┌───────────────┐   ┌───────────────────────┐
 │ index.html      │ ─────────────────▶│ ChatHandler    │──▶│ ChatEngine.getResponse│
 │ script.js       │                    │               │   │ (keyword match, CSV)  │
 │ style.css       │◀───────────────── │               │◀──┘
 └────────────────┘   {reply,intent,   │  low confidence   ┌───────────────────────┐
                        confidence}     │  or "fallback"? ─▶│ AiClient.ask()        │
                                        │                    │ (Gemini API, w/ short │
                                        └───────┬────────────┤  session history)     │
                                                 │            └───────────────────────┘
                                                 ▼
                                       ChatLogger → data/chat_log.csv
```

- `ChatEngine.java` loads predefined intents (keyword patterns → responses)
  from `data/intents.csv` and matches user messages against them using
  keyword scoring, weighted so longer/more specific patterns count for more
  than a single short word — the "predefined logic" approach. Supports dynamic
  tokens such as `{time}` for real-time live clock answers.
- `AiClient.java` calls the Google Gemini API using `gemini-2.5-flash` (using the JDK's built-in
  `java.net.http.HttpClient` — no extra libraries) so the bot can answer
  anything the local keyword matching doesn't recognize, or only matched
  weakly. This only runs when an API key is configured (see below); without
  a key, unmatched messages just get the plain fallback reply as before.
- `ChatbotServer.java` starts a high-concurrency HTTP server using Java Virtual Threads
  on port `5000` (or `$PORT`, if set), serves the static frontend from `public/`, and exposes:
  - `POST /api/chat`: accepts `{"message": "...", "sessionId": "..."}` and returns `{"reply", "intent", "confidence"}`.
  - `POST /api/reset`: clears the server-side rolling memory for the specified session.
  - `GET /api/health`: returns health status and whether AI fallback is enabled.
- **Session memory**: the frontend generates a random `sessionId` (stored in
  `localStorage`) and sends it with every request. The server keeps a short
  rolling history (last few turns) per session in memory and includes it
  when calling Gemini, so AI fallback replies stay coherent across a
  conversation instead of treating every message in isolation.
- **Logging**: every turn is appended to `data/chat_log.csv`
  (`timestamp, session_id, message, intent, confidence`) so you can see what
  people actually ask and use it to grow `data/intents.csv` over time.
- `public/index.html`, `style.css`, `script.js` — the chat UI with clickable quick
  suggestion chips, timestamps, clear chat button, markdown/code formatting, and
  a dark/light theme toggle.

## Requirements

- JDK 11 or later (runs standard library only; utilizes Java Virtual Threads on modern JDKs).

## Enabling AI fallback (answers to anything) — free tier

By default the bot only answers what's in `data/intents.csv`. To let it
answer arbitrary questions too, using **Google Gemini's free tier** (no
credit card required):

1. Go to https://aistudio.google.com/apikey and sign in with a Google
   account, then click "Create API key". It's free — no billing setup
   needed to start.
2. Provide the key to the app using **one** of these two methods:

   **Option A — save it to a file (set once, never repeat it):**
   Open `gemini_key.txt` in the project root and replace its placeholder
   text with your real key (nothing else in the file, just the key).
   The server checks this file automatically every time it starts — no
   environment variable needed. Keep this file private; `.gitignore`
   already excludes it from version control.

   **Option B — environment variable (only lasts for the current terminal session):**
   ```bash
   # Windows (PowerShell)
   $env:GEMINI_API_KEY="your-key-here"

   # macOS/Linux
   export GEMINI_API_KEY="your-key-here"
   ```

   If both are set, the environment variable takes priority over the file.
3. Run the server as usual. On startup it prints whether AI fallback is
   enabled or disabled, and the chat UI's status line reflects this too.

## Build & Run

From the project root (`chatbot-java/`):

### Windows (Quick Run)
Double-click `run.bat` or run:
```bat
run.bat
```

### Manual Run
```bash
# Compile
javac -d out src/main/java/chatbot/*.java src/test/java/chatbot/*.java

# Run
java -cp out chatbot.ChatbotServer
```

Then open **http://localhost:5000** in your browser.

To run on a different port:
```bash
PORT=8080 java -cp out chatbot.ChatbotServer
```

## Running the tests

```bash
javac -d out src/main/java/chatbot/*.java src/test/java/chatbot/*.java
java -cp out chatbot.ChatEngineTest
```

## Project structure

```
chatbot-java/
├── data/
│   ├── intents.csv          # predefined patterns & responses
│   └── chat_log.csv         # created at runtime — every turn logged here
├── gemini_key.txt           # (optional) save your Gemini API key here once
├── run.bat                  # One-click Windows build and launch script
├── .gitignore                # excludes out/, chat_log.csv, gemini_key.txt
├── public/
│   ├── index.html           # chat UI (chips, clear chat, timestamps)
│   ├── style.css
│   └── script.js            # talks to /api/chat, /api/health, /api/reset
├── src/
│   ├── main/java/chatbot/
│   │   ├── ChatEngine.java   # intent loading + weighted keyword matching + dynamic placeholders
│   │   ├── AiClient.java     # Gemini API client (gemini-2.5-flash) with session history
│   │   ├── ChatLogger.java   # appends each turn to data/chat_log.csv
│   │   └── ChatbotServer.java# Virtual-threaded HTTP server + routes + session handling
│   └── test/java/chatbot/
│       └── ChatEngineTest.java  # plain-Java tests, no framework needed
└── README.md
```

## Extending it

- Add more rows to `data/intents.csv` (format: `intent,pattern1|pattern2|...,response`)
  to teach it new replies.
- Dynamic tokens: you can use `{time}` in responses in `data/intents.csv` to
  inject the current system time and date formatted cleanly.
- `ChatEngine.LOW_CONFIDENCE_THRESHOLD` controls how confident a local match
  needs to be before the server trusts it over asking the AI.
