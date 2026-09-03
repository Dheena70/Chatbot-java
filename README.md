# 🤖 DevBot — High-Performance Java Chatbot with Gemini AI

[![Java Version](https://img.shields.io/badge/Java-11%20%7C%2017%20%7C%2021%20%7C%2025-orange.svg)](https://openjdk.org/)
[![Framework](https://img.shields.io/badge/Framework-Zero%20Dependencies-brightgreen.svg)]()
[![Concurrency](https://img.shields.io/badge/Concurrency-Virtual%20Threads-blue.svg)]()
[![AI](https://img.shields.io/badge/AI%20Fallback-Google%20Gemini-8e44ad.svg)](https://aistudio.google.com/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)]()

A lightweight, blazing-fast, hybrid rule-based and AI-powered chatbot built with **100% pure standard Java** — zero external frameworks, zero third-party dependencies, and zero build tool overhead (no Spring Boot, no Maven, no Gradle required).

It delivers sub-millisecond local responses for predefined intents and seamlessly fails over to **Google Gemini AI** with rolling conversation memory for open-ended queries, general knowledge, jokes, and creative tasks.

---

## ✨ Features

- ⚡ **Zero External Libraries**: Built exclusively on the standard JDK (`com.sun.net.httpserver` and `java.net.http.HttpClient`). Compiles in under 2 seconds.
- 🧵 **Java Virtual Threads**: Uses `Executors.newVirtualThreadPerTaskExecutor()` to handle hundreds of concurrent chat requests without thread exhaustion or blocking.
- 🧠 **Hybrid Edge-First Intelligence**:
  - **Local Rule Engine**: Weighted keyword/substring scoring from `data/intents.csv`.
  - **Dynamic Time & Date**: Resolves live system clock tokens (`{time}`) on the fly.
  - **Gemini AI Fallback**: Resilient multi-model failover (`gemini-3.5-flash`, `gemini-flash-latest`, `gemini-3.6-flash`) with automatic retry.
- 💭 **Rolling Session Memory**: Retains multi-turn conversation context per session so users can ask contextual follow-ups.
- 🎨 **Modern Responsive UI**:
  - Desktop-optimized 860px layout with smooth transitions and dark/light theme toggle.
  - Interactive clickable suggestion chips.
  - Live message timestamps and safe Markdown/code block rendering.
  - Clear Chat (reset session) button.
- 📊 **Audit Logging**: Appends every turn safely to `data/chat_log.csv` without impacting request latency.
- 🔒 **Security First**: Complete separation of secrets; API keys (`gemini_key.txt`) are strictly git-ignored.
- 🚀 **One-Click Runner**: Double-click `run.bat` on Windows to compile and launch immediately.

---

## 🏗️ Architecture

```text
 Browser (public/)                 ChatbotServer                ChatEngine / AiClient
 ┌────────────────┐   POST /api/chat   ┌────────────────┐   ┌──────────────────────────┐
 │ index.html      │ ─────────────────▶│  ChatHandler   │──▶│ ChatEngine.getResponse() │
 │ script.js       │                   │  (Virtual Th.) │   │  (CSV Weighted Scoring)  │
 │ style.css       │◀───────────────── │                │◀──┘                          │
 └────────────────┘   {reply,intent,   │                │   Low Confidence / Unmatched │
                        confidence}    │                │  ┌───────────────────────────┤
                                       │                │─▶│ AiClient.ask()            │
                                       └───────┬────────┘  │ (Google Gemini API w/     │
                                               │           │  Multi-Turn Session Memo) │
                                               ▼           └───────────────────────────┘
                                     ChatLogger → data/chat_log.csv
```

---

## 📋 Requirements

- **JDK 11 or later** (OpenJDK 17, 21, or 25 recommended for Virtual Threads).
- Compatible with Windows, macOS, and Linux.

---

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/<YOUR_USERNAME>/chatbot-java.git
cd chatbot-java
```

### 2. Run the Application

#### Windows (One-Click)
Double-click **`run.bat`** or run:
```bat
run.bat
```

#### macOS / Linux
```bash
# Compile
javac -d out src/main/java/chatbot/*.java src/test/java/chatbot/*.java

# Run
java -cp out chatbot.ChatbotServer
```

### 3. Open in Browser
Visit **[http://localhost:5000](http://localhost:5000)** in your browser!

---

## 🔑 Enabling Gemini AI Fallback (Optional — Free Tier)

To allow the chatbot to answer *any* general question, coding challenge, or generate endless jokes beyond the CSV:

1. Get a free API key at **[Google AI Studio](https://aistudio.google.com/apikey)** (no credit card required).
2. Save your key using either method:
   - **File (Recommended)**: Create `gemini_key.txt` in the project root and paste your key inside (already git-ignored).
   - **Environment Variable**:
     ```powershell
     # Windows PowerShell
     $env:GEMINI_API_KEY="your-api-key"
     ```
     ```bash
     # macOS/Linux
     export GEMINI_API_KEY="your-api-key"
     ```
3. Restart the server. The status indicator will switch to 🟢 **"Online — rule-based + AI fallback"**.

---

## 🧪 Running the Tests

The project includes a built-in plain Java test runner with zero testing framework dependencies:

```bash
javac -d out src/main/java/chatbot/*.java src/test/java/chatbot/*.java
java -cp out chatbot.ChatEngineTest
```

Expected output:
```text
PASS: greeting matches 'hi'
PASS: greeting matches tanglish 'vanakkam'
PASS: goodbye matches 'see you'
PASS: identity question matches 'who are you'
PASS: unknown input falls back
PASS: blank input falls back
PASS: clear phrase match is confident
PASS: single weak keyword in a long sentence is not confident
PASS: time question matches 'time' intent
PASS: time reply substitutes dynamic {time} token
PASS: time reply contains current year
PASS: cm question matches 'cm_tamilnadu' intent
PASS: cm reply mentions Vijay
PASS: tell me a joke matches 'joke' intent
PASS: another joke matches 'joke' intent
PASS: consecutive jokes rotate to a different joke

16 passed, 0 failed
```

---

## 📁 Project Structure

```text
chatbot-java/
├── data/
│   ├── intents.csv               # Predefined intent patterns and response pools
│   └── chat_log.csv              # Runtime conversation audit log (git-ignored)
├── public/
│   ├── index.html                # Responsive web chat interface
│   ├── style.css                 # Modern CSS layout, theme variables & animations
│   └── script.js                 # API client, Markdown renderer & session handling
├── src/
│   ├── main/java/chatbot/
│   │   ├── AiClient.java         # Resilient Gemini API client with auto-failover
│   │   ├── ChatEngine.java       # Keyword scoring engine & dynamic token resolver
│   │   ├── ChatLogger.java       # Non-blocking CSV audit logger
│   │   └── ChatbotServer.java    # Virtual-threaded HTTP server and REST endpoints
│   └── test/java/chatbot/
│       └── ChatEngineTest.java   # Standalone regression test suite
├── gemini_key.txt.example        # Template for API key configuration
├── run.bat                       # One-click Windows build and launch script
├── .gitignore                    # Prevents build artifacts and secrets from being committed
└── README.md                     # Project documentation
```

---

## 🛠️ Extending Intents

You can easily teach the bot new responses without writing Java code:

1. Open `data/intents.csv`.
2. Add a new row in this format:
   ```csv
   intent_name,keyword1|keyword2|phrase,Response 1 ;; Response 2 (optional pool)
   ```
   - Use `|` to separate matching keyword variations.
   - Use `;;` to define a pool of randomized responses.
   - Use `{time}` to inject the live system date and time.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
