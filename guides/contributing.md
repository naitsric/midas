# Cómo contribuir a midas-docs

> Guía para agregar o modificar documentación en este repositorio.

## Contexto

Este repositorio es la fuente de verdad de MIDAS. Mantenerlo actualizado y consistente es responsabilidad de todo el equipo.

## Proceso

1. Crea un branch con el nombre `docs/<tema>`
2. Agrega o edita los `.md` correspondientes siguiendo los lineamientos del [README](../README.md)
3. Abre un PR con una descripción breve de qué documentación se agregó o cambió
4. Al menos un miembro del equipo debe aprobar antes de mergear

## Convenciones de commits

```
docs: add chrome-extension architecture overview
docs: update consent mechanism flow
docs: add ADR-0001 whatsapp-capture-approach
```

## Checklist antes de hacer PR

- [ ] El archivo sigue la plantilla (Título, Contexto, Contenido, Referencias, Última actualización)
- [ ] Nombres de archivo en kebab-case y en inglés
- [ ] Contenido consistente en un solo idioma por documento
- [ ] Diagramas en Mermaid (no imágenes, excepto cuando Mermaid no puede expresarlo)
- [ ] Sin secretos ni datos sensibles
- [ ] `Última actualización` con la fecha correcta
- [ ] Referencias cruzadas actualizadas

---
Última actualización: 2026-03-24
