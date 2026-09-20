package chatbot;

import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * SystemController
 *
 * The Jarvis execution engine for Windows OS automation.
 * Handles app launching, web/YouTube searches, volume adjustments,
 * screenshot capture, file/note creation, and live hardware diagnostics.
 */
public class SystemController {

    private static final Map<String, String> APP_ALIASES = new HashMap<>();

    static {
        APP_ALIASES.put("notepad", "notepad");
        APP_ALIASES.put("notes", "notepad");
        APP_ALIASES.put("calc", "calc");
        APP_ALIASES.put("calculator", "calc");
        APP_ALIASES.put("paint", "mspaint");
        APP_ALIASES.put("mspaint", "mspaint");
        APP_ALIASES.put("cmd", "cmd");
        APP_ALIASES.put("terminal", "cmd");
        APP_ALIASES.put("powershell", "powershell");
        APP_ALIASES.put("task manager", "taskmgr");
        APP_ALIASES.put("taskmgr", "taskmgr");
        APP_ALIASES.put("chrome", "chrome");
        APP_ALIASES.put("browser", "chrome");
        APP_ALIASES.put("edge", "msedge");
        APP_ALIASES.put("spotify", "spotify");
        APP_ALIASES.put("settings", "ms-settings:");
        APP_ALIASES.put("vs code", "code");
        APP_ALIASES.put("vscode", "code");
        APP_ALIASES.put("code", "code");
        APP_ALIASES.put("explorer", "explorer");
    }

    /** Launches a desktop application by name or alias. */
    public static String launchApp(String appName) {
        if (appName == null || appName.isBlank()) {
            return "Application name cannot be empty.";
        }
        String cleanName = appName.trim().toLowerCase();
        String command = APP_ALIASES.getOrDefault(cleanName, cleanName);

        try {
            new ProcessBuilder("cmd.exe", "/c", "start", "", command).start();
            return "Launching " + appName + ", Sir.";
        } catch (IOException e) {
            return "Could not launch " + appName + ": " + e.getMessage();
        }
    }

    /** Opens an existing folder in Windows Explorer (Downloads, Desktop, Documents, etc.). */
    public static String openFolder(String folderName) {
        String clean = (folderName == null ? "" : folderName.trim().toLowerCase());
        String path;
        String userHome = System.getProperty("user.home");

        switch (clean) {
            case "downloads":
                path = userHome + "\\Downloads";
                break;
            case "desktop":
                path = userHome + "\\Desktop";
                break;
            case "documents":
                path = userHome + "\\Documents";
                break;
            case "pictures":
                path = userHome + "\\Pictures";
                break;
            default:
                path = folderName;
                break;
        }

        try {
            new ProcessBuilder("explorer.exe", path).start();
            return "Opening " + clean + " folder for you.";
        } catch (IOException e) {
            return "Could not open folder " + path + ": " + e.getMessage();
        }
    }

