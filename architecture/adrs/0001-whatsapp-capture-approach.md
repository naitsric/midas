# 0001 — Adoptar extensión Chrome sobre WhatsApp Web como mecanismo de captura

## Estado
Propuesta

## Contexto
MIDAS necesita capturar conversaciones de WhatsApp de asesores financieros para detectar intención financiera y generar solicitudes pre-llenadas. Se evaluaron cuatro approaches técnicos con diferentes trade-offs de viabilidad, riesgo legal y fricción para el usuario.

## Decisión
Adoptar el **modelo Cooby**: extensión Chrome sobre WhatsApp Web que actúa como capa de UI, sincroniza conversaciones con el backend de IA, sin modificar el código de WhatsApp.

### Approaches evaluados

| Approach | Viabilidad | Riesgo |
|----------|-----------|--------|
| **Extensión Chrome (WhatsApp Web)** | **ALTA** | Consentimiento explícito del asesor. Modelo replicable de Cooby (1,000+ equipos) |
| WhatsApp Business API | MEDIA | Exige migrar a número Business — cambio de comportamiento significativo que contradice el positioning |
| Accessibility APIs Android | BAJA | Frágil, arriesga bans de cuenta |
| WhatsApp modificado (GB WA) | NULA | Ban permanente — WhatsApp lo detecta activamente |

### Por qué extensión Chrome

1. **Legalmente defensible**: el asesor instala voluntariamente (consent explícito)
2. **Mínimo cambio de comportamiento**: solo instalar extensión + usar WhatsApp Web
3. **Precedente validado**: Cooby opera con equipos financieros, setup 10 minutos
4. **Mantiene número personal**: no requiere migrar a WhatsApp Business
5. **No modifica código de WhatsApp**: menor riesgo de bans

## Consecuencias

### Positivas
- Approach más seguro legalmente y con menor fricción
- Modelo técnico probado por Cooby en producción
- Compatible con mecanismo de consent obligatorio

### Negativas
- **Requiere desktop/laptop**: los asesores que trabajan exclusivamente desde celular no pueden usar la extensión Chrome. Se evalúa ruta paralela con app móvil (Android Notification Listener).
- **Dependencia de WhatsApp Web**: si WhatsApp cambia la interfaz web, la extensión puede romperse
- **"Advanced Chat Privacy"** de WhatsApp (2025): si clientes activan esta feature, bloquea el modelo de captura

---
Última actualización: 2026-03-24
