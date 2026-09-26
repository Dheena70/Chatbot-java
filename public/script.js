const chatWindow = document.getElementById('chatWindow');
const chatForm = document.getElementById('chatForm');
const messageInput = document.getElementById('messageInput');
const botStatus = document.getElementById('botStatus');
const themeToggle = document.getElementById('themeToggle');
const clearBtn = document.getElementById('clearBtn');
const welcomeTime = document.getElementById('welcomeTime');
const voiceIndicator = document.getElementById('voiceIndicator');
const autoListenToggle = document.getElementById('autoListenToggle');
const startVoiceChatBtn = document.getElementById('startVoiceChatBtn');
const voiceToggle = document.getElementById('voiceToggle');
const voiceIcon = document.getElementById('voiceIcon');
const micBtn = document.getElementById('micBtn');
const queuePanel = document.getElementById('queuePanel');
const queueList = document.getElementById('queueList');
const queueCount = document.getElementById('queueCount');
const clearQueueBtn = document.getElementById('clearQueueBtn');

// --- Command Execution & Queue State ---
let isExecuting = false;
const taskQueue = []; // Array of { id, text, isVoice }
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

// --- API endpoint base ---
const API_BASE = window.location.protocol === 'file:' ? 'http://localhost:5000' : '';

// --- Health check: reflect status & auto-reconnect ---
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

// --- Message persistence (localStorage) ---
const HISTORY_KEY = 'jarvis_chat_history_v2';
let chatHistory = [];

function loadHistory() {
  try {
    const data = localStorage.getItem(HISTORY_KEY);
    return data ? JSON.parse(data) : [];
  } catch (e) {
    return [];
  }
}

function saveHistory() {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(chatHistory));
  } catch (e) {
    console.warn("Storage error:", e);
  }
}

function appendHistory(text, sender, actionExecuted, time) {
  chatHistory.push({
    text,
    sender,
    actionExecuted: actionExecuted || null,
    time: time || getTimeString()
  });
  // Cap history to 100 messages to prevent excessive storage
  if (chatHistory.length > 100) chatHistory.shift();
  saveHistory();
}

// --- Voice settings & state ---
let voiceEnabled = localStorage.getItem('jarvis_voice') !== 'false';
let continuousVoiceMode = localStorage.getItem('jarvis_continuous_voice') === 'true';

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
      setVisualizer('idle');
    }
  });
}

function updateAutoListenUI() {
  if (!autoListenToggle) return;
  if (continuousVoiceMode) {
    autoListenToggle.classList.add('active');
    autoListenToggle.title = "ChatGPT Voice Mode: ACTIVE (Listening after reply). Click to turn off.";
    if (startVoiceChatBtn) {
      startVoiceChatBtn.textContent = "🛑 Stop Voice Mode";
      startVoiceChatBtn.classList.add('chip-highlight');
    }
  } else {
    autoListenToggle.classList.remove('active');
    autoListenToggle.title = "ChatGPT Voice Mode: OFF (Click to enable continuous voice conversation)";
    if (startVoiceChatBtn) {
      startVoiceChatBtn.textContent = "🎙️ Hands-free Voice Mode";
    }
  }
}
updateAutoListenUI();

if (autoListenToggle) {
  autoListenToggle.addEventListener('click', () => {
    continuousVoiceMode = !continuousVoiceMode;
    localStorage.setItem('jarvis_continuous_voice', continuousVoiceMode);
    updateAutoListenUI();
    if (continuousVoiceMode && !isListening) {
      startListening();
    }
  });
}

if (startVoiceChatBtn) {
  startVoiceChatBtn.addEventListener('click', () => {
    continuousVoiceMode = !continuousVoiceMode;
    localStorage.setItem('jarvis_continuous_voice', continuousVoiceMode);
    updateAutoListenUI();
    if (continuousVoiceMode && !isListening) {
      startListening();
    } else if (!continuousVoiceMode && isListening) {
      stopListening();
    }
  });
}

// --- Visualizer helper ---
function setVisualizer(state) {
  if (!voiceIndicator) return;
  if (state === 'speaking') {
    voiceIndicator.className = 'voice-indicator active speaking';
  } else if (state === 'listening') {
    voiceIndicator.className = 'voice-indicator active listening';
  } else {
    voiceIndicator.className = 'voice-indicator';
  }
}

