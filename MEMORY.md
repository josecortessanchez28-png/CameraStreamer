# MEMORY.md - Memoria del Proyecto CameraStreamer

## Arquitectura Actual (v2 - Internet Relay)

```
Huawei (Camera1 API → JPEG → WebSocket)
    ↓ WiFi hotspot (tiene internet via Samsung hotspot)
Glitch.com (Relay Server WebSocket - node.js)
    ↓ internet
PC Python (WebSocket client → OpenCV/Tkinter display)
```

## Estado: 2026-06-08
- [x] Proyecto creado desde cero
- [ ] Servidor relay Node.js para Glitch
- [ ] App Android Huawei (streaming WebSocket)
- [ ] App de escritorio Python
- [ ] Compilar APK via GitHub Actions
- [ ] Test end-to-end

## Historial de Decisiones

### 2026-06-08 - Nuevo enfoque: Internet Relay
- **Problema anterior:** Huawei y PC en subredes diferentes (192.168.43.x vs 192.168.42.x)
- **Solución:** Servidor relay en internet (Glitch.com) via WebSocket
- **Ventajas:** Sin cables, sin depender de redes locales, sin ADB
- **Protocolo:** WebSocket binario (JPEG frames)

### Configuración técnica
- **Resolución:** 480p (640x480), 10 FPS
- **JPEG quality:** 70 (balance calidad/bytes)
- **WebSocket:** binario, frames JPEG directos
- **Relay:** Glitch.com, Node.js + ws + express
- **Android:** Camera1 API, minSdk 23, ForegroundService
- **Escritorio:** Python 3, websockets + opencv + tkinter
- **Compilación:** GitHub Actions (JDK 17, Gradle)

## Archivos Clave
- `MEMORY.md` - Este archivo (memoria persistente)
- `PREFERENCES.cm` - Preferencias del usuario
- `relay_server/server.js` - Servidor relay para Glitch
- `android_app/` - Código fuente Android
- `desktop_app/desktop_streamer.py` - App de escritorio

## Red
- **Phone A (Samsung SM-J415FN):** Hotspot WiFi → internet para Huawei
- **Huawei:** Se conecta al hotspot de Samsung, tiene internet
- **PC:** USB tethering desde Samsung (pero NO se usa para esto)

## Notas para Futuro AI
- El usuario prefiere soluciones simples y directas
- Todo debe ser editable por el usuario
- No instalar apps sin aprobación del usuario
- Usar GitHub Actions para compilar APK (no Android Studio local)
- El usuario habla español - responder en español
