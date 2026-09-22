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
import java.util.Base64;
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
        // Productivity
        APP_ALIASES.put("notepad", "notepad");
        APP_ALIASES.put("notes", "notepad");
        APP_ALIASES.put("calc", "calc");
        APP_ALIASES.put("calculator", "calc");
        APP_ALIASES.put("chrome", "chrome");
        APP_ALIASES.put("google chrome", "chrome");
        APP_ALIASES.put("browser", "chrome");
        APP_ALIASES.put("edge", "msedge");
        APP_ALIASES.put("microsoft edge", "msedge");
        APP_ALIASES.put("word", "shell:AppsFolder\\Microsoft.Office.WINWORD.EXE.15");
        APP_ALIASES.put("ms word", "shell:AppsFolder\\Microsoft.Office.WINWORD.EXE.15");
        APP_ALIASES.put("excel", "shell:AppsFolder\\Microsoft.Office.EXCEL.EXE.15");
        APP_ALIASES.put("ms excel", "shell:AppsFolder\\Microsoft.Office.EXCEL.EXE.15");
        APP_ALIASES.put("powerpoint", "shell:AppsFolder\\Microsoft.Office.POWERPNT.EXE.15");
        APP_ALIASES.put("ppt", "shell:AppsFolder\\Microsoft.Office.POWERPNT.EXE.15");
        APP_ALIASES.put("onenote", "shell:AppsFolder\\Microsoft.Office.ONENOTE.EXE.15");
        APP_ALIASES.put("outlook", "shell:AppsFolder\\Microsoft.OutlookForWindows_8wekyb3d8bbwe!Microsoft.OutlookforWindows");
        APP_ALIASES.put("acrobat", "shell:AppsFolder\\AcrobatReader");
        APP_ALIASES.put("adobe acrobat", "shell:AppsFolder\\AcrobatReader");
        APP_ALIASES.put("pdf", "shell:AppsFolder\\AcrobatReader");
        APP_ALIASES.put("explorer", "explorer");
        APP_ALIASES.put("file explorer", "explorer");

        // Social
        APP_ALIASES.put("whatsapp", "shell:AppsFolder\\5319275A.WhatsAppDesktop_cv1g1gvanyjgm!App");
        APP_ALIASES.put("whatsapp beta", "shell:AppsFolder\\5319275A.51895FA4EA97F_cv1g1gvanyjgm!App");
        APP_ALIASES.put("telegram", "shell:AppsFolder\\TelegramMessengerLLP.TelegramDesktop_t4vj0pshhgkwm!Telegram.TelegramDesktop.Store");
        APP_ALIASES.put("unigram", "shell:AppsFolder\\38833FF26BA1D.UnigramPreview_g9c9v27vpyspw!App");
        APP_ALIASES.put("instagram", "shell:AppsFolder\\Facebook.InstagramBeta_8xx8rvfyw5nnt!App");

        // Developer Tools & AI
        APP_ALIASES.put("chatgpt", "shell:AppsFolder\\OpenAI.Codex_2p2nqsd0c76g0!App");
        APP_ALIASES.put("perplexity", "shell:AppsFolder\\com.todesktop.25020447d4kq915");
        APP_ALIASES.put("vs code", "code");
        APP_ALIASES.put("vscode", "code");
        APP_ALIASES.put("code", "code");
        APP_ALIASES.put("visual studio code", "code");
        APP_ALIASES.put("windsurf", "shell:AppsFolder\\Chrome._crx_afnjdehacipkkciagigebpiola");
        APP_ALIASES.put("terminal", "wt");
        APP_ALIASES.put("windows terminal", "wt");
        APP_ALIASES.put("cmd", "cmd");
        APP_ALIASES.put("powershell", "powershell");
        APP_ALIASES.put("xampp", "C:\\xampp\\xampp-control.exe");
        APP_ALIASES.put("zotero", "shell:AppsFolder\\Zotero.Zotero.7.0.27");

        // Utilities & Tools
        APP_ALIASES.put("snipping tool", "snippingtool");
        APP_ALIASES.put("snippingtool", "snippingtool");
        APP_ALIASES.put("snip", "snippingtool");
        APP_ALIASES.put("settings", "ms-settings:");
        APP_ALIASES.put("task manager", "taskmgr");
        APP_ALIASES.put("taskmgr", "taskmgr");
        APP_ALIASES.put("anydesk", "shell:AppsFolder\\prokzult ad");
        APP_ALIASES.put("winrar", "C:\\Program Files\\WinRAR\\WinRAR.exe");

        // Media & Creativity
        APP_ALIASES.put("vlc", "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe");
        APP_ALIASES.put("vlc media player", "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe");
        APP_ALIASES.put("photos", "shell:AppsFolder\\Microsoft.Windows.Photos_8wekyb3d8bbwe!App");
        APP_ALIASES.put("camera", "shell:AppsFolder\\Microsoft.WindowsCamera_8wekyb3d8bbwe!App");
        APP_ALIASES.put("paint", "mspaint");
        APP_ALIASES.put("mspaint", "mspaint");
        APP_ALIASES.put("canva", "shell:AppsFolder\\com.canva.CanvaDesktop");
        APP_ALIASES.put("filmora", "D:\\VE\\Wondershare\\Wondershare Filmora\\Wondershare Filmora Launcher.exe");
        APP_ALIASES.put("filmora 15", "D:\\VE\\Wondershare\\Wondershare Filmora\\Wondershare Filmora Launcher.exe");
        APP_ALIASES.put("wondershare filmora", "D:\\VE\\Wondershare\\Wondershare Filmora\\Wondershare Filmora Launcher.exe");
        APP_ALIASES.put("spotify", "shell:AppsFolder\\SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify");
        APP_ALIASES.put("media player", "shell:AppsFolder\\Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic");
        APP_ALIASES.put("sticky notes", "shell:AppsFolder\\Microsoft.MicrosoftStickyNotes_8wekyb3d8bbwe!App");
        APP_ALIASES.put("stickynotes", "shell:AppsFolder\\Microsoft.MicrosoftStickyNotes_8wekyb3d8bbwe!App");

        // Games & Other
        APP_ALIASES.put("angry birds", "shell:AppsFolder\\1ED5AEA5.4160926B82DB_p2gbknwb5d8r2!App");
        APP_ALIASES.put("angry birds 2", "shell:AppsFolder\\1ED5AEA5.4160926B82DB_p2gbknwb5d8r2!App");
        APP_ALIASES.put("beach buggy", "shell:AppsFolder\\VectorUnit.BeachBuggyRacing_hvbhrzr8672s2!App");
        APP_ALIASES.put("beach buggy racing", "shell:AppsFolder\\VectorUnit.BeachBuggyRacing_hvbhrzr8672s2!App");
        APP_ALIASES.put("solitaire", "shell:AppsFolder\\Microsoft.MicrosoftSolitaireCollection_8wekyb3d8bbwe!App");
        APP_ALIASES.put("xbox", "shell:AppsFolder\\Microsoft.GamingApp_8wekyb3d8bbwe!Microsoft.Xbox.App");

        // Accessibility
        APP_ALIASES.put("narrator", "narrator");
        APP_ALIASES.put("magnifier", "magnify");
        APP_ALIASES.put("osk", "osk");
        APP_ALIASES.put("keyboard", "osk");
        APP_ALIASES.put("on-screen keyboard", "osk");
    }

    /** Launches a desktop application by name, alias, or dynamic Start Menu search. */
    public static String launchApp(String appName) {
        if (appName == null || appName.isBlank()) {
            return "Application name cannot be empty.";
        }
        String cleanName = appName.trim().toLowerCase();

        // 1. Direct Alias Match
        String command = APP_ALIASES.get(cleanName);
        if (command != null) {
            try {
                if (command.startsWith("shell:")) {
                    new ProcessBuilder("explorer.exe", command).start();
                } else if (command.contains(":\\")) {
                    new ProcessBuilder(command).start();
                } else {
                    new ProcessBuilder("cmd.exe", "/c", "start", "", command).start();
                }
                return "Launching " + appName + ", Sir.";
            } catch (IOException e) {
                // fall through to dynamic PowerShell search
            }
        }

        // 2. Dynamic Start Menu discovery via PowerShell Get-StartApps
        try {
            String psScript = "$apps = Get-StartApps;\n"
                    + "$match = $apps | Where-Object { $_.Name -like '*" + cleanName.replace("'", "''") + "*' } | Select-Object -First 1;\n"
                    + "if ($match) {\n"
                    + "    Start-Process 'explorer.exe' ('shell:AppsFolder\\' + $match.AppID);\n"
                    + "    Write-Output 'FOUND';\n"
                    + "} else {\n"
                    + "    Start-Process '" + cleanName.replace("'", "''") + "' -ErrorAction SilentlyContinue;\n"
                    + "    Write-Output 'ATTEMPTED';\n"
                    + "};\n";

            String encoded = Base64.getEncoder().encodeToString(psScript.getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded).start();
            p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return "Launching " + appName + ", Sir.";
        } catch (Exception e) {
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

    /**
     * Directly turns Wi-Fi or Bluetooth ON or OFF without opening any Settings window.
     * Uses Windows Runtime Radio Management API.
     */
    public static String toggleRadio(String radioKind, String targetState) {
        if (radioKind == null) return "Invalid device.";
        String cleanKind = radioKind.trim().toLowerCase();
        String expectedKind = cleanKind.contains("blue") ? "Bluetooth" : "WiFi";
        boolean turnOn = targetState != null && (targetState.trim().equalsIgnoreCase("on") || targetState.trim().equalsIgnoreCase("enable"));
        String stateEnum = turnOn ? "On" : "Off";

        String script = "Add-Type -AssemblyName System.Runtime.WindowsRuntime;\n"
                + "$as = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1;\n"
                + "function Await($t, $r) {\n"
                + "    $m = $as.MakeGenericMethod($r);\n"
                + "    $net = $m.Invoke($null, @($t));\n"
                + "    $net.Wait(-1) | Out-Null;\n"
                + "    return $net.Result;\n"
                + "};\n"
                + "[Windows.Devices.Radios.Radio,Windows.System.Devices,ContentType=WindowsRuntime] | Out-Null;\n"
                + "$radios = Await ([Windows.Devices.Radios.Radio]::GetRadiosAsync()) ([System.Collections.Generic.IReadOnlyList[Windows.Devices.Radios.Radio]]);\n"
                + "$r = $radios | Where-Object { $_.Kind -eq '" + expectedKind + "' };\n"
                + "if ($r) {\n"
                + "    Await ($r.SetStateAsync([Windows.Devices.Radios.RadioState]::" + stateEnum + ")) ([Windows.Devices.Radios.RadioAccessStatus]) | Out-Null;\n"
                + "    Write-Output 'SUCCESS';\n"
                + "} else {\n"
                + "    Write-Output 'NOT_FOUND';\n"
                + "};\n";

        try {
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded
            ).redirectErrorStream(true).start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (output.toString().contains("SUCCESS")) {
                return (expectedKind.equals("WiFi") ? "Wi-Fi" : "Bluetooth") + " has been turned " + (turnOn ? "ON" : "OFF") + ", Sir.";
            } else {
                return "Could not change " + (expectedKind.equals("WiFi") ? "Wi-Fi" : "Bluetooth") + " state.";
            }
        } catch (Exception e) {
            return "Failed to toggle " + radioKind + ": " + e.getMessage();
        }
    }

    /** Directly closes a running application by name or alias. */
    public static String closeApp(String appName) {
        if (appName == null || appName.isBlank()) return "Please specify an application to close.";
        String clean = appName.trim().toLowerCase();
        String procName;
        switch (clean) {
            case "notepad": procName = "notepad"; break;
            case "calc":
            case "calculator": procName = "CalculatorApp*,calc*"; break;
            case "chrome": procName = "chrome"; break;
            case "edge": procName = "msedge"; break;
            case "spotify": procName = "spotify"; break;
            case "paint":
            case "mspaint": procName = "mspaint"; break;
            case "code":
            case "vscode":
            case "vs code": procName = "code"; break;
            case "cmd": procName = "cmd"; break;
            case "powershell": procName = "powershell"; break;
            case "taskmgr":
            case "task manager": procName = "taskmgr"; break;
            case "whatsapp": procName = "WhatsApp*"; break;
            case "telegram": procName = "Telegram*"; break;
            case "instagram": procName = "Instagram*"; break;
            case "chatgpt": procName = "ChatGPT*"; break;
            case "vlc":
            case "vlc media player": procName = "vlc"; break;
            case "anydesk": procName = "AnyDesk"; break;
            case "canva": procName = "Canva*"; break;
            case "snipping tool":
            case "snippingtool": procName = "SnippingTool*"; break;
            case "word": procName = "WINWORD"; break;
            case "excel": procName = "EXCEL"; break;
            case "powerpoint":
            case "ppt": procName = "POWERPNT"; break;
            case "onenote": procName = "ONENOTE"; break;
            case "outlook": procName = "*Outlook*"; break;
            case "winrar": procName = "WinRAR"; break;
            case "perplexity": procName = "*Perplexity*"; break;
            case "xampp": procName = "xampp*"; break;
            case "zotero": procName = "zotero"; break;
            case "photos": procName = "*Photos*"; break;
            case "camera": procName = "*Camera*"; break;
            case "sticky notes":
            case "stickynotes": procName = "*StickyNotes*"; break;
            case "filmora":
            case "filmora 15":
            case "wondershare filmora": procName = "*Filmora*"; break;
            case "angry birds":
            case "angry birds 2": procName = "*AngryBirds*"; break;
            case "beach buggy":
            case "beach buggy racing": procName = "*BeachBuggy*"; break;
            case "solitaire": procName = "*Solitaire*"; break;
            default: procName = clean; break;
        }

        try {
            Process p = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                    "Stop-Process -Name " + procName + " -Force -ErrorAction SilentlyContinue"
            ).redirectErrorStream(true).start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return "Closed " + appName + ", Sir.";
        } catch (Exception e) {
            return "Failed to close " + appName + ": " + e.getMessage();
        }
    }

    /** Directly toggles Windows Dark Mode ON or OFF. */
    public static String toggleDarkMode(String state) {
        boolean on = state == null || state.equalsIgnoreCase("on") || state.equalsIgnoreCase("enable") || state.equalsIgnoreCase("dark");
        int val = on ? 0 : 1; // 0 = Dark mode, 1 = Light mode
        try {
            String psCmd = "Set-ItemProperty -Path HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize -Name AppsUseLightTheme -Value " + val + "; "
                    + "Set-ItemProperty -Path HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize -Name SystemUsesLightTheme -Value " + val;
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psCmd).start();
            return "Dark mode has been turned " + (on ? "ON" : "OFF") + ", Sir.";
        } catch (IOException e) {
            return "Failed to toggle Dark Mode: " + e.getMessage();
        }
    }

    /** Directly turns off the monitor display (sleep screen). */
    public static String turnOffScreen() {
        try {
            String psCmd = "(Add-Type '[DllImport(\"user32.dll\")]public static extern int SendMessage(int hWnd, int hMsg, int wParam, int lParam);' -Name a -Passthru)::SendMessage(-1,0x0112,0xF170,2)";
            new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", psCmd).start();
            return "Display turned off, Sir.";
        } catch (IOException e) {
            return "Failed to turn off screen: " + e.getMessage();
        }
    }
}
