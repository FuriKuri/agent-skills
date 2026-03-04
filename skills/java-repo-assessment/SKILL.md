---
name: java-repo-assessment
description: "Comprehensive assessment and quality analysis of Java repositories. Combines established static analysis tools (PMD, SpotBugs, Checkstyle, JaCoCo, JDepend) with Git history forensics (hotspots, knowledge maps, bus factor, temporal coupling) to produce a detailed health report. All tools are invoked via Maven fully-qualified plugin coordinates — the project's pom.xml is NEVER modified. Use this skill whenever the user wants to analyze, evaluate, assess, audit, or review a Java project, codebase, or repository — including requests for code quality reports, architecture reviews, tech debt assessments, refactoring prioritization, team knowledge analysis, or maintainability evaluations. Also trigger when the user mentions code health, hotspot analysis, bus factor, code ownership, or dependency analysis in a Java context."
---

# Java Repository Assessment Skill

Comprehensive quality and health report for Java projects. Combines **established tooling** for hard metrics with **Git history forensics** for behavioral insights — similar to CodeScene, but with open-source tools.

## Core Principle: Keine POM-Änderungen

**Die pom.xml des Projekts wird NIEMALS verändert.** Alle Maven-Plugins werden über vollqualifizierte GAV-Koordinaten aufgerufen:

```bash
mvn groupId:artifactId:version:goal -Dproperty=value
```

Maven löst das Plugin automatisch aus Maven Central auf — ohne dass es in der pom.xml deklariert sein muss. Damit kann die Analyse auf jedes beliebige Maven-Projekt angewendet werden.

## Tool-Referenz: Vollqualifizierte Maven-Kommandos

### PMD (Source Code Analyse + Copy-Paste Detection)
```bash
# PMD mit bestpractices, design, errorprone, performance Rulesets
mvn org.apache.maven.plugins:maven-pmd-plugin:3.28.0:pmd \
  -Dpmd.rulesets=category/java/bestpractices.xml,category/java/design.xml,category/java/errorprone.xml,category/java/performance.xml

# Copy-Paste Detection (CPD)
mvn org.apache.maven.plugins:maven-pmd-plugin:3.28.0:cpd -Dpmd.minimumTokens=100
```
Output: `target/pmd.xml`, `target/cpd.xml`

### SpotBugs (Bytecode-Analyse, Bug Patterns)
```bash
# Voraussetzung: Code muss kompiliert sein!
mvn compile -q

# SpotBugs mit maximalem Effort
mvn com.github.spotbugs:spotbugs-maven-plugin:4.9.8.2:spotbugs \
  -Dspotbugs.effort=Max -Dspotbugs.threshold=Low
```
Output: `target/spotbugsXml.xml`

### Checkstyle (Coding Standards)
```bash
mvn org.apache.maven.plugins:maven-checkstyle-plugin:3.6.0:checkstyle \
  -Dcheckstyle.configLocation=google_checks.xml
```
Output: `target/checkstyle-result.xml`

### Dependency Updates & Tree
```bash
mvn org.codehaus.mojo:versions-maven-plugin:2.18.0:display-dependency-updates
mvn org.codehaus.mojo:versions-maven-plugin:2.18.0:display-plugin-updates
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree
```

### OWASP Dependency-Check (CVE-Scan)
```bash
mvn org.owasp:dependency-check-maven:11.1.1:check
```
Output: `target/dependency-check-report.html`, `target/dependency-check-report.xml`
Hinweis: Erster Lauf lädt NVD-Datenbank (~300MB).

### JaCoCo (Test Coverage) — Sonderfall

JaCoCo braucht einen Agent zur Instrumentierung. Drei Strategien:

**1. Projekt hat JaCoCo bereits konfiguriert (prüfe pom.xml):**
```bash
mvn test -q
mvn org.jacoco:jacoco-maven-plugin:0.8.12:report
```