    /** Opens a URL in the user's default browser. */
    public static String openUrl(String url) {
        if (url == null || url.isBlank()) return "URL cannot be empty.";
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                new ProcessBuilder("cmd.exe", "/c", "start", "", url).start();
            }
            return "Opening link in your browser.";
        } catch (Exception e) {
            return "Could not open URL: " + e.getMessage();
        }
    }

    /** Searches Google for a query. */
    public static String searchGoogle(String query) {
        if (query == null || query.isBlank()) return "Please specify what you want to search.";
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        return openUrl("https://www.google.com/search?q=" + encoded);
    }

    /** Searches or plays a query on YouTube. */
    public static String searchYouTube(String query) {
        if (query == null || query.isBlank()) {
            return openUrl("https://www.youtube.com");
        }
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        openUrl("https://www.youtube.com/results?search_query=" + encoded);
        return "Playing/Searching \"" + query.trim() + "\" on YouTube, Sir.";
    }

    /**
     * Changes master audio volume on Windows.
     * direction: "up", "down", or "mute"
     */
    public static String changeVolume(String direction) {
        int keyCode;
        int count = 4; // Each tap is ~2% volume
        String actionName;

        switch (direction.toLowerCase().trim()) {
            case "up":
                keyCode = 175; // VK_VOLUME_UP
                actionName = "Volume increased.";
                break;
            case "down":
                keyCode = 174; // VK_VOLUME_DOWN
                actionName = "Volume decreased.";
                break;
            case "mute":
            case "unmute":
                keyCode = 173; // VK_VOLUME_MUTE
                count = 1;
                actionName = "Audio mute toggled.";
                break;
            default:
                return "Unknown volume command. Use 'up', 'down', or 'mute'.";
        }

        try {
            StringBuilder script = new StringBuilder("$o = New-Object -ComObject WScript.Shell; ");
            for (int i = 0; i < count; i++) {
                script.append("$o.SendKeys([char]").append(keyCode).append("); ");
            }
            new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", script.toString()).start();
            return actionName;
        } catch (IOException e) {
            return "Failed to adjust volume: " + e.getMessage();
        }
    }

    /** Reads battery percentage on Windows. */
    public static String getBatteryStatus() {
        try {
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command",
                    "Write-Output ((Get-CimInstance Win32_Battery).EstimatedChargeRemaining.ToString() + '%')"
            ).start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    return "Battery is at " + line.trim() + ".";
                }
            }
        } catch (Exception ignored) {}
        return "Battery status currently unavailable.";
    }

    /** Returns free and total disk space on the primary drive. */
    public static String getDiskStatus() {
        File drive = new File("C:\\");
        long freeBytes = drive.getFreeSpace();
        long totalBytes = drive.getTotalSpace();

        double freeGb = Math.round((freeBytes / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0;
        double totalGb = Math.round((totalBytes / (1024.0 * 1024.0 * 1024.0)) * 10.0) / 10.0;

        return "Drive C: has " + freeGb + " GB free out of " + totalGb + " GB.";
    }

    /** Returns quick system statistics (RAM and CPU cores). */
    public static String getSystemInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);

        return "System CPU Cores: " + cores + " cores. JVM Memory: "
                + (totalMemory - freeMemory) + " MB used / " + maxMemory + " MB max allocated.";
    }

    /** Captures a full-screen screenshot, saves it to the Desktop, and opens it. */
    public static String captureScreenshot() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenCapture = robot.createScreenCapture(screenRect);

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String desktopPath = System.getProperty("user.home") + "\\Desktop";
            File outputFile = new File(desktopPath, "Screenshot_" + timeStamp + ".png");

            ImageIO.write(screenCapture, "png", outputFile);

            // Open the saved screenshot
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(outputFile);
            }

            return "Screenshot captured and saved to your Desktop: " + outputFile.getName();
        } catch (Exception e) {
            return "Could not take screenshot: " + e.getMessage();
        }
    }

    /** Creates a quick text note on the Desktop and opens it in Notepad. */
    public static String createDesktopNote(String title, String content) {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String desktopPath = System.getProperty("user.home") + "\\Desktop";
            String filename = (title == null || title.isBlank() ? "Note_" + timeStamp : title.trim()) + ".txt";
            File noteFile = new File(desktopPath, filename);

            try (FileWriter writer = new FileWriter(noteFile)) {
                writer.write("--- Note Created: " + new Date() + " ---\n\n");
                if (content != null) writer.write(content);
            }

            new ProcessBuilder("notepad.exe", noteFile.getAbsolutePath()).start();
            return "Created note '" + filename + "' on your Desktop and opened it in Notepad, Sir.";
        } catch (IOException e) {
            return "Failed to create note: " + e.getMessage();
        }
    }

    /** Creates a new folder on the Desktop. */
    public static String createDesktopFolder(String folderName) {
        if (folderName == null || folderName.isBlank()) return "Please specify a folder name.";
        String desktopPath = System.getProperty("user.home") + "\\Desktop";
        File dir = new File(desktopPath, folderName.trim());
        if (dir.exists()) {
            return "Folder '" + folderName + "' already exists on your Desktop.";
        }
        if (dir.mkdir()) {
            return "Created folder '" + folderName + "' on your Desktop, Sir.";
        } else {
            return "Could not create folder '" + folderName + "'.";
        }
    }

    /** Locks the Windows workstation. */
    public static String lockWorkstation() {
        try {
            new ProcessBuilder("rundll32.exe", "user32.dll,LockWorkStation").start();
            return "Locking your workstation now, Sir.";
        } catch (IOException e) {
            return "Could not lock screen: " + e.getMessage();
        }
    }

    /**
     * Executes arbitrary PowerShell commands safely on Windows for general Jarvis tasks.
     * Blocks catastrophic system commands (formatting, system32 destruction).
     */
    public static String executePowerShell(String script) {
        if (script == null || script.isBlank()) return "Empty command.";

        // Security Guardrails: Protect critical OS paths and formats
        String lower = script.toLowerCase();
        if (lower.contains("format ") || lower.contains("system32") || lower.contains("del /f /s /q c:\\")
                || lower.contains("remove-item -recurse -force c:\\windows")) {
            return "⚠️ Blocked for security: That command could damage your Windows operating system.";
        }

        try {
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < 2000) {
                        output.append(line).append("\n");
                    }
                }
            }

            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after 15 seconds.";
            }

            String result = output.toString().trim();
            return result.isEmpty() ? "Command executed successfully, Sir." : result;
        } catch (Exception e) {
            return "Execution failed: " + e.getMessage();
        }
    }

    /** Simulates keyboard hotkeys like Win+D (show desktop) or Alt+Tab. */
    public static String simulateHotkey(String combo) {
        if (combo == null) return "Invalid hotkey.";
        try {
            Robot robot = new Robot();
            String clean = combo.toLowerCase().trim();
            if (clean.equals("win+d") || clean.equals("show_desktop") || clean.equals("minimize_all")) {
                robot.keyPress(java.awt.event.KeyEvent.VK_WINDOWS);
                robot.keyPress(java.awt.event.KeyEvent.VK_D);
                robot.keyRelease(java.awt.event.KeyEvent.VK_D);
                robot.keyRelease(java.awt.event.KeyEvent.VK_WINDOWS);
                return "Toggled Show Desktop (Win + D), Sir.";
            } else if (clean.equals("alt+tab")) {
                robot.keyPress(java.awt.event.KeyEvent.VK_ALT);
                robot.keyPress(java.awt.event.KeyEvent.VK_TAB);
                robot.keyRelease(java.awt.event.KeyEvent.VK_TAB);
                robot.keyRelease(java.awt.event.KeyEvent.VK_ALT);
                return "Switched window (Alt + Tab), Sir.";
            }
            return "Hotkey executed: " + combo;
        } catch (Exception e) {
            return "Hotkey failed: " + e.getMessage();
        }
    }
}
