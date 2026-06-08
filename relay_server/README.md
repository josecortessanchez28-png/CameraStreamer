# CameraStreamer Relay

Servidor WebSocket relay para CameraStreamer.

## Cómo funciona

1. **Emisor** (Android Huawei) se conecta y envía frames JPEG por WebSocket
2. **Relay** retransmite cada frame a todos los **receptores** conectados
3. **Receptor** (PC Python) recibe y muestra el video en tiempo real

## Despliegue en Glitch.com

1. Crear cuenta gratuita en [glitch.com](https://glitch.com)
2. Click "New Project" → "Import from GitHub"
3. Pegar URL de este repositorio
4. Glitch ejecuta `npm install` automáticamente
5. URL pública: `https://tu-proyecto.glitch.me`

## API

### WebSocket
- **Emisor se registra:** `{"type":"register","camera_id":"huawei_1","name":"Cámara Huawei"}`
- **Emisor envía frame:** mensaje binario (bytes JPEG)
- **Receptor recibe frame:** mensaje binario (bytes JPEG)

### HTTP
- `GET /` - Estado del servidor (JSON)
- `GET /health` - Health check

## Variables de entorno

- `PORT` - Puerto del servidor (default: 3000, Glitch asigna automáticamente)
