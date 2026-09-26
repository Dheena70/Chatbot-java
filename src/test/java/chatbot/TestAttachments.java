package chatbot;

import java.util.List;

public class TestAttachments {
    public static void main(String[] args) {
        String testJson = "{\"message\":\"look at this image\",\"sessionId\":\"sess-123\",\"attachments\":["
                + "{\"name\":\"sample.png\",\"type\":\"image/png\",\"size\":1024,\"base64\":\"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==\"},"
                + "{\"name\":\"code.py\",\"type\":\"text/x-python\",\"size\":50,\"textContent\":\"print('hello jarvis')\"}"
                + "]}";

        List<AiClient.Attachment> list = ChatbotServer.extractAttachments(testJson);
        if (list.size() != 2) {
            System.err.println("FAILED: expected 2 attachments, got " + list.size());
            System.exit(1);
        }

        AiClient.Attachment att1 = list.get(0);
        if (!"sample.png".equals(att1.name) || !att1.isImage() || att1.base64 == null) {
            System.err.println("FAILED: att1 invalid: " + att1.name);
            System.exit(1);
        }

        AiClient.Attachment att2 = list.get(1);
        if (!"code.py".equals(att2.name) || !"print('hello jarvis')".equals(att2.textContent)) {
            System.err.println("FAILED: att2 invalid: " + att2.name + " text: " + att2.textContent);
            System.exit(1);
        }

        System.out.println("PASS: Attachment parsing and multimodal extraction verified successfully!");
    }
}
