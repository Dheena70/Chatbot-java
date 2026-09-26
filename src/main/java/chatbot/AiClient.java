package chatbot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * AiClient
 *
 * Calls the Google Gemini API so the chatbot can answer questions that the
 * local rule-based ChatEngine doesn't recognize (or answers with low confidence).
 * Uses only the JDK's built-in java.net.http.HttpClient — no external HTTP or
 * JSON libraries.
 *
 * Gemini has a genuine free tier (no credit card required): get a key at
 * https://aistudio.google.com/apikey and set it as the GEMINI_API_KEY
 * environment variable. Free-tier requests are rate-limited (a handful of
 * requests per minute) — fine for a demo/project, not for heavy production
 * traffic.
 */
public class AiClient {

    private static final String[] DEFAULT_MODELS = {
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.6-flash",
        "gemini-3.1-flash-lite"
    };

    private final String apiKey;
    private final HttpClient httpClient;
    private final List<String> models;

    public AiClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        String envModel = System.getenv("GEMINI_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            this.models = List.of(envModel.trim());
        } else {
            this.models = List.of(DEFAULT_MODELS);
        }
    }

    /** True if an API key was configured (i.e. AI fallback is usable). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** One turn of prior conversation, used to give the AI short-term memory of the session. */
    public static class Turn {
        public final String role; // "user" or "model"
        public final String text;

        public Turn(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    /** Single-shot call with no conversation history. */
    public String ask(String userMessage) throws IOException, InterruptedException {
        return ask(userMessage, List.of());
    }

    /**
     * Sends the user's message to Gemini — including up to a few prior turns of
     * this session for context — and returns the reply text. Automatically tries
     * candidate models if one is under temporary high demand (503) or deprecated (404).
     */
    public String ask(String userMessage, List<Turn> history) throws IOException, InterruptedException {
        StringBuilder contents = new StringBuilder("[");
        for (Turn turn : history) {
            contents.append("{\"role\":\"").append(turn.role).append("\",")
                    .append("\"parts\":[{\"text\":\"").append(escapeJson(turn.text)).append("\"}]},");
        }
        contents.append("{\"role\":\"user\",\"parts\":[{\"text\":\"")
                .append(escapeJson(userMessage)).append("\"}]}]");
        String systemInstruction = "{\"parts\":[{\"text\":\"You are DevBot (JARVIS), an advanced autonomous Jarvis-like Personal AI Assistant with real control over the user's Windows laptop, created by Dheena. "
                + "The current year is 2026. The Chief Minister of Tamil Nadu is C. Joseph Vijay (TVK), who assumed office on May 10, 2026. "
                + "You have full power to execute computer tasks on the laptop by starting your reply with an action tag:\\n"
                + "- [ACTION:toggle_wifi:<on|off>] Directly turn Wi-Fi ON or OFF! NEVER just open settings!\\n"
                + "- [ACTION:toggle_bluetooth:<on|off>] Directly turn Bluetooth ON or OFF! NEVER just open settings!\\n"
                + "- [ACTION:close_app:<app>] Directly close, terminate, or turn off an application (e.g. antigravity, whatsapp, telegram, instagram, chatgpt, vlc, anydesk, canva, filmora, word, excel, powerpoint, angry birds, beach buggy, solitaire, notepad, calc, chrome, edge, spotify, paint, code, cmd)!\\n"
                + "- [ACTION:toggle_darkmode:<on|off>] Directly turn Dark Mode ON or OFF!\\n"
                + "- [ACTION:turn_off_screen] Directly turn off the monitor display (sleep screen)!\\n"
                + "- [ACTION:volume:<up|down|mute|unmute>] Adjust speaker volume or mute/unmute audio directly!\\n"
                + "- [ACTION:open_app:<app>] Launch app (e.g. antigravity, whatsapp, telegram, instagram, chatgpt, perplexity, vlc, anydesk, canva, filmora, word, excel, powerpoint, angry birds, beach buggy, solitaire, notepad, calc, paint, cmd, taskmgr, chrome, spotify, code)\\n"
                + "- [ACTION:powershell:<command>] Execute ANY Windows PowerShell command to accomplish the user's task (e.g. check IP, list files, check RAM, kill process, network check, organize files, manage software)\\n"
                + "- [ACTION:search_youtube:<query>] Play or search videos on YouTube (ONLY if user explicitly asks for YouTube)\\n"
                + "- [ACTION:search_google:<query>] Search Google (ONLY if user explicitly asks to search the web)\\n"
                + "- [ACTION:set_timer:<seconds>|<label>] Set countdown timer with audio chime and popup notification\\n"
                + "- [ACTION:set_alarm:<time>|<label>] Set alarm or open Windows Clock alarm app\\n"
                + "- [ACTION:send_whatsapp:<phone_or_name>|<message>] Open WhatsApp with message drafted for recipient\\n"
                + "- [ACTION:open_url:<url>] Open website\\n"
                + "- [ACTION:open_folder:<downloads|desktop|documents>] Open folder in File Explorer\\n"
                + "- [ACTION:screenshot] Capture full screen and open image\\n"
                + "- [ACTION:create_note:<title>|<content>] Create text note on Desktop and open in Notepad\\n"
                + "- [ACTION:create_folder:<foldername>] Create folder on Desktop\\n"
                + "- [ACTION:hotkey:<win+d|alt+tab>] Show desktop / switch window\\n"
                + "- [ACTION:lock] Lock Windows workstation\\n"
                + "- [ACTION:battery] Check battery percentage\\n"
                + "- [ACTION:disk] Check C: drive free space\\n"
                + "CRITICAL RULE: When the user asks you to turn ON, turn OFF, open, or close something (e.g. Wi-Fi, Bluetooth, Mute, Volume, Dark Mode, Apps, Screen), NEVER just open the Settings screen or take them to a location! ALWAYS execute the direct action tag so the feature is turned ON or turned OFF directly by you without requiring the user to do anything!\\n"
                + "CRITICAL BROWSER RULE: NEVER open the browser or search Google/YouTube when the user asks to open an app, software, game, or tool! If the user says 'open antigravity' or 'open antigravity ide', use [ACTION:open_app:antigravity]! Do NOT treat casual words like 'unna' as opening Antigravity! NEVER redirect app opening commands to the browser!\\n"
                + "CRITICAL OS ASSISTANT & SELF-HEALING RULE: You are the user's personal AI assistant with full system OS control. Execute all tasks requested (sending messages, setting alarms, running system scripts, managing software). If an error occurs, you will receive a diagnostic error notification to autonomously fix it!\\n"
                + "Always answer in a polite, highly capable, loyal Jarvis persona (calling the user Sir or Dheena). "
                + "When the user asks you to do ANY laptop task, ALWAYS choose the best action tag (use [ACTION:powershell:...] for general/custom tasks) so it gets performed live on their computer! "
                + "If no system action is requested, reply normally without any [ACTION] tag.\"}]}";

        String requestBody = "{\"system_instruction\":" + systemInstruction + ",\"contents\":" + contents + "}";

        Exception lastException = null;
        for (String model : models) {
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String text = extractFirstTextBlock(response.body());
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                } else {
                    lastException = new RuntimeException("Model " + model + " returned "
                            + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (lastException instanceof IOException) throw (IOException) lastException;
        if (lastException instanceof InterruptedException) throw (InterruptedException) lastException;
        throw new RuntimeException("All Gemini models failed: "
                + (lastException != null ? lastException.getMessage() : "unknown error"));
    }

    /**
     * Pulls the first "text" field out of the response's
     * candidates[0].content.parts[0].text path, decoding standard JSON escapes
     * (including \\uXXXX). Hand-rolled on purpose to avoid a JSON library dependency.
     */
    private String extractFirstTextBlock(String json) {
        String key = "\"text\"";
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
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                        break;
                    default: sb.append(next);
                }
            } else {
                sb.append(c);
            }
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
