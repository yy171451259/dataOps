---
name: restart-services
description: >
  This skill should be used when the user asks to restart the frontend or backend services.
  It provides scripts to automatically kill running processes and restart the Spring Boot backend
  (mvn spring-boot:run) and Vite frontend dev server (npm run dev) without asking the user for confirmation.
---

# Restart Services Skill

## Purpose

Automatically restart the DataOps DMS backend (Spring Boot on port 8080) and/or frontend
(Vite dev server on port 3000). Do NOT ask the user for confirmation — just execute.

## When to Use

- User says "重启后端" / "重启前端" / "restart backend" / "restart frontend"
- User says "重启服务" (restart both)

## How to Use

### Restart Backend Only

1. Find the PID listening on port 8080: `netstat -ano | findstr ":8080"`
2. Kill it: `taskkill /PID <PID> /F`
3. Start backend: `cd /d c:\Users\Administrator\Documents\dataOps\backend && mvn spring-boot:run`

### Restart Frontend Only

1. Find the PID listening on port 3000: `netstat -ano | findstr ":3000"`
2. Kill it: `taskkill /PID <PID> /F`
3. Start frontend: `cd /d c:\Users\Administrator\Documents\dataOps\frontend && npm run dev`

### Restart Both

Do backend first, then frontend (in parallel if possible).

## Notes

- The workspace root is `c:\Users\Administrator\Documents\dataOps`
- Backend uses Maven wrapper or `mvn`, frontend uses `npm run dev` (Vite)
- Always kill before restarting to avoid port conflicts
- Execute directly without asking for confirmation
