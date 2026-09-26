package chatbot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatEngine
 *
 * Loads a predefined set of intents (pattern -> response pairs) from a CSV
 * file and matches incoming user messages against those patterns using
 * weighted keyword/substring scoring. This is the "predefined logic" the
 * chatbot uses to generate replies — plain Java, no external frameworks.
 */
public class ChatEngine {

    /** One row of the intents.csv file. Supports multiple pool responses separated by ;; */
    static class Intent {
        String name;
        List<String> patterns;
        String response;
        List<String> responses = new ArrayList<>();
        private int lastIndex = -1;

        Intent(String name, List<String> patterns, String response) {
            this.name = name;
            this.patterns = patterns;
            this.response = response;
            for (String r : response.split("\\s*;{2,}\\s*")) {
                String trimmed = r.trim();
                if (!trimmed.isEmpty()) this.responses.add(trimmed);
            }
            if (this.responses.isEmpty()) {
                this.responses.add(response);
            }
        }

        synchronized String pickResponse() {
            if (responses.size() <= 1) return responses.get(0);
            int idx;
            do {
                idx = ThreadLocalRandom.current().nextInt(responses.size());
            } while (idx == lastIndex && responses.size() > 1);
            lastIndex = idx;
            return responses.get(idx);
        }
    }

    private final List<Intent> intents = new ArrayList<>();
    private String fallbackResponse = "Sorry, I didn't understand that.";

    /** Below this confidence, a matched intent is treated as too weak to trust on its own. */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.34;

    public ChatEngine(String csvPath) throws IOException {
        loadIntents(csvPath);
    }

