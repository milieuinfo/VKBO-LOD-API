# Google Java Style Guide — Referentie

## 2. Bronbestand basics

- **Bestandsnaam**: zelfde als de top-level klasse (hoofdlettergevoelig) + `.java`
- **Encoding**: UTF-8
- **Witruimte**: alleen ASCII spatie (0x20); geen tabs voor inspringing
- **Escape sequences**: gebruik `\t`, `\n`, etc. i.p.v. octaal/Unicode-escapes
- **Non-ASCII**: gebruik het eigenlijke Unicode-teken of Unicode-escape; kies wat leesbaarder is

## 3. Structuur van een bronbestand

Volgorde (elk onderdeel gescheiden door één lege regel):

1. Licentie/copyright (indien aanwezig)
2. Package-declaratie
3. Imports
4. Precies één top-level klassedeclaratie

### Imports

- Geen wildcard-imports (`import java.util.*`)
- Geen regelafbreking bij imports
- Volgorde: eerst alle statische imports (één groep), dan alle niet-statische imports (één groep); gescheiden door één lege regel
- Binnen elke groep: alfabetische ASCII-volgorde
- Geen statische import voor geneste klassen

### Klasse-inhoud

- Gebruik een logische volgorde; wees consistent
- Overloaded methoden staan altijd bij elkaar (geen andere members ertussen)

## 4. Opmaak

### Accolades

- Altijd accolades bij `if`, `else`, `for`, `do`, `while` (ook bij lege body of één statement)
- K&R-stijl: openingsaccolade aan het einde van de regel, sluitingsaccolade op een nieuwe regel
- Lege blokken: `{}` mag compact, maar **niet** in multi-block statements (`try/catch`)

### Inspringing

- **+2 spaties** per blok
- Continuatieregels: minimaal **+4 spaties**

### Overige opmaak

- Één statement per regel
- **Kolomlimiet: 100 tekens**
- Regelafbreking bij operators: breek **vóór** de operator (uitzondering: toewijzingsoperator)
- Methode-/constructornaam blijft aan de `(` geplakt
- Komma blijft aan het voorgaande token geplakt

### Witruimte

- Spatie tussen keyword en `(`: `if (`, `for (`, `catch (`
- Spatie voor `{`
- Spatie rondom binaire/ternaire operatoren
- Geen spatie bij `::` (method reference) of `.` (dot separator)
- Geen horizontale uitlijning vereist (mag, maar niet aanraden)

### Specifieke constructies

| Construct | Regel |
|---|---|
| Enum | Optioneel regelafbreking na komma; mag als array-initializer |
| Variabele | Één variabele per declaratie; declareer zo dicht mogelijk bij gebruik |
| Array | `String[] args`, niet `String args[]` |
| Switch (nieuw) | Gebruik `->`, altijd exhaustief inclusief `default` |
| Switch (oud) | Fall-through markeren met `// fall through` |
| Annotaties op klasse/methode | Elke annotatie op eigen regel |
| Annotaties op veld | Meerdere op één regel toegestaan |
| Modifiers | Volgorde: `public protected private abstract default static final sealed non-sealed transient volatile synchronized native strictfp` |
| Long literals | Gebruik `L` (hoofdletter), niet `l` |
| Tekstblokken | `"""` altijd op een nieuwe regel |
| TODO-commentaar | `// TODO: <bug-link> - <uitleg>` |

## 5. Naamgeving

| Type | Conventie | Voorbeeld |
|---|---|---|
| Package / module | `lowercase` aaneengesloten | `com.example.deepspace` |
| Klasse / interface | `UpperCamelCase` | `ImmutableList` |
| Methode | `lowerCamelCase` | `sendMessage` |
| Constante | `UPPER_SNAKE_CASE` | `MAX_COUNT` |
| Niet-constant veld | `lowerCamelCase` | `computedValues` |
| Parameter | `lowerCamelCase` | `userId` |
| Lokale variabele | `lowerCamelCase` | `index` |
| Type variabele | `E`, `T`, `T2` of `RequestT` | |

- Geen prefixes/suffixes (`m_`, `s_`, `_name`, `kName`)
- Acroniemen in CamelCase: `XmlHttpRequest`, niet `XMLHTTPRequest`

### CamelCase-algoritme

1. Zet naar plain ASCII, verwijder apostrofs
2. Splits op spaties en leestekens
3. Zet alles lowercase, dan eerste letter van elk woord naar uppercase
4. Samenvoegen tot identifier

## 6. Programmeerpraktijken

- **`@Override`**: altijd gebruiken wanneer legaal (uitzondering: parent is `@Deprecated`)
- **Caught exceptions**: nooit negeren zonder commentaar waarom
- **Statische members**: altijd kwalificeren via de klassenaam (`Foo.staticMethod()`)
- **Finalizers**: niet overschrijven (`Object.finalize` is deprecated)

## 7. Javadoc

### Opmaak

```java
/**
 * Samenvatting als noun- of verb-phrase.
 *
 * <p>Meer detail indien nodig.
 *
 * @param name beschrijving
 * @return beschrijving
 * @throws IOException als ...
 */
```

- Één lege regel (alleen `*`) tussen alinea's
- Nieuwe alinea begint met `<p>` direct voor het eerste woord
- Volgorde block tags: `@param`, `@return`, `@throws`, `@deprecated`
- Samenvatting: geen volledige zin, maar wel met hoofdletter en punt

### Wanneer Javadoc verplicht

- Elke zichtbare klasse, member of record-component
- Uitzondering: vanzelfsprekende getters (`getFoo()`) en overrides
- Gebruik Javadoc i.p.v. implementatiecommentaar voor algemene uitleg van klasse/methode
