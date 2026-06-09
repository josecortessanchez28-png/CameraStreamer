const express = require('express');
const http = require('http');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const PORT = process.env.PORT || 3000;

const senders = new Map();
const receivers = new Set();

let latestFrame = null;
let frameCount = 0;

app.use(express.raw({ type: '*/*', limit: '10mb' }));

app.post('/upload', (req, res) => {
  latestFrame = req.body;
  frameCount++;
  res.send('OK');
});

app.get('/frame.jpg', (req, res) => {
  if (!latestFrame) return res.status(404).send('No frame');
  res.type('image/jpeg').send(latestFrame);
});

app.get('/', (req, res) => {
  res.json({
    status: 'ok',
    service: 'CameraStreamer Relay',
    senders: Array.from(senders.values()).map(s => ({
      id: s.id, name: s.name,
      connected: s.ws.readyState === WebSocket.OPEN
    })),
    receivers: receivers.size,
    frames: frameCount
  });
});

wss.on('connection', (ws, req) => {
  const ip = req.headers['x-forwarded-for'] || req.socket.remoteAddress;
  console.log(`[CONNECT] ${ip}`);

  let isSender = false;
  let senderId = null;

  ws.on('message', (data, isBinary) => {
    if (isBinary) {
      latestFrame = data;
      frameCount++;
      for (const r of receivers) {
        if (r.readyState === WebSocket.OPEN) r.send(data);
      }
    } else {
      try {
        const msg = JSON.parse(data.toString());
        if (msg.type === 'register') {
          isSender = true;
          senderId = msg.camera_id || `cam_${Date.now()}`;
          senders.set(senderId, { id: senderId, name: msg.name || senderId, ws });
          console.log(`[SENDER] Registered: ${senderId}`);
          ws.send(JSON.stringify({ type: 'registered', camera_id: senderId, status: 'ok' }));
        }
      } catch (e) {}
    }
  });

  ws.on('close', () => {
    if (isSender && senderId) senders.delete(senderId);
    receivers.delete(ws);
  });

  setTimeout(() => {
    if (!isSender) { receivers.add(ws); console.log(`[RECEIVER] Added (total: ${receivers.size})`); }
  }, 100);
});

server.listen(PORT, () => {
  console.log(`CameraStreamer Relay running on port ${PORT}`);
});
