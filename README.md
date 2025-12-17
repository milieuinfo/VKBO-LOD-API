# VKBO-LOD-API
## Ondernemingen en vestigingen API - VKBO OGC van JSON naar JSON-LD en Turtle

Deze webapplicatie stelt een eenvoudige HTTP-API beschikbaar waarmee je informatie over een onderneming (op basis van de ondenemingsnummer) kunt opvragen in verschillende formaten:

- `application/json` (originele API-response)
- `application/ld+json` (verrijkte JSON-LD)
- `text/turtle` (geparsed RDF-model in Turtle)
- `application/rdf+xml` (geparsed RDF-model in rdf/xml)
- `text/html` (verrijkte JSON-LD + originele API-response gepresenteerd in html met een kaartje)

---

## 🔧 Installatie

Zorg dat je Java 17+ en Maven geïnstalleerd hebt.

Clone dit project:

```bash
git clone https://github.com/gezever/VKBO-LOD-API.git
cd VKBO-LOD-API
```


## Build en start de applicatie:

```bash
mvn clean install
java -jar target/vkbolodapi-0.0.1-SNAPSHOT.jar
```
### of

```bash
mvn spring-boot:run
```

De applicatie draait standaard op:

### 👉 http://localhost:8080

## 🔍 Voorbeelden
### 1. Opvragen als JSON (originele Geo API-response)
```bash
   curl -H "Accept: application/json"  http://localhost:8080/id/organisatie/0401574852
```   
### 2. Opvragen als JSON-LD
```bash
   curl -H "Accept: application/ld+json"  http://localhost:8080/id/organisatie/0401574852
```      
   Geeft een verrijkte response terug met @context, WKT-geometrie en linked-data structuur.

### 3. Opvragen als Turtle
```bash
   curl -H "Accept: text/turtle"  http://localhost:8080/id/organisatie/0401574852
```
   Geeft RDF-data terug in text/turtle formaat, bruikbaar in triple stores of RDF-tooling.

### 4. Opvragen als Rdf/Xml
```bash
   curl -H "Accept: application/rdf+xml"  http://localhost:8080/id/organisatie/0401574852
```
Geeft RDF-data terug in rdf/xml formaat, bruikbaar in triple stores, RDF-tooling, of xslt-processen.

## 📦 Endpoints
| Methode |               Endpoint               | Beschrijving                   |
| :----- |:------------------------------------:|:-------------------------------|
| GET | /id/organisatie/{ondernemingsnummer} | Ondernemingsinformatie ophalen |

Voorbeeld:
/id/organisatie/0401574852

## 🛠 Interne werking
#### De applicatie gebruikt:

 - RestTemplate om data op te halen bij https://geo.api.vlaanderen.be/VKBO/ogc/features/v1/collections/Vkbo/items

 - Jackson voor JSON-parsing

 - Apache Jena om JSON-LD naar Rdf te transformeren 

 - Apache Jena om Rdf te serialiseren naar JSON-LD, Turtle of Rdf/Xml

 - Apache Jena om Rdf te verrijken door reasoning

 - Jsonld-java om de json-ld te framen


## 📝 Licentie
MIT – Vrij te gebruiken, aanpassen of verspreiden. Link graag terug naar dit project als je het gebruikt.


## 📈 Voorgesteld Model

![Model](app/src/documentation/model.png)