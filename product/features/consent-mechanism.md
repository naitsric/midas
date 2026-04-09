# Consent Mechanism

> Mecanismo de consentimiento para captura de conversaciones — la decisión de producto #1 de MIDAS.

## Contexto

Las leyes de protección de datos en Colombia (Ley 1581/2012 + Ley 1266/2008) y México (LFPDPPP) requieren consentimiento previo, expreso e informado de **ambas partes** (asesor Y cliente) antes de cualquier recolección de datos personales. Los datos financieros tienen estatus especial en ambas jurisdicciones.

MIDAS no puede ser 100% invisible — necesita al menos un mecanismo de disclosure al cliente.

## Diseño propuesto

### Consent del asesor
- Se obtiene al momento de instalar la extensión Chrome y crear cuenta
- Términos claros sobre qué datos se capturan y cómo se procesan
- El asesor puede ver exactamente qué se captura y puede eliminarlo

### Consent del cliente
- Template automatizado en el primer mensaje del asesor a cada nuevo cliente:
  > "Esta conversación puede ser procesada por mi asistente de IA para agilizar tu solicitud."
- La extensión Chrome inyecta este mensaje automáticamente (configurable)
- Si el cliente no responde o se opone, la captura se desactiva para esa conversación

## Marco regulatorio

### Colombia — Ley 1581/2012 + Ley 1266/2008
- Consentimiento previo, expreso e informado antes de CUALQUIER recolección
- Datos financieros con estatus especial (Ley 1266)
- SIC puede multar hasta 2,000 SMLMV (~$500,000 USD)
- Puede suspender procesamiento por 6 meses o cerrar operaciones
- Bases de datos deben registrarse en el RNBD dentro de 2 meses de creación
- La SIC ya tomó acción contra WhatsApp en 2021

### México — LFPDPPP
- Consentimiento expreso para datos financieros (tácito no es suficiente)
- Único en LatAm: incluye penalidades criminales (prisión) para ciertas violaciones
- Derechos ARCO obligan respuesta en 20 días hábiles

## Principios de diseño

1. **Transparencia total**: el asesor ve exactamente qué se captura
2. **Control del usuario**: el asesor puede eliminar datos capturados
3. **Valor inmediato**: solicitud pre-llenada en las primeras 24 horas
4. **Opt-out granular**: por conversación, no todo-o-nada

## Acción requerida

**Validar con abogados en Colombia y México ANTES de escribir código.**

## Referencias

- [Product Overview](../overview.md)
- [Company Overview](../../company/overview.md)

---
Última actualización: 2026-03-24