**2. Agent manuell anhängen (ohne POM-Änderung):**
```bash
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
  -Dartifact=org.jacoco:org.jacoco.agent:0.8.12:jar:runtime \
  -DoutputDirectory=/tmp/jacoco

mvn test -DargLine="-javaagent:/tmp/jacoco/org.jacoco.agent-0.8.12-runtime.jar=destfile=target/jacoco.exec"

mvn org.jacoco:jacoco-maven-plugin:0.8.12:report -Djacoco.dataFile=target/jacoco.exec
```

**3. Kein Coverage verfügbar:** Überspringen und im Report vermerken.

### Lines of Code (ohne Maven)
```bash
# Fallback wenn cloc/scc nicht installiert
find src -name '*.java' | xargs wc -l | sort -nr | head -50
find src -name '*.java' | wc -l
find src/test -name '*.java' | xargs wc -l 2>/dev/null | tail -1
```

## Analysis Workflow

### Phase 1: Project Discovery
1. Build-System: Maven (`pom.xml`) oder Gradle (`build.gradle`)?
2. Java-Version (aus `maven.compiler.source`/`target` oder `java.version`)
3. Modulstruktur (Multi-Module? Welche Module?)
4. Framework(s): Spring Boot, Quarkus, Jakarta EE, etc.
5. Bereits konfigurierte Plugins? (PMD, SpotBugs, Checkstyle, JaCoCo in pom.xml)
6. Bei Gradle: GAV-Kommandos funktionieren NICHT → siehe Gradle-Fallback am Ende

### Phase 2: Compile & Tool Execution
```bash
mvn compile -q
```
Dann alle Tools aus der Tool-Referenz oben nacheinander ausführen. Bei Multi-Module-Projekten liegen Reports im jeweiligen `target/`-Verzeichnis.

### Phase 3: Ergebnisse der Statischen Analyse
Parse die XML-Reports. Nicht als rohe Liste zusammenfassen, sondern:
- Gruppiere nach Kategorie und Schweregrad
- Fokus auf Top-20 kritischste Findings
- Jedes Finding mit Datei und Zeilennummer

### Phase 4: Architecture Metrics

Hier kein Maven-Plugin — **Import-Analyse direkt aus Source Code**.

**Package Dependency Metrics** pro signifikantem Package:

| Metric | Formula | Meaning |
|--------|---------|---------|
| **Ca** (Afferent Coupling) | Incoming deps | Verantwortung |
| **Ce** (Efferent Coupling) | Outgoing deps | Abhängigkeit |
| **I** (Instability) | Ce / (Ca + Ce) | 0=stabil, 1=instabil |
| **A** (Abstractness) | abstracts / total | Abstraktionsgrad |
| **D** (Distance) | \|A + I - 1\| | Ideal = 0 |

Identifiziere: **Zone of Pain** (low I, low A) und **Zone of Uselessness** (high I, high A).

**Weitere Architektur-Checks:**
- Zirkuläre Package-Abhängigkeiten (Graph aus Imports aufbauen)
- LCOM4 für Klassen > 10 Methoden
- Schichtverletzungen (Controller→Repository direkt, Domain→Infrastructure)
- Stable Dependencies Principle: instabile Packages sollten nur von stabileren abhängen

### Phase 5: Git History Forensics

#### 5.1 Hotspots (Churn × Complexity)
```bash
git log --format=format: --name-only --since=12.month \
  | grep '\.java$' | grep -v '^$' \
  | sort | uniq -c | sort -nr | head -50
```
Kombiniere mit Dateigröße oder PMD-Findings zur Churn×Complexity-Matrix.

#### 5.2 Knowledge Distribution & Bus Factor
```bash
# Pro-Datei Ownership
for f in $(git log --format=format: --name-only --since=12.month \
  | grep '\.java$' | grep -v '^$' | sort -u); do
  echo "=== $f ==="
  git log --format='%aN' --since=12.month -- "$f" | sort | uniq -c | sort -nr | head -3
done
```
**Höchste Risikostufe**: Hotspot + Bus Factor 1.

