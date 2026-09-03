package chatbot;

import java.io.IOException;

/**
 * ChatEngineTest
 *
 * A minimal plain-Java test runner for ChatEngine — no JUnit dependency,
 * consistent with the rest of the project's "standard library only" approach.
 *
 * Run with (from the project root, so data/intents.csv resolves):
 *   javac -d out src/main/java/chatbot/*.java src/test/java/chatbot/*.java
 *   java -cp out chatbot.ChatEngineTest
 *
 * Exits with a non-zero status if any check fails, so it can be wired into
 * a CI step later without needing a test framework on the classpath.
 */
public class ChatEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        ChatEngine engine = new ChatEngine("data/intents.csv");

        check("greeting matches 'hi'", "greeting", engine.getResponse("hi").intent);
        check("greeting matches tanglish 'vanakkam'", "greeting", engine.getResponse("vanakkam").intent);
        check("goodbye matches 'see you'", "goodbye", engine.getResponse("see you later").intent);
        check("identity question matches 'who are you'", "identity", engine.getResponse("who are you?").intent);
        check("unknown input falls back", "fallback", engine.getResponse("asdkjhasdkjh random gibberish").intent);
        check("blank input falls back", "fallback", engine.getResponse("   ").intent);

        ChatEngine.ChatResult confident = engine.getResponse("good morning");
        check("clear phrase match is confident", true, ChatEngine.isConfident(confident));

        ChatEngine.ChatResult weak = engine.getResponse("hi, I was wondering if you could possibly help me understand something unrelated");
        check("single weak keyword in a long sentence is not confident", false, ChatEngine.isConfident(weak));

        ChatEngine.ChatResult timeResult = engine.getResponse("what time is it");
        check("time question matches 'time' intent", "time", timeResult.intent);
        check("time reply substitutes dynamic {time} token", false, timeResult.reply.contains("{time}"));
        check("time reply contains current year", true, timeResult.reply.contains(String.valueOf(java.time.Year.now().getValue())));

        ChatEngine.ChatResult cmResult = engine.getResponse("who is cm of tamil nadu");
        check("cm question matches 'cm_tamilnadu' intent", "cm_tamilnadu", cmResult.intent);
        check("cm reply mentions Vijay", true, cmResult.reply.contains("Vijay"));

        ChatEngine.ChatResult joke1 = engine.getResponse("tell me a joke");
        check("tell me a joke matches 'joke' intent", "joke", joke1.intent);
        ChatEngine.ChatResult joke2 = engine.getResponse("another joke");
        check("another joke matches 'joke' intent", "joke", joke2.intent);
        check("consecutive jokes rotate to a different joke", false, joke1.reply.equals(joke2.reply));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String description, String expected, String actual) {
        if (expected.equals(actual)) {
            System.out.println("PASS: " + description);
            passed++;
        } else {
            System.out.println("FAIL: " + description + " (expected \"" + expected + "\", got \"" + actual + "\")");
            failed++;
        }
    }

    private static void check(String description, boolean expected, boolean actual) {
        if (expected == actual) {
            System.out.println("PASS: " + description);
            passed++;
        } else {
            System.out.println("FAIL: " + description + " (expected " + expected + ", got " + actual + ")");
            failed++;
        }
    }
}