    private void loadIntents(String csvPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
            String line = reader.readLine(); // header row: intent,patterns,response
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = splitCsvLine(line, 3);
                if (parts.length < 3) continue;

                String intentName = parts[0].trim();
                String patternsRaw = parts[1].trim();
                String response = parts[2].trim();

                List<String> patterns = new ArrayList<>();
                for (String p : patternsRaw.split("\\|")) {
                    String trimmed = p.trim().toLowerCase();
                    if (!trimmed.isEmpty()) patterns.add(trimmed);
                }

                if (intentName.equals("fallback")) {
                    fallbackResponse = response;
                } else {
                    intents.add(new Intent(intentName, patterns, response));
                }
            }
        }
    }

    /** Splits a simple CSV line into at most `limit` fields (no quoted-comma support needed here). */
    private String[] splitCsvLine(String line, int limit) {
        return line.split(",", limit);
    }

    /** Result of matching a user message against known intents. */
    public static class ChatResult {
        public final String reply;
        public final String intent;
        public final double confidence;

        ChatResult(String reply, String intent, double confidence) {
            this.reply = reply;
            this.intent = intent;
            this.confidence = confidence;
        }
    }

    public ChatResult getResponse(String message) {
        if (message == null || message.isBlank()) {
            return new ChatResult(fallbackResponse, "fallback", 0.0);
        }

        String normalized = message.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
        if (normalized.isEmpty()) {
            return new ChatResult(fallbackResponse, "fallback", 0.0);
        }

        Intent bestIntent = null;
        double bestScore = 0;

        for (Intent intent : intents) {
            double score = 0;
            for (String pattern : intent.patterns) {
                if (matchesPattern(normalized, pattern)) {
                    // Weight longer / more specific patterns higher than single short words,
                    // so e.g. "good morning" outweighs a lone "hi" match elsewhere.
                    int patternWordCount = Math.max(1, pattern.split("\\s+").length);
                    score += patternWordCount;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestIntent = intent;
            }
        }

        if (bestIntent == null) {
            if (normalized.startsWith("play ") && normalized.length() > 5) {
                String songQuery = message.trim().replaceAll("(?i)^(?:play|listen to)\\s+", "").trim();
                return new ChatResult(SystemController.playYouTube(songQuery), "jarvis_play", 1.0);
            }
            return new ChatResult(formatDynamicResponse(fallbackResponse, message), "fallback", 0.0);
        }

        int wordCount = Math.max(1, normalized.split("\\s+").length);
        double confidence = Math.min(1.0, bestScore / wordCount);
        confidence = Math.round(confidence * 100.0) / 100.0;
        return new ChatResult(formatDynamicResponse(bestIntent.pickResponse(), message), bestIntent.name, confidence);
    }

    /** Replaces dynamic tokens like {time}, {battery}, {disk}, or system actions with live values and execution. */
    public static String formatDynamicResponse(String response) {
        return formatDynamicResponse(response, null);
    }

    public static String formatDynamicResponse(String response, String userMessage) {
        if (response == null) return null;
        if (response.contains("{time}")) {
            ZonedDateTime now = ZonedDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a (EEEE, MMMM d, yyyy)");
            response = response.replace("{time}", now.format(formatter));
        }
        if (response.contains("{battery}")) {
            response = response.replace("{battery}", SystemController.getBatteryStatus());
        }
        if (response.contains("{disk}")) {
            response = response.replace("{disk}", SystemController.getDiskStatus());
        }
        if (response.contains("{sysinfo}")) {
            response = response.replace("{sysinfo}", SystemController.getSystemInfo());
        }
        if (response.contains("{screenshot}")) {
            String target = extractScreenshotTarget(userMessage);
            response = response.replace("{screenshot}", SystemController.captureScreenshot(target));
        }
        if (response.contains("{vol_up}")) {
            response = response.replace("{vol_up}", SystemController.changeVolume("up"));
        }
        if (response.contains("{vol_down}")) {
            response = response.replace("{vol_down}", SystemController.changeVolume("down"));
        }
        if (response.contains("{vol_mute}")) {
            response = response.replace("{vol_mute}", SystemController.changeVolume("mute"));
        }
        if (response.contains("{notepad}")) {
            response = response.replace("{notepad}", SystemController.launchApp("notepad"));
        }
        if (response.contains("{calc}")) {
            response = response.replace("{calc}", SystemController.launchApp("calc"));
        }
        if (response.contains("{paint}")) {
            response = response.replace("{paint}", SystemController.launchApp("paint"));
        }
        if (response.contains("{taskmgr}")) {
            response = response.replace("{taskmgr}", SystemController.launchApp("taskmgr"));
        }
        if (response.contains("{cmd}")) {
            response = response.replace("{cmd}", SystemController.launchApp("cmd"));
        }
        if (response.contains("{settings}")) {
            response = response.replace("{settings}", SystemController.launchApp("settings"));
        }
        if (response.contains("{downloads}")) {
            response = response.replace("{downloads}", SystemController.openFolder("downloads"));
        }
        if (response.contains("{desktop}")) {
            response = response.replace("{desktop}", SystemController.openFolder("desktop"));
        }
        if (response.contains("{lock}")) {
            response = response.replace("{lock}", SystemController.lockWorkstation());
        }
        if (response.contains("{youtube}")) {
            response = response.replace("{youtube}", SystemController.searchYouTube(null));
        }
        if (response.contains("{wifi_on}")) {
            response = response.replace("{wifi_on}", SystemController.toggleRadio("wifi", "on"));
        }
        if (response.contains("{wifi_off}")) {
            response = response.replace("{wifi_off}", SystemController.toggleRadio("wifi", "off"));
        }
        if (response.contains("{bt_on}")) {
            response = response.replace("{bt_on}", SystemController.toggleRadio("bluetooth", "on"));
        }
        if (response.contains("{bt_off}")) {
            response = response.replace("{bt_off}", SystemController.toggleRadio("bluetooth", "off"));
        }
        if (response.contains("{darkmode_on}")) {
            response = response.replace("{darkmode_on}", SystemController.toggleDarkMode("on"));
        }
        if (response.contains("{darkmode_off}")) {
            response = response.replace("{darkmode_off}", SystemController.toggleDarkMode("off"));
        }
        if (response.contains("{screen_off}")) {
            response = response.replace("{screen_off}", SystemController.turnOffScreen());
        }
        if (response.contains("{close_notepad}")) {
            response = response.replace("{close_notepad}", SystemController.closeApp("notepad"));
        }
        if (response.contains("{close_calc}")) {
            response = response.replace("{close_calc}", SystemController.closeApp("calc"));
        }
        if (response.contains("{close_chrome}")) {
            response = response.replace("{close_chrome}", SystemController.closeApp("chrome"));
        }
        if (response.contains("{antigravity}")) {
            response = response.replace("{antigravity}", SystemController.launchApp("antigravity"));
        }
        if (response.contains("{close_antigravity}")) {
            response = response.replace("{close_antigravity}", SystemController.closeApp("antigravity"));
        }
        if (response.contains("{alarm}")) {
            response = response.replace("{alarm}", SystemController.setAlarm(null, "Alarm"));
        }
        if (response.contains("{timer}")) {
            response = response.replace("{timer}", SystemController.setTimer(60, "Timer Alert"));
        }
        if (response.contains("{whatsapp}")) {
            response = response.replace("{whatsapp}", SystemController.launchApp("whatsapp"));
        }
        if (response.contains("{telegram}")) {
            response = response.replace("{telegram}", SystemController.launchApp("telegram"));
        }
        if (response.contains("{instagram}")) {
            response = response.replace("{instagram}", SystemController.launchApp("instagram"));
        }
        if (response.contains("{chatgpt}")) {
            response = response.replace("{chatgpt}", SystemController.launchApp("chatgpt"));
        }
        if (response.contains("{vlc}")) {
            response = response.replace("{vlc}", SystemController.launchApp("vlc"));
        }
        if (response.contains("{word}")) {
            response = response.replace("{word}", SystemController.launchApp("word"));
        }
        if (response.contains("{excel}")) {
            response = response.replace("{excel}", SystemController.launchApp("excel"));
        }
        if (response.contains("{filmora}")) {
            response = response.replace("{filmora}", SystemController.launchApp("filmora"));
        }
        if (response.contains("{canva}")) {
            response = response.replace("{canva}", SystemController.launchApp("canva"));
        }
        if (response.contains("{anydesk}")) {
            response = response.replace("{anydesk}", SystemController.launchApp("anydesk"));
        }
        if (response.contains("{angrybirds}")) {
            response = response.replace("{angrybirds}", SystemController.launchApp("angry birds"));
        }
        if (response.contains("{beachbuggy}")) {
            response = response.replace("{beachbuggy}", SystemController.launchApp("beach buggy"));
        }
        if (response.contains("{solitaire}")) {
            response = response.replace("{solitaire}", SystemController.launchApp("solitaire"));
        }
        return response;
    }

    /** True if the given result is confident enough to trust without asking the AI too. */
    public static boolean isConfident(ChatResult result) {
        return !"fallback".equals(result.intent) && result.confidence >= LOW_CONFIDENCE_THRESHOLD;
    }

    /**
     * Matches pattern against normalized text respecting word boundaries.
     * Prevents short patterns like "hi" or "rain" from matching inside longer words
     * like "which", "this", or "train".
     */
    public static boolean matchesPattern(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()) return false;
        String regex = "(?<![a-z0-9])" + Pattern.quote(pattern) + "(?![a-z0-9])";
        return Pattern.compile(regex).matcher(text).find();
    }

    /**
     * Extracts screenshot target (app, site, window, desktop) from user message.
     * Supports patterns like "take screenshot on youtube", "screenshot of notepad",
     * "youtube screenshot", "youtube la screenshot", etc.
     */
    public static String extractScreenshotTarget(String message) {
        if (message == null || message.isBlank()) return null;
        String lower = message.toLowerCase().trim();

        if (lower.contains("screenshot") || lower.contains("screen shot") || lower.contains("capture screen") || lower.contains("snap screen")) {
            if (lower.contains("youtube") || lower.contains("you tube")) return "youtube";
            if (lower.contains("google")) return "google";
            if (lower.contains("desktop") || lower.contains("home screen")) return "desktop";
            if (lower.contains("notepad") || lower.contains("notes")) return "notepad";
            if (lower.contains("calc") || lower.contains("calculator")) return "calculator";
            if (lower.contains("paint") || lower.contains("mspaint")) return "paint";
            if (lower.contains("chrome") || lower.contains("browser")) return "chrome";
            if (lower.contains("edge")) return "edge";
            if (lower.contains("cmd") || lower.contains("command prompt")) return "cmd";
            if (lower.contains("powershell") || lower.contains("terminal")) return "powershell";
            if (lower.contains("vscode") || lower.contains("vs code") || lower.contains("code")) return "code";
            if (lower.contains("whatsapp")) return "whatsapp";
            if (lower.contains("telegram")) return "telegram";
            if (lower.contains("instagram")) return "instagram";
            if (lower.contains("spotify")) return "spotify";
            if (lower.contains("vlc")) return "vlc";
            if (lower.contains("word")) return "word";
            if (lower.contains("excel")) return "excel";
            if (lower.contains("powerpoint") || lower.contains("ppt")) return "powerpoint";
            if (lower.contains("canva")) return "canva";
            if (lower.contains("filmora")) return "filmora";
            if (lower.contains("anydesk")) return "anydesk";
            if (lower.contains("antigravity")) return "antigravity";
            if (lower.contains("taskmgr") || lower.contains("task manager")) return "taskmgr";
            if (lower.contains("previous") || lower.contains("background") || lower.contains("behind")) return "previous";

            // Extract pattern "on <target>", "of <target>", "in <target>"
            Pattern p = Pattern.compile("(?:on|of|in|for|at)\\s+([a-zA-Z0-9_\\-\\.]+)");
            Matcher m = p.matcher(lower);
            if (m.find()) {
                String candidate = m.group(1).trim();
                if (!candidate.equals("my") && !candidate.equals("the") && !candidate.equals("this") && !candidate.equals("screen")) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
