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
      botStatus.innerHTML = '<span class="dot"></span>Online — Laptop Control Active';
    } else {
      botStatus.innerHTML = '<span class="dot"></span>Online — Rule-based Demo';
    }
  } catch (err) {
    isServerOnline = false;
    botStatus.innerHTML = '<span class="dot offline"></span>Offline — server unreachable';
  }
}
checkHealth();
setInterval(checkHealth, 3000);

// --- Jarvis Voice (Text-to-Speech) ---
const voiceToggle = document.getElementById('voiceToggle');
const voiceIcon = document.getElementById('voiceIcon');
let voiceEnabled = localStorage.getItem('jarvis_voice') !== 'false';

function updateVoiceIcon() {
  if (!voiceIcon || !voiceToggle) return;
  if (voiceEnabled) {
    voiceIcon.innerHTML = '<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"></path>';
    voiceToggle.title = "Jarvis Voice: ON (Click to mute)";
  } else {
    voiceIcon.innerHTML = '<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon><line x1="23" y1="9" x2="17" y2="15"></line><line x1="17" y1="9" x2="23" y2="15"></line>';
    voiceToggle.title = "Jarvis Voice: OFF (Click to unmute)";
  }
}
updateVoiceIcon();

if (voiceToggle) {
  voiceToggle.addEventListener('click', () => {
    voiceEnabled = !voiceEnabled;
    localStorage.setItem('jarvis_voice', voiceEnabled);
    updateVoiceIcon();
    if (!voiceEnabled && window.speechSynthesis) {
      window.speechSynthesis.cancel();
    }
  });
}

function speakJarvis(text) {
  if (!voiceEnabled || !('speechSynthesis' in window)) return;
  try {
    window.speechSynthesis.cancel();
    let speechText = text
      .replace(/```[\s\S]*?```/g, 'Code block executed.')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/https?:\/\/\S+/g, '')
      .replace(/[*#_~]/g, '')
      .replace(/⚡.*?Action:.*?\n/gi, '')
      .trim();

    if (!speechText) return;
    if (speechText.length > 250) {
      const firstLine = speechText.split('\n')[0];
      speechText = firstLine.length > 20 ? firstLine : speechText.substring(0, 200);
    }

    const utterance = new SpeechSynthesisUtterance(speechText);
    utterance.rate = 1.05;
    utterance.pitch = 0.95;

    const voices = window.speechSynthesis.getVoices();
    const chosen = voices.find(v => v.lang.startsWith('en') && (v.name.includes('David') || v.name.includes('George') || v.name.includes('Natural') || v.name.includes('Guy')))
                || voices.find(v => v.lang.startsWith('en'));
    if (chosen) utterance.voice = chosen;

    window.speechSynthesis.speak(utterance);
  } catch (e) {
    console.warn("TTS error:", e);
  }
}

// Ensure voices are loaded
if ('speechSynthesis' in window) {
  window.speechSynthesis.onvoiceschanged = () => {
    window.speechSynthesis.getVoices();
  };
}

// --- Voice Input (Microphone / Speech-to-Text) ---
const micBtn = document.getElementById('micBtn');
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

if (SpeechRecognition && micBtn) {
  const recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'en-US';

  let isListening = false;

  recognition.onstart = () => {
    isListening = true;
    micBtn.classList.add('recording');
    messageInput.placeholder = 'Listening, Sir... Speak now.';
  };

  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    if (transcript && transcript.trim()) {
      messageInput.value = transcript;
      sendMessage(transcript);
    }
  };

  recognition.onerror = (event) => {
    isListening = false;
    micBtn.classList.remove('recording');
    messageInput.placeholder = 'Speak or type a command for Jarvis, Sir...';
  };

  recognition.onend = () => {
    isListening = false;
    micBtn.classList.remove('recording');
    messageInput.placeholder = 'Speak or type a command for Jarvis, Sir...';
  };

  micBtn.addEventListener('click', () => {
    if (!isListening) {
      try {
        recognition.start();
      } catch (err) {
        recognition.stop();
      }
    } else {
      recognition.stop();
    }
  });
} else if (micBtn) {
  micBtn.title = "Microphone not supported in this browser";
  micBtn.style.opacity = '0.5';
}

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
      speakJarvis(data.reply);
    } else {
      const err = data.error || "Something went wrong.";
      addMessage(err, 'bot');
      speakJarvis(err);
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
    const resetMsg = "At your service, Sir. I am JARVIS, your personal AI assistant. I have full control over your Windows laptop. Speak using the microphone button or type any command to get started!";
    addMessage(resetMsg, 'bot');
    speakJarvis(resetMsg);
  });
}