#### 5.3 Temporal Coupling
```bash
git log --format='---' --name-only --since=12.month \
  | grep '\.java$' \
  | awk 'BEGIN{RS="---"} NF>1 {for(i=1;i<=NF;i++) for(j=i+1;j<=NF;j++) print $i " <-> " $j}' \
  | sort | uniq -c | sort -nr | head -30
```
Cross-Module Coupling = starkes Architekturproblem-Signal.

#### 5.4 Developer Congestion
```bash
for f in $(git log --format=format: --name-only --since=6.month \
  | grep '\.java$' | grep -v '^$' | sort -u); do
  authors=$(git log --format='%aN' --since=6.month -- "$f" | sort -u | wc -l)
  commits=$(git log --oneline --since=6.month -- "$f" | wc -l)
  echo "$authors authors, $commits commits: $f"
done | sort -t',' -k1 -nr | head -20
```

#### 5.5 Code Age
```bash
for f in $(find src -name '*.java'); do
  echo "$(git log -1 --format='%ai' -- "$f" 2>/dev/null) $f"
done | sort
```

### Phase 6: Performance Anti-Patterns

Pattern-Erkennung im Source Code:
- N+1: DB/API-Calls in `for`/`while`-Schleifen
- `DriverManager.getConnection()` statt Connection Pool
- `Thread.sleep()` in Request-Handlern
- String `+` in Loops statt `StringBuilder`
- `findAll()` ohne Limits, fehlende Pagination
- `Pattern.compile()` in Loops statt als `static final`
- Eager Loading in JPA (`FetchType.EAGER` oder fehlende Lazy-Konfiguration)

### Phase 7: Test Quality
- Test-zu-Code Ratio (Lines Test / Lines Production)
- Testarten zählen: `@Test`, `@SpringBootTest`, `@Testcontainers`, `@DataJpaTest`
- Hotspot-Dateien ohne Coverage = höchstes Risiko
- Test-Smells: `Thread.sleep()`, leere Assertions, `@Disabled` ohne Grund

## Report-Struktur

```
# Repository Health Assessment: [Project Name]
## Executive Summary
## 1. Projekt-Überblick
## 2. Quantitative Metriken
## 3. Statische Analyse
## 4. Architektur (Stability, Kopplung, Kohäsion, Zyklen, Schichtverletzungen)
## 5. Git-Forensik (Hotspots, Bus Factor, Temporal Coupling, Congestion)
## 6. Performance Anti-Patterns
## 7. Test-Qualität & Coverage
## 8. Dependency Health
## 9. Scoring-Matrix (1-5 pro Kategorie)
## 10. Handlungsempfehlungen (Kritisch → Quick Wins → Strategisch)
```

| Score | Bedeutung |
|-------|-----------|
| 5 | Exzellent |
| 4 | Gut — kleinere Issues |
| 3 | Akzeptabel — sollte adressiert werden |
| 2 | Bedenklich — signifikante Issues |
| 1 | Kritisch — sofortiges Handeln nötig |

## Prinzipien

1. **pom.xml NICHT verändern** — alles via GAV-Koordinaten
2. **Tools first** — erst messen, dann urteilen
3. **Datei + Zeile** bei jedem Finding
4. **Dimensionen kombinieren** — SpotBugs-Bug + Hotspot + Bus Factor 1 = höchste Priorität
5. **Priorisieren** — Top-20% die 80% der Probleme verursachen
6. **Bestehende Config respektieren** — im Report erwähnen falls Projekt eigene Rulesets hat

## Gradle-Fallback

Bei Gradle-Projekten funktionieren die GAV-Kommandos nicht. Alternativen:
```bash
./gradlew compileJava
./gradlew pmdMain          # falls Plugin konfiguriert
./gradlew spotbugsMain     # falls Plugin konfiguriert
./gradlew checkstyleMain   # falls Plugin konfiguriert
./gradlew test jacocoTestReport
./gradlew dependencyUpdates  # benötigt ben-manes versions Plugin
```
Falls Plugins nicht konfiguriert: CLI-Versionen der Tools nutzen oder User darauf hinweisen.