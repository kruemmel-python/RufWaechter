# Entscheidungsengine

## Priorität

1. lokale exakte Freigabe
2. lokale exakte Blockierung
3. lokale exakte temporäre Regel
4. persönliche PhoneBlock-Freigabe
5. persönliche PhoneBlock-Sperre
6. PhoneBlock-Community-Sperre
7. längstes aktives Präfix
8. längste passende Länderregel
9. Privat-/Unbekannt-Regel
10. lokale Reputation
11. Carrier-Verifikation
12. sichere Standardaktion

Bei gleich spezifischen Präfixen gewinnt zuerst die zuletzt aktualisierte Regel, dann die restriktivere Aktion und zuletzt die kleinere ID. Das Ergebnis ist deterministisch.

## Standard-Scoring

Der Reputationsscore liefert 65 Prozent seines Wertes. Mehrere Quellen, konsistente Meldungen und Kategorien wie Betrug oder Robocall erhöhen den Score. Legitime Kategorien und bestandene Carrier-Verifikation senken ihn. Daten über 30 Tage verlieren Konfidenz. Eine einzelne schwache Quelle wird maximal zu `WARN`.

Schwellen: Warnen ab 35, Stummschalten ab 60, Blockieren ab 80 und nur ab 75 Prozent Konfidenz. Einstellungen erzwingen strikt aufsteigende Werte.

Jede Entscheidung enthält Aktion, Score, Konfidenz, strukturierte Gründe, Regel-ID und monotone Laufzeit. Unbekannt bedeutet nicht automatisch Spam.
