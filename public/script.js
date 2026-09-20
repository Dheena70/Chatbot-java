const chatWindow = document.getElementById('chatWindow');
const chatForm = document.getElementById('chatForm');
const messageInput = document.getElementById('messageInput');
const botStatus = document.getElementById('botStatus');
const themeToggle = document.getElementById('themeToggle');
const clearBtn = document.getElementById('clearBtn');
const welcomeTime = document.getElementById('welcomeTime');

// --- Session id (lets the server keep short conversation history per browser) ---
function getSessionId() {
  let id = localStorage.getItem('chatbot_session_id');
  if (!id) {
    id = (crypto.randomUUID ? crypto.randomUUID() : 'session-' + Date.now() + '-' + Math.random());
    localStorage.setItem('chatbot_session_id', id);
  }
  return id;
}
let sessionId = getSessionId();

// --- Time formatting helper ---
function getTimeString() {
  const d = new Date();
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}
if (welcomeTime) {
  welcomeTime.textContent = getTimeString();
}

// --- Theme (dark/light) ---
function applyTheme(theme) {
  document.body.classList.toggle('light', theme === 'light');
}
function initTheme() {
  const saved = localStorage.getItem('chatbot_theme') || 'dark';
  applyTheme(saved);
}
themeToggle.addEventListener('click', () => {
  const isLight = document.body.classList.contains('light');
  const next = isLight ? 'dark' : 'light';
  applyTheme(next);
  localStorage.setItem('chatbot_theme', next);
});
initTheme();

// --- API endpoint base (allows testing directly via file:// if backend is running on 5000) ---
const API_BASE = window.location.protocol === 'file:' ? 'http://localhost:5000' : '';

// --- Health check: reflect whether AI fallback is enabled & auto-reconnect ---
let isServerOnline = false;
async function checkHealth() {
  try {
    const res = await fetch(API_BASE + '/api/health', { cache: 'no-store' });
    if (!res.ok) throw new Error('Status ' + res.status);
    const data = await res.json();
    isServerOnline = true;
    if (data.aiEnabled) {
      botStatus.innerHTML = '<span class="dot"></span>Online — rule-based + AI fallback';
    } else {
      botStatus.innerHTML = '<span class="dot"></span>Online — rule-based demo (Java)';
    }
  } catch (err) {
    isServerOnline = false;
    botStatus.innerHTML = '<span class="dot offline"></span>Offline — server unreachable (run run.bat)';
  }
}
checkHealth();
setInterval(checkHealth, 3000);

// --- Message formatting with safe escaping ---
function escapeHtml(str) {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function formatMessage(text) {
  let escaped = escapeHtml(text);
  escaped = escaped.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
  escaped = escaped.replace(/\*(.*?)\*/g, '<em>$1</em>');
  escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>');
  escaped = escaped.replace(/\n/g, '<br>');
  return escaped;
}

function addMessage(text, sender, actionExecuted) {
  const msg = document.createElement('div');
  msg.className = 'msg ' + sender;

  const content = document.createElement('div');
  content.className = 'msg-content';

  if (sender === 'bot' && actionExecuted) {
    const badge = document.createElement('div');
    badge.className = 'action-badge';
    badge.innerHTML = '⚡ <span>Jarvis Action:</span> ' + escapeHtml(actionExecuted);
    content.appendChild(badge);
  }

  const bubble = document.createElement('div');
  bubble.className = 'bubble';
  if (sender === 'bot') {
    bubble.innerHTML = formatMessage(text);
  } else {
    bubble.textContent = text;
  }

  const time = document.createElement('span');
  time.className = 'msg-time';
  time.textContent = getTimeString();

  content.appendChild(bubble);
  content.appendChild(time);
  msg.appendChild(content);

  chatWindow.appendChild(msg);
  chatWindow.scrollTop = chatWindow.scrollHeight;
  return msg;
}

function addTypingIndicator() {
  const msg = document.createElement('div');
  msg.className = 'msg bot typing';
  msg.innerHTML = '<div class="msg-content"><div class="bubble"><span class="typing-dot"></span><span class="typing-dot"></span><span class="typing-dot"></span></div></div>';
  chatWindow.appendChild(msg);
  chatWindow.scrollTop = chatWindow.scrollHeight;
  return msg;
}

async function sendMessage(text) {
  if (!text || messageInput.disabled) return;

  addMessage(text, 'user');
  messageInput.value = '';
  messageInput.disabled = true;

  const typingEl = addTypingIndicator();

  try {
    const res = await fetch(API_BASE + '/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, sessionId })
    });
    const data = await res.json();
    typingEl.remove();

    if (res.ok) {
      addMessage(data.reply, 'bot', data.actionExecuted);
    } else {
      addMessage(data.error || "Something went wrong.", 'bot');
    }
  } catch (err) {
    typingEl.remove();
    addMessage("⚠️ Couldn't reach the server. Please run `run.bat` on your computer to start the chatbot backend, then try again.", 'bot');
  } finally {
    messageInput.disabled = false;
    messageInput.focus();
  }
}

chatForm.addEventListener('submit', (e) => {
  e.preventDefault();
  const text = messageInput.value.trim();
  if (text) {
    sendMessage(text);
  }
});

// --- Quick suggestion chips ---
document.querySelectorAll('.chip').forEach(chip => {
  chip.addEventListener('click', () => {
    const msg = chip.getAttribute('data-msg');
    if (msg) {
      sendMessage(msg);
    }
  });
});

// --- Clear Chat / Reset Session ---
if (clearBtn) {
  clearBtn.addEventListener('click', async () => {
    try {
      await fetch(API_BASE + '/api/reset', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId })
      });
    } catch (e) {
      // Best effort
    }
    localStorage.removeItem('chatbot_session_id');
    sessionId = getSessionId();

    chatWindow.innerHTML = '';
    addMessage("Greetings! I am DevBot, your personal Jarvis-style AI assistant. I can chat, write code, answer questions, and control your laptop. Try asking me to open apps, check your battery, or adjust the volume!", 'bot');
  });
}
