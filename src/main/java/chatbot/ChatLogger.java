package chatbot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * ChatLogger
 *
 * Appends one CSV line per conversation turn to data/chat_log.csv, so you can
 * see what people actually ask the bot and use it to grow data/intents.csv
 * over time. Best-effort only: logging failures are printed to stderr but
 * never crash the request.
 */
public class ChatLogger {

    private static final String LOG_PATH = "data/chat_log.csv";

    public static synchronized void log(String sessionId, String message, String intent, double confidence) {
        try {
            Path path = Path.of(LOG_PATH);
            boolean isNew = !Files.exists(path);
            try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_PATH, true))) {
                if (isNew) {
                    writer.println("timestamp,session_id,message,intent,confidence");
                }
                writer.println(String.join(",",
                        Instant.now().toString(),
                        csvEscape(sessionId),
                        csvEscape(message),
                        csvEscape(intent),
                        String.valueOf(confidence)));
            }
        } catch (IOException e) {
            System.err.println("Chat log write failed: " + e.getMessage());
        }
    }

    /** Quotes a field and escapes embedded quotes, since messages may contain commas or quotes. */
    private static String csvEscape(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
