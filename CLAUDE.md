# Agent Skills Collection

Dieses Repository ist eine Sammlung von wiederverwendbaren Agent Skills für Claude Code.

## Repo-Struktur

```
skills/
  <skill-name>/
    SKILL.md          # Skill-Definition (Frontmatter + Anweisungen)
```

Jeder Skill liegt in einem eigenen Verzeichnis unter `skills/`. Die Datei `SKILL.md` enthält YAML-Frontmatter (`name`, `description`) gefolgt von den Skill-Anweisungen.

## Konventionen

- **Sprache**: Skills können auf Deutsch oder Englisch verfasst sein
- **SKILL.md Frontmatter**: Jeder Skill braucht `name` und `description` im YAML-Frontmatter
- **Keine externen Abhängigkeiten**: Skills sollen eigenständig funktionieren und nur auf Tools zugreifen, die Claude Code nativ bereitstellt
- **Verzeichnisname = Skill-Name**: Der Ordnername unter `skills/` entspricht dem Skill-Identifier

## Vorhandene Skills

- `java-repo-assessment` — Umfassende Qualitätsanalyse von Java-Repositories (statische Analyse, Architekturmetriken, Git-Forensik)