// --- Best human voice selector ---
function getBestVoice() {
  if (!('speechSynthesis' in window)) return null;
  const voices = window.speechSynthesis.getVoices();
  if (!voices || voices.length === 0) return null;

  // Prioritize Microsoft Natural, Google English Male, or UK/US clear voices
  const preferred = [
    'natural', 'ryan', 'christopher', 'george', 'guy', 'david', 'google uk english male', 'uk english male', 'en-gb', 'en-us'
  ];
  for (const p of preferred) {
    const match = voices.find(v => v.name.toLowerCase().includes(p) || (p.startsWith('en-') && v.lang.toLowerCase().startsWith(p)));
    if (match) return match;
  }
  return voices.find(v => v.lang.startsWith('en')) || voices[0];
}

// --- Jarvis Text-to-Speech (ChatGPT Voice Style) ---
function speakJarvis(text, onComplete) {
  if (!voiceEnabled || !('speechSynthesis' in window)) {
    if (onComplete) onComplete();
    return;
  }

  try {
    window.speechSynthesis.cancel();
    if (window.speechSynthesis.paused) {
      window.speechSynthesis.resume();
    }

    // Clean text: strip code blocks, markdown symbols, action tags, and URLs
    let speechText = text
      .replace(/```[\s\S]*?```/g, 'Code block executed.')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/https?:\/\/\S+/g, '')
      .replace(/[*#_~>]/g, '')
      .replace(/⚡.*?Action:.*?\n/gi, '')
      .trim();

    if (!speechText) {
      if (onComplete) onComplete();
      return;
    }

    // Limit length to avoid browser timeout on huge text
    if (speechText.length > 280) {
      const firstLine = speechText.split('\n')[0];
      speechText = firstLine.length > 30 ? firstLine : speechText.substring(0, 240);
    }

    const utterance = new SpeechSynthesisUtterance(speechText);
    utterance.rate = 1.05;
    utterance.pitch = 0.96;

    const voice = getBestVoice();
    if (voice) utterance.voice = voice;

    setVisualizer('speaking');

    utterance.onend = () => {
      setVisualizer('idle');
      if (onComplete) onComplete();
      if (continuousVoiceMode) {
        setTimeout(() => {
          if (continuousVoiceMode && !isListening) {
            startListening();
          }
        }, 400);
      }
    };

    utterance.onerror = (e) => {
      console.warn("TTS error:", e);
      setVisualizer('idle');
      if (onComplete) onComplete();
    };

    window.speechSynthesis.speak(utterance);
  } catch (e) {
    console.warn("TTS exception:", e);
    setVisualizer('idle');
    if (onComplete) onComplete();
  }
}

if ('speechSynthesis' in window) {
  window.speechSynthesis.onvoiceschanged = () => {
    window.speechSynthesis.getVoices();
  };
}

// --- Voice Input (Microphone / Speech-to-Text) ---
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
let recognition = null;
let isListening = false;

function startListening() {
  if (!recognition || isListening) return;
  try {
    recognition.start();
  } catch (err) {
    console.warn("Recognition start error:", err);
  }
}

function stopListening() {
  if (!recognition || !isListening) return;
  try {
    recognition.stop();
  } catch (err) {
    console.warn("Recognition stop error:", err);
  }
}

if (SpeechRecognition && micBtn) {
  recognition = new SpeechRecognition();
  recognition.continuous = false;
  recognition.interimResults = false;
  recognition.lang = 'en-US';

  recognition.onstart = () => {
    isListening = true;
    micBtn.classList.add('recording');
    setVisualizer('listening');
    messageInput.placeholder = 'Listening, Sir... Speak now.';
  };

  recognition.onresult = (event) => {
    const transcript = event.results[0][0].transcript;
    if (transcript && transcript.trim()) {
      messageInput.value = '';
      enqueueTask(transcript, true);
    }
  };

  recognition.onerror = (event) => {
    isListening = false;
    micBtn.classList.remove('recording');
    setVisualizer('idle');
    messageInput.placeholder = 'Speak or type a command for Jarvis, Sir...';
  };

  recognition.onend = () => {
    isListening = false;
    micBtn.classList.remove('recording');
    setVisualizer('idle');
    messageInput.placeholder = 'Speak or type a command for Jarvis, Sir...';
  };

  micBtn.addEventListener('click', () => {
    if (!isListening) {
      startListening();
    } else {
      stopListening();
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

// --- Command Queue UI Management ---
function renderQueueUI() {
  if (!queuePanel || !queueList || !queueCount) return;
  if (taskQueue.length === 0) {
    queuePanel.style.display = 'none';
    return;
  }
  queuePanel.style.display = 'block';
  queueCount.textContent = taskQueue.length;
  queueList.innerHTML = '';
  taskQueue.forEach((item, index) => {
    const div = document.createElement('div');
    div.className = 'queue-item';
    div.innerHTML = `
      <div class="queue-item-left">
        <span class="queue-item-idx">#${index + 1}</span>
        <span class="queue-item-text" title="${escapeHtml(item.text)}">${escapeHtml(item.text)}</span>
      </div>
      <div class="queue-item-actions">
        <button type="button" class="queue-action-btn btn-edit" title="Edit this queued command">✏️</button>
        <button type="button" class="queue-action-btn btn-remove" title="Remove from queue">✕</button>
      </div>
    `;
    div.querySelector('.btn-edit').addEventListener('click', () => {
      taskQueue.splice(index, 1);
      renderQueueUI();
      messageInput.value = item.text;
      messageInput.focus();
    });
    div.querySelector('.btn-remove').addEventListener('click', () => {
      taskQueue.splice(index, 1);
      renderQueueUI();
    });
    queueList.appendChild(div);
  });
}

if (clearQueueBtn) {
  clearQueueBtn.addEventListener('click', () => {
    taskQueue.length = 0;
    renderQueueUI();
  });
}

function startInlineEdit(bubble, oldText) {
  const origHtml = bubble.innerHTML;
  bubble.innerHTML = '';

  const editBox = document.createElement('div');
  editBox.className = 'bubble-edit-box';

  const textarea = document.createElement('textarea');
  textarea.className = 'bubble-edit-input';
  textarea.value = oldText;
  textarea.rows = 2;

  const actions = document.createElement('div');
  actions.className = 'bubble-edit-actions';

  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn-cancel-edit';
  cancelBtn.type = 'button';
  cancelBtn.textContent = 'Cancel';
  cancelBtn.addEventListener('click', () => {
    bubble.innerHTML = origHtml;
  });

  const saveBtn = document.createElement('button');
  saveBtn.className = 'btn-save-edit';
  saveBtn.type = 'button';
  saveBtn.textContent = 'Save & Run';
  saveBtn.addEventListener('click', () => {
    const newText = textarea.value.trim();
    if (newText) {
      bubble.textContent = newText;
      const found = chatHistory.find(h => h.sender === 'user' && h.text === oldText);
      if (found) {
        found.text = newText;
        saveHistory();
      }
      enqueueTask(newText, false);
    } else {
      bubble.innerHTML = origHtml;
    }
  });

  textarea.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      saveBtn.click();
    } else if (e.key === 'Escape') {
      cancelBtn.click();
    }
  });

  actions.appendChild(cancelBtn);
  actions.appendChild(saveBtn);
  editBox.appendChild(textarea);
  editBox.appendChild(actions);
  bubble.appendChild(editBox);
  textarea.focus();
}

function renderMessage(text, sender, actionExecuted, time) {
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
  content.appendChild(bubble);

  // Message metadata & action buttons (Edit & Copy)
  const meta = document.createElement('div');
  meta.className = 'msg-meta';

  const timeEl = document.createElement('span');
  timeEl.className = 'msg-time';
  timeEl.textContent = time || getTimeString();
  meta.appendChild(timeEl);

  if (sender === 'user') {
    const editBtn = document.createElement('button');
    editBtn.type = 'button';
    editBtn.className = 'msg-action-btn edit-msg-btn';
    editBtn.title = 'Edit and re-run this command';
    editBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg> Edit';
    editBtn.addEventListener('click', () => {
      startInlineEdit(bubble, text);
    });
    meta.appendChild(editBtn);

    const copyBtn = document.createElement('button');
    copyBtn.type = 'button';
    copyBtn.className = 'msg-action-btn copy-msg-btn';
    copyBtn.title = 'Copy command text';
    copyBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy';
    copyBtn.addEventListener('click', () => {
      navigator.clipboard.writeText(text);
      copyBtn.innerHTML = '✓ Copied';
      setTimeout(() => {
        copyBtn.innerHTML = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Copy';
      }, 1500);
    });
    meta.appendChild(copyBtn);
  }

  content.appendChild(meta);
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

function enqueueTask(text, isVoice = false) {
  if (!text || !text.trim()) return;
  const clean = text.trim();
  if (isExecuting) {
    taskQueue.push({
      id: 'task-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4),
      text: clean,
      isVoice
    });
    renderQueueUI();
  } else {
    runTask(clean, isVoice);
  }
}

async function runTask(text, isVoice = false) {
  isExecuting = true;
  const timeNow = getTimeString();
  renderMessage(text, 'user', null, timeNow);
  appendHistory(text, 'user', null, timeNow);

  const typingEl = addTypingIndicator();

  try {
    const res = await fetch(API_BASE + '/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, sessionId })
    });
    const data = await res.json();
    typingEl.remove();

    const botTime = getTimeString();
    if (res.ok) {
      renderMessage(data.reply, 'bot', data.actionExecuted, botTime);
      appendHistory(data.reply, 'bot', data.actionExecuted, botTime);
      if (isVoice) {
        await new Promise(resolve => speakJarvis(data.reply, resolve));
      }
    } else {
      const err = data.error || "Something went wrong.";
      renderMessage(err, 'bot', null, botTime);
      appendHistory(err, 'bot', null, botTime);
      if (isVoice) {
        await new Promise(resolve => speakJarvis(err, resolve));
      }
    }
  } catch (err) {
    typingEl.remove();
    const errMsg = "⚠️ Couldn't reach the server. Please check if DevBot backend is active.";
    const botTime = getTimeString();
    renderMessage(errMsg, 'bot', null, botTime);
    appendHistory(errMsg, 'bot', null, botTime);
    if (isVoice) {
      await new Promise(resolve => speakJarvis(errMsg, resolve));
    }
  } finally {
    isExecuting = false;
    if (taskQueue.length > 0) {
      const nextTask = taskQueue.shift();
      renderQueueUI();
      runTask(nextTask.text, nextTask.isVoice);
    } else {
      renderQueueUI();
    }
  }
}

// Keep sendMessage as alias to enqueueTask
function sendMessage(text, isVoice = false) {
  enqueueTask(text, isVoice);
}

chatForm.addEventListener('submit', (e) => {
  e.preventDefault();
  const text = messageInput.value.trim();
  if (text) {
    messageInput.value = '';
    messageInput.focus();
    enqueueTask(text, false);
  }
});

// --- Quick suggestion chips ---
document.querySelectorAll('.chip').forEach(chip => {
  if (chip.id === 'startVoiceChatBtn') return;
  chip.addEventListener('click', () => {
    const msg = chip.getAttribute('data-msg');
    if (msg) {
      enqueueTask(msg, false);
    }
  });
});

// --- Initialize / Restore chat from storage on load ---
function initChatHistory() {
  chatHistory = loadHistory();
  if (chatHistory.length > 0) {
    chatWindow.innerHTML = '';
    for (const item of chatHistory) {
      renderMessage(item.text, item.sender, item.actionExecuted, item.time);
    }
  } else {
    // If no previous history, show the welcoming message
    chatWindow.innerHTML = '';
    const welcome = "At your service, Sir. I am JARVIS, your personal AI assistant. I have full control over your Windows laptop. Speak using the microphone button or type any command to get started!";
    const timeNow = getTimeString();
    renderMessage(welcome, 'bot', null, timeNow);
    appendHistory(welcome, 'bot', null, timeNow);
  }
}
initChatHistory();

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
    localStorage.removeItem(HISTORY_KEY);
    chatHistory = [];
    sessionId = getSessionId();

    chatWindow.innerHTML = '';
    const resetMsg = "At your service, Sir. I am JARVIS, your personal AI assistant. I have full control over your Windows laptop. Speak using the microphone button or type any command to get started!";
    const timeNow = getTimeString();
    renderMessage(resetMsg, 'bot', null, timeNow);
    appendHistory(resetMsg, 'bot', null, timeNow);
  });
}
