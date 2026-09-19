package chatbot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
            return new ChatResult(formatDynamicResponse(fallbackResponse), "fallback", 0.0);
        }

        int wordCount = Math.max(1, normalized.split("\\s+").length);
        double confidence = Math.min(1.0, bestScore / wordCount);
        confidence = Math.round(confidence * 100.0) / 100.0;
        return new ChatResult(formatDynamicResponse(bestIntent.pickResponse()), bestIntent.name, confidence);
    }

    /** Replaces dynamic tokens like {time} or {date} with live system values. */
    public static String formatDynamicResponse(String response) {
        if (response == null) return null;
        if (response.contains("{time}")) {
            ZonedDateTime now = ZonedDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a (EEEE, MMMM d, yyyy)");
            response = response.replace("{time}", now.format(formatter));
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
}
