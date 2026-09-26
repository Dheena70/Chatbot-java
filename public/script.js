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

// --- Attachment Elements & State ---
const attachBtn = document.getElementById('attachBtn');
const attachMenu = document.getElementById('attachMenu');
const fileInput = document.getElementById('fileInput');
const attachmentPreviewBar = document.getElementById('attachmentPreviewBar');
const attachmentChipsList = document.getElementById('attachmentChipsList');
const lightboxModal = document.getElementById('lightboxModal');
const lightboxImg = document.getElementById('lightboxImg');
const lightboxCaption = document.getElementById('lightboxCaption');
const lightboxClose = document.getElementById('lightboxClose');
const lightboxBackdrop = document.getElementById('lightboxBackdrop');
let pendingAttachments = []; // Array of { id, name, size, type, isImage, isPdf, isText, dataUrl, base64, textContent }

// --- Command Execution & Queue State ---
let isExecuting = false;
const taskQueue = []; // Array of { id, text, isVoice, attachments }
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

function appendHistory(text, sender, actionExecuted, time, attachments) {
  chatHistory.push({
    text,
    sender,
    actionExecuted: actionExecuted || null,
    time: time || getTimeString(),
    attachments: (attachments && attachments.length > 0) ? attachments.map(a => ({
      name: a.name,
      size: a.size,
      type: a.type,
      isImage: !!a.isImage,
      isPdf: !!a.isPdf,
      thumb: a.isImage && a.dataUrl && a.dataUrl.length < 60000 ? a.dataUrl : null
    })) : null
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
    let attBadge = '';
    if (item.attachments && item.attachments.length > 0) {
      const hasImg = item.attachments.some(a => a.isImage);
      attBadge = ` <span style="opacity:0.75; font-size:11px; margin-left:4px;">${hasImg ? '🖼️' : '📎'} (${item.attachments.length})</span>`;
    }
    div.innerHTML = `
      <div class="queue-item-left">
        <span class="queue-item-idx">#${index + 1}</span>
        <span class="queue-item-text" title="${escapeHtml(item.text)}">${escapeHtml(item.text)}${attBadge}</span>
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
      if (item.attachments && item.attachments.length > 0) {
        pendingAttachments = [...item.attachments];
        renderAttachmentPreviews();
      }
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

// --- Attachment Helper Functions ---
function getFileIcon(name) {
  if (!name) return '📁';
  const ext = name.toLowerCase().split('.').pop();
  switch (ext) {
    case 'pdf': return '📕';
    case 'doc': case 'docx': return '📘';
    case 'xls': case 'xlsx': case 'csv': return '📊';
    case 'ppt': case 'pptx': return '📙';
    case 'zip': case 'rar': case '7z': case 'tar': case 'gz': return '🗜️';
    case 'java': case 'py': case 'js': case 'ts': case 'html': case 'css':
    case 'json': case 'cpp': case 'c': case 'sql': case 'sh': case 'bat':
    case 'xml': case 'yaml': case 'yml': return '💻';
    case 'txt': case 'md': case 'log': return '📄';
    case 'png': case 'jpg': case 'jpeg': case 'webp': case 'gif': case 'svg': case 'bmp': return '🖼️';
    default: return '📁';
  }
}

function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function isTextFileName(name) {
  if (!name) return false;
  return /\.(txt|java|py|js|ts|html|css|json|md|csv|xml|sql|log|yaml|yml|bat|sh|c|cpp|h|ini|conf)$/i.test(name);
}

function readFileAsDataURL(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

function readFileAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsText(file, 'UTF-8');
  });
}

async function processFiles(fileList) {
  if (!fileList || fileList.length === 0) return;
  const files = Array.from(fileList);

  for (const file of files) {
    if (file.size > 25 * 1024 * 1024) {
      alert(`File "${file.name}" exceeds the 25MB limit.`);
      continue;
    }

    const isImage = file.type.startsWith('image/') || /\.(png|jpg|jpeg|webp|gif|bmp|svg)$/i.test(file.name);
    const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    const isText = isTextFileName(file.name) || file.type.startsWith('text/');

    const item = {
      id: 'att_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6),
      name: file.name,
      size: file.size,
      type: file.type || (isImage ? 'image/jpeg' : (isPdf ? 'application/pdf' : 'text/plain')),
      isImage,
      isPdf,
      isText,
      dataUrl: null,
      base64: null,
      textContent: null
    };

    if (isImage || isPdf) {
      const dataUrl = await readFileAsDataURL(file);
      item.dataUrl = dataUrl;
      const comma = dataUrl.indexOf(',');
      item.base64 = comma !== -1 ? dataUrl.substring(comma + 1) : dataUrl;
    } else if (isText) {
      const text = await readFileAsText(file);
      item.textContent = text;
    } else {
      const dataUrl = await readFileAsDataURL(file);
      item.dataUrl = dataUrl;
      const comma = dataUrl.indexOf(',');
      item.base64 = comma !== -1 ? dataUrl.substring(comma + 1) : dataUrl;
    }

    pendingAttachments.push(item);
  }

  renderAttachmentPreviews();
}

function renderAttachmentPreviews() {
  if (!attachmentPreviewBar || !attachmentChipsList) return;
  if (pendingAttachments.length === 0) {
    attachmentPreviewBar.style.display = 'none';
    attachmentChipsList.innerHTML = '';
    return;
  }

  attachmentPreviewBar.style.display = 'block';
  attachmentChipsList.innerHTML = '';

  pendingAttachments.forEach((att, idx) => {
    const chip = document.createElement('div');
    chip.className = 'attachment-chip';

    if (att.isImage && att.dataUrl) {
      const img = document.createElement('img');
      img.className = 'attachment-thumb';
      img.src = att.dataUrl;
      img.alt = att.name;
      chip.appendChild(img);
    } else {
      const badge = document.createElement('div');
      badge.className = 'attachment-icon-badge';
      badge.textContent = getFileIcon(att.name);
      chip.appendChild(badge);
    }

    const info = document.createElement('div');
    info.className = 'attachment-info';
    info.innerHTML = `
      <span class="attachment-name" title="${escapeHtml(att.name)}">${escapeHtml(att.name)}</span>
      <span class="attachment-size">${formatBytes(att.size)}</span>
    `;
    chip.appendChild(info);

    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'attachment-remove';
    removeBtn.title = 'Remove attachment';
    removeBtn.textContent = '✕';
    removeBtn.addEventListener('click', () => {
      pendingAttachments.splice(idx, 1);
      renderAttachmentPreviews();
    });
    chip.appendChild(removeBtn);

    attachmentChipsList.appendChild(chip);
  });
}

function clearAttachments() {
  pendingAttachments = [];
  renderAttachmentPreviews();
  if (fileInput) fileInput.value = '';
}

// --- Lightbox Modal Controls ---
function openLightbox(imgSrc, caption) {
  if (!lightboxModal || !lightboxImg) return;
  lightboxImg.src = imgSrc;
  if (lightboxCaption) lightboxCaption.textContent = caption || 'Photo Preview';
  lightboxModal.style.display = 'flex';
}

function closeLightbox() {
  if (!lightboxModal) return;
  lightboxModal.style.display = 'none';
  if (lightboxImg) lightboxImg.src = '';
}

if (lightboxClose) lightboxClose.addEventListener('click', closeLightbox);
if (lightboxBackdrop) lightboxBackdrop.addEventListener('click', closeLightbox);
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeLightbox();
});

// --- Attach Button & Menu Handlers ---
if (attachBtn && attachMenu) {
  attachBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const isShowing = attachMenu.style.display === 'flex';
    attachMenu.style.display = isShowing ? 'none' : 'flex';
    attachBtn.classList.toggle('active', !isShowing);
  });

  document.addEventListener('click', (e) => {
    if (!attachMenu.contains(e.target) && e.target !== attachBtn && !attachBtn.contains(e.target)) {
      attachMenu.style.display = 'none';
      attachBtn.classList.remove('active');
    }
  });

  document.querySelectorAll('.attach-menu-item').forEach(item => {
    item.addEventListener('click', (e) => {
      e.stopPropagation();
      const type = item.getAttribute('data-type');
      if (fileInput) {
        if (type === 'image') {
          fileInput.accept = 'image/*';
        } else if (type === 'document') {
          fileInput.accept = '.pdf,.doc,.docx,.txt,.csv,.rtf,.odt';
        } else if (type === 'code') {
          fileInput.accept = '.java,.py,.js,.ts,.html,.css,.json,.md,.sql,.sh,.bat,.xml,.yaml,.yml';
        } else {
          fileInput.accept = '*/*';
        }
        fileInput.click();
      }
      attachMenu.style.display = 'none';
      attachBtn.classList.remove('active');
    });
  });
}

if (fileInput) {
  fileInput.addEventListener('change', (e) => {
    processFiles(e.target.files);
  });
}

// --- Drag & Drop onto Chat Window ---
window.addEventListener('dragover', (e) => {
  e.preventDefault();
  document.body.classList.add('drag-active');
});
window.addEventListener('dragleave', (e) => {
  if (!e.relatedTarget) {
    document.body.classList.remove('drag-active');
  }
});
window.addEventListener('drop', (e) => {
  e.preventDefault();
  document.body.classList.remove('drag-active');
  if (e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files.length > 0) {
    processFiles(e.dataTransfer.files);
  }
});

// --- Clipboard Paste (e.g. Win+Shift+S screenshot or copied file) ---
document.addEventListener('paste', (e) => {
  if (e.clipboardData && e.clipboardData.files && e.clipboardData.files.length > 0) {
    const files = Array.from(e.clipboardData.files);
    const hasImage = files.some(f => f.type.startsWith('image/'));
    if (hasImage) {
      e.preventDefault();
    }
    processFiles(files);
  }
});

function renderMessage(text, sender, actionExecuted, time, attachments) {
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

  // Render attached images or file cards
  if (attachments && attachments.length > 0) {
    const attContainer = document.createElement('div');
    attContainer.className = 'msg-attachments';
    attachments.forEach(att => {
      if (att.isImage) {
        const img = document.createElement('img');
        img.className = 'msg-attachment-img';
        img.src = att.dataUrl || att.thumb || '';
        img.alt = escapeHtml(att.name);
        img.title = 'Click to enlarge: ' + escapeHtml(att.name);
        img.addEventListener('click', () => {
          openLightbox(att.dataUrl || att.thumb || '', att.name);
        });
        attContainer.appendChild(img);
      } else {
        const fileCard = document.createElement('div');
        fileCard.className = 'msg-attachment-file';
        fileCard.innerHTML = `
          <span class="msg-attachment-file-icon">${getFileIcon(att.name)}</span>
          <span class="msg-attachment-file-name">${escapeHtml(att.name)}</span>
          <span class="msg-attachment-file-size">${formatBytes(att.size)}</span>
        `;
        attContainer.appendChild(fileCard);
      }
    });
    content.appendChild(attContainer);
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

function enqueueTask(text, isVoice = false, attachments = []) {
  if ((!text || !text.trim()) && (!attachments || attachments.length === 0)) return;
  const clean = text ? text.trim() : (attachments[0].isImage ? "Please analyze this attached photo/image, Sir." : "Please inspect this attached file, Sir.");
  if (isExecuting) {
    taskQueue.push({
      id: 'task-' + Date.now() + '-' + Math.random().toString(36).substr(2, 4),
      text: clean,
      isVoice,
      attachments: attachments || []
    });
    renderQueueUI();
  } else {
    runTask(clean, isVoice, attachments);
  }
}

async function runTask(text, isVoice = false, attachments = []) {
  isExecuting = true;
  const timeNow = getTimeString();
  renderMessage(text, 'user', null, timeNow, attachments);
  appendHistory(text, 'user', null, timeNow, attachments);

  const typingEl = addTypingIndicator();

  try {
    const payload = {
      message: text,
      sessionId,
      attachments: (attachments || []).map(a => ({
        name: a.name,
        type: a.type,
        size: a.size,
        base64: a.base64 || '',
        textContent: a.textContent || ''
      }))
    };

    const res = await fetch(API_BASE + '/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
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
      runTask(nextTask.text, nextTask.isVoice, nextTask.attachments);
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
  if (!text && pendingAttachments.length === 0) return;

  const taskText = text || (pendingAttachments[0].isImage ? "Please analyze this attached photo/image, Sir." : "Please inspect this attached file, Sir.");
  const attachmentsToSend = [...pendingAttachments];

  messageInput.value = '';
  clearAttachments();
  messageInput.focus();

  enqueueTask(taskText, false, attachmentsToSend);
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
      renderMessage(item.text, item.sender, item.actionExecuted, item.time, item.attachments);
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
