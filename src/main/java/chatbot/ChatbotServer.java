package chatbot;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatbotServer
 *
 * A minimal HTTP server built entirely on the standard JDK (com.sun.net.httpserver) —
 * no Spring, no external frameworks. Serves the chat UI (static files under /public)
 * and a JSON API endpoint (/api/chat) backed by ChatEngine, with optional AI fallback
 * via AiClient and short per-session conversation memory.
 *
 * Run with:
 *   javac -d out src/main/java/chatbot/*.java
 *   java -cp out chatbot.ChatbotServer
 *
 * Then open http://localhost:5000 (or $PORT, if set) in a browser.
 */
public class ChatbotServer {

    private static final int DEFAULT_PORT = 5000;
    private static final int MAX_HISTORY_TURNS = 6; // 3 user + 3 bot turns of context sent to the AI

    private static ChatEngine engine;
    private static AiClient aiClient;

    /** Per-session rolling conversation history, used only to give the AI short-term memory. */
    private static final Map<String, Deque<AiClient.Turn>> sessionHistory = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        engine = new ChatEngine("data/intents.csv");

        String apiKey = resolveApiKey();
        aiClient = new AiClient(apiKey);
        if (aiClient.isConfigured()) {
            System.out.println("AI fallback enabled.");
        } else {
            System.out.println("AI fallback DISABLED — set GEMINI_API_KEY (env var) "
                    + "or create gemini_key.txt in the project root to enable it.");
        }

        int port = DEFAULT_PORT;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT env value \"" + portEnv + "\", falling back to " + DEFAULT_PORT);
            }
        }

        HttpServer server = null;
        int targetPort = port;
        for (int p = targetPort; p <= targetPort + 5; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                port = p;
                break;
            } catch (java.net.BindException e) {
                System.err.println("Port " + p + " is in use, trying port " + (p + 1) + "...");
            }
        }
        if (server == null) {
            throw new java.net.BindException("Could not bind to any port between " + targetPort + " and " + (targetPort + 5));
        }

        server.createContext("/api/chat", new ChatHandler());
        server.createContext("/api/reset", new ResetHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/", new StaticFileHandler("public"));

        ExecutorService executor;
        try {
            var method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            executor = (ExecutorService) method.invoke(null);
            System.out.println("Concurrency: Using Java Virtual Threads.");
        } catch (Exception e) {
            executor = Executors.newCachedThreadPool();
            System.out.println("Concurrency: Using cached thread pool.");
        }
        server.setExecutor(executor);
        server.start();

        System.out.println("=================================================");
        System.out.println("  Chatbot server running at http://localhost:" + port);
        System.out.println("=================================================");
    }

    /** Handles POST /api/chat — accepts {"message": "..."} and returns {"reply","intent","confidence"}. */
    static class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    handleOptions(exchange);
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}", null);
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String message = extractJsonStringField(body, "message");
                String sessionId = getOrCreateSessionId(exchange, body);

                if (message == null || message.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Message cannot be empty.\"}", sessionId);
                    return;
                }

                ChatEngine.ChatResult result = engine.getResponse(message);
                String reply = result.reply;
                String intent = result.intent;

                // Route to AI when local matching found nothing, weak match, or for jokes for infinite variety.
                boolean needsAi = ("fallback".equals(result.intent) || "joke".equals(result.intent) || !ChatEngine.isConfident(result))
                        && aiClient.isConfigured();

                if (needsAi) {
                    try {
                        List<AiClient.Turn> history = List.copyOf(
                                sessionHistory.getOrDefault(sessionId, new ArrayDeque<>()));
                        reply = aiClient.ask(message, history);
                        intent = "ai";
                    } catch (Exception e) {
                        System.err.println("AI fallback failed: " + e.getMessage());
                        // Keep the original canned/local reply on failure.
                    }
                }

                recordTurn(sessionId, message, reply);
                ChatLogger.log(sessionId, message, intent, result.confidence);

                String json = "{"
                        + "\"reply\":\"" + escapeJson(reply) + "\","
                        + "\"intent\":\"" + escapeJson(intent) + "\","
                        + "\"confidence\":" + result.confidence
                        + "}";
                sendJson(exchange, 200, json, sessionId);
            } catch (Throwable t) {
                System.err.println("Request error in ChatHandler: " + t.getMessage());
                try {
                    sendJson(exchange, 500, "{\"error\":\"Internal server error: " + escapeJson(t.getMessage()) + "\"}", null);
                } catch (IOException ignored) {}
            }
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            String json = "{\"status\":\"ok\",\"aiEnabled\":" + aiClient.isConfigured() + "}";
            sendJson(exchange, 200, json, null);
        }
    }

    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}", null);
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String sessionId = extractJsonStringField(body, "sessionId");
            if (sessionId != null && !sessionId.isBlank()) {
                sessionHistory.remove(sessionId);
            }
            sendJson(exchange, 200, "{\"status\":\"cleared\"}", sessionId);
        }
    }

    /** Serves static files (HTML/CSS/JS) from a given root directory. */
    static class StaticFileHandler implements HttpHandler {
        private final String root;

        StaticFileHandler(String root) {
            this.root = root;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath.equals("/")) requestPath = "/index.html";

            Path filePath = Path.of(root, requestPath).normalize();
            if (!filePath.startsWith(Path.of(root)) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(notFound); }
                return;
            }

            String contentType = guessContentType(filePath.toString());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] bytes = Files.readAllBytes(filePath);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        private String guessContentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            return "application/octet-stream";
        }
    }

    /**
     * Resolves the session id for this exchange: prefers a "sessionId" field in the
     * JSON body (the frontend generates and persists one via localStorage), falls
     * back to a "sessionId" cookie for other clients, and generates a fresh one
     * (returned via Set-Cookie) if neither is present.
     */
    private static String getOrCreateSessionId(HttpExchange exchange, String body) {
        String fromBody = extractJsonStringField(body, "sessionId");
        if (fromBody != null && !fromBody.isBlank()) {
            return fromBody;
        }

        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals("sessionId") && !kv[1].isBlank()) {
                    return kv[1];
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    /** Appends this exchange's turn to the session's rolling history, capped to MAX_HISTORY_TURNS. */
    private static void recordTurn(String sessionId, String userMessage, String botReply) {
        Deque<AiClient.Turn> history = sessionHistory.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(new AiClient.Turn("user", userMessage));
            history.addLast(new AiClient.Turn("model", botReply));
            while (history.size() > MAX_HISTORY_TURNS) {
                history.removeFirst();
            }
        }
    }

    /**
     * Resolves the Gemini API key: checks the GEMINI_API_KEY environment variable
     * first (useful for servers/CI), and if that's not set, falls back to reading
     * a plain-text file called gemini_key.txt in the project root — this lets you
     * save the key once and never have to set an environment variable again.
     * gemini_key.txt should contain nothing but the key itself (whitespace is trimmed).
     */
    private static String resolveApiKey() {
        String fromEnv = System.getenv("GEMINI_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }

        Path keyFile = Path.of("gemini_key.txt");
        if (Files.exists(keyFile)) {
            try {
                String fromFile = Files.readString(keyFile).trim();
                if (!fromFile.isBlank() && !fromFile.contains("paste-your-real-gemini-api-key-here")) {
                    return fromFile;
                }
            } catch (IOException e) {
                System.err.println("Could not read gemini_key.txt: " + e.getMessage());
            }
        }

        return null;
    }
    private static void sendJson(HttpExchange exchange, int status, String json, String sessionId) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if (sessionId != null) {
            exchange.getResponseHeaders().add("Set-Cookie", "sessionId=" + sessionId + "; Path=/; SameSite=Lax");
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    /** Very small helper to pull a string field out of a flat JSON body like {"message":"hi"}. */
    private static String extractJsonStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon == -1) return null;
        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote == -1) return null;
        StringBuilder sb = new StringBuilder();
        int i = firstQuote + 1;
        while (i < json.length() && json.charAt(i) != '"') {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++;
                char next = json.charAt(i);
                if (next == 'u' && i + 4 < json.length()) {
                    String hex = json.substring(i + 1, i + 5);
                    try {
                        c = (char) Integer.parseInt(hex, 16);
                        i += 4;
                    } catch (NumberFormatException ex) {
                        c = next;
                    }
                } else {
                    c = next;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
