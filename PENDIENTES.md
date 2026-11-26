# Pendientes antes de la entrega (09-12-2026)

Cambios que se hacen al final, cuando todo esté probado. Al terminar cada uno, marcarlo con `[x]`.

## 1. Dirección del servidor

El servidor nuevo quedó en **https://conatacoch-api-i25d.onrender.com** (el nombre `conatacoch-api` ya estaba usado en Render).
Mientras tanto, para probar la app: en `local.properties` poner `API_URL=https://conatacoch-api-i25d.onrender.com`.

Cambiar `conatacoch-api.onrender.com` por `conatacoch-api-i25d.onrender.com` en:

- [ ] `app/build.gradle.kts` (dirección por defecto de `API_URL`)
- [ ] `app/src/main/java/cl/conatacoch/app/ui/navigation/AppNavHost.kt` (comentario del panel web)
- [ ] `README.md`
- [ ] `CLAUDE.md`
- [ ] `INSTALACION.md` (secciones 3 y 4)
- [ ] `PLAN_DE_PRUEBAS.md` (pasos de preparación y PSE-07)
- [ ] Informe Final (anexos de instalación y manual del panel)

## 2. Cuentas y servicios

- [ ] Configurar cron-job.org con `https://conatacoch-api-i25d.onrender.com/api/salud` cada 10 minutos (INSTALACION 2.5).
- [ ] Primer ingreso al panel web y alta del TOTP con la app autenticadora.
- [ ] Opcional: en Render, en `DATABASE_URL`, cambiar `sslmode=require` por `sslmode=verify-full` para quitar el aviso
      «SECURITY WARNING» de los logs. Hoy no afecta: la conexión ya va cifrada.
- [ ] Después de la entrega: cambiar la contraseña del usuario de MongoDB Atlas (se compartió en un chat).

## 3. Pruebas en el PC y el teléfono

- [ ] Lista de pruebas manuales de `PLAN_DE_PRUEBAS.md` (sección «Pruebas manuales en tu computador y teléfono»).
- [ ] Encuesta SUS, escaneo ZAP y capturas de la app para el informe.
