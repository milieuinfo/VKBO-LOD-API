package be.vlaanderen.omgeving.vkbolodapi.service;

import be.vlaanderen.omgeving.vkbolodapi.configuration.JsonldConfiguration;
import be.vlaanderen.omgeving.vkbolodapi.configuration.ReasoningModelConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
@Service
public class OndernemingsService {
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JsonldConfiguration jsonldConfiguration;

    @Autowired
    private ReasoningModelConfiguration reasoningModelConfiguration;

    public String getJson(String ondernemingsnr) {
        try {
            return extractOriginalJson(ondernemingsnr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String getJsonLd(String ondernemingsnr) {
        Model model = extractModel(ondernemingsnr);
        return rdfToJsonLd(model);
    }

    public String getJsonLd(String originalJson, String ondernemingsnr) {
        Model model = extractModel(originalJson, ondernemingsnr);
        return rdfToJsonLd(model);
    }

    public Model extractModel(String ondernemingsnr) {
        String json = extractJsonLd(ondernemingsnr);
        return parseModelFromJsonLD(json);
    }

    public Model extractModel(String originalJson, String ondernemingsnr) {
        String jsonld = transformToJsonLd(originalJson, ondernemingsnr);
        return parseModelFromJsonLD(jsonld);
    }

    private String getValue(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && !Objects.equals(node.get(key).asText(), " ")) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private ObjectNode addUnit(ObjectNode jsonld, ObjectMapper mapper, JsonNode feature) {

        JsonNode properties = feature.get("properties");
        JsonNode geometry = feature.get("geometry");

        String kboNummer = properties.get("Ondernemingsnr").asText();
        if (kboNummer.startsWith("2")) {
            jsonld.put("@id", "organisation:" + kboNummer);
            String ondrmnr = getValue(properties, "Ondernemingsnr_maatsch_zetel");
            if (ondrmnr != null) {
                jsonld.put("is_eenheid_van", "organisation:" + ondrmnr);
            }
        }

        // --- GEO: WKT POINT ---
        ArrayNode coords = (ArrayNode) geometry.get("coordinates");
        double lon = coords.get(0).asDouble();
        double lat = coords.get(1).asDouble();

        String wkt = "POINT(" + lon + " " + lat + ")";

        // --- Organisatiegegevens vanuit VKBO ---

        jsonld.put("voorkeursnaam", getValue(properties, "Maatschappelijke_naam"));
        jsonld.put("wettelijke_naam", getValue(properties, "Maatschappelijke_naam"));
        jsonld.put("alternatieve_naam", getValue(properties, "Commerciele_naam", "Afgekorte_naam", "Zoeknaam"));
        jsonld.put("rechtsvorm", getValue(properties, "Rechtsvorm"));
        jsonld.put("rechtstoestand", getValue(properties, "Rechtstoestand"));
        jsonld.put("startdatum", getValue(properties, "Startdatum"));
        jsonld.put("inschrijvingsdatum", getValue(properties, "Datum_inschrijving"));
        String nace = getValue(properties, "NACE_hoofdact_RSZ");
        if (nace != null) {
            String nace123 = nace.length() >= 3 ? nace.substring(0, 3) : nace;
            jsonld.put("activiteit", "nace:" + nace123);
        }


        // --- Adresobject ---
        ObjectNode adres = mapper.createObjectNode();
        //adres.put("@type", "locn:Address");
        adres.put("straat", getValue(properties, "VKBO_Straat", "KBO_Straat", "AR_straat", "CRAB_straat"));
        adres.put("huisnummer", getValue(properties, "VKBO_Huisnr", "KBO_Huisnr", "AR_huisnr"));
        adres.put("gemeente", getValue(properties, "VKBO_Gemeente", "KBO_Gemeente"));
        adres.put("postcode", getValue(properties, "VKBO_Postcode", "KBO_Postcode", "AR_postcode"));
        adres.put("busnummer", getValue(properties, "VKBO_Busnr", "KBO_Busnr", "AR_busnr"));
        jsonld.set("adres", adres);

        // --- Geometry object ---
        ObjectNode geomLd = mapper.createObjectNode();
        //geomLd.put("@id", "https://data.vlaanderen.be/id/geometry/organisation/" + kboNummer);
        geomLd.put("wkt", wkt);
        jsonld.set("geometry", geomLd);

        // --- Identifier object ---
        ObjectNode ident = mapper.createObjectNode();
        //ident.put("@id", "https://data.vlaanderen.be/id/identifier/organisation/" + kboNummer);
        if (kboNummer.startsWith("2")) {
            ident.put("vestigingsnrsnr", kboNummer);
        } else {
            ident.put("ondermemingsnr", kboNummer);
        }
        ident.put("@type", "adms:Identifier");
        jsonld.set("registratie", ident);
        return jsonld;
    }

    private String transformToJsonLd(String json, String ondernemingsnummer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            int number = root
                    .path("numberReturned")
                    .asInt(0);

            // JSON-LD @context
            JsonNode context = jsonldConfiguration.getJsonLDContext();

            // --- JSON-LD root opbouwen ---
            ObjectNode jsonld = mapper.createObjectNode();
            jsonld.set("@context", context);
            if (ondernemingsnummer.startsWith("0") || ondernemingsnummer.startsWith("1")) {
                jsonld.put("@id", "organisation:" + ondernemingsnummer);
                // --- Identifier object ---
                ObjectNode ident = mapper.createObjectNode();
                ident.put("ondermemingsnr", ondernemingsnummer);
                jsonld.set("registratie", ident);
            }

            JsonNode features = root.get("features");

            if (number == 1) {
                addUnit(jsonld, mapper, features.get(0));
            }
            if (number > 1) {
                ArrayNode vestigingen = mapper.createArrayNode();
                for (JsonNode feature : features) {
                    ObjectNode vestiging = mapper.createObjectNode();
                    vestigingen.add(addUnit(vestiging, mapper, feature));

                }
                jsonld.set("heeft_geregistreerde_vestiging", vestigingen);
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonld);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform JSON to JSON-LD", e);
        }
    }

    private String extractJsonLd(String ondernemingsnr) {
        String json ;
        try {
            json = extractOriginalJson(ondernemingsnr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return transformToJsonLd(json, ondernemingsnr);
    }

    private String extractOriginalJson(String ondernemingsnr) throws JsonProcessingException {
        try {
            String urlOnderneming = String.format(
                    "https://geo.api.vlaanderen.be/VKBO/ogc/features/v1/collections/Vkbo/items?f=application/json&filter-lang=cql-text&filter=Ondernemingsnr eq '%s'",
                    ondernemingsnr);


            String urlMaatschappelijkeZetel = String.format(
                    "https://geo.api.vlaanderen.be/VKBO/ogc/features/v1/collections/Vkbo/items?f=application/json&filter-lang=cql-text&filter=Ondernemingsnr_maatsch_zetel eq '%s'",
                    ondernemingsnr);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            ObjectMapper mapper = new ObjectMapper();

            // --- 1) Zoek op Ondernemingsnummer ---
            ResponseEntity<String> ondernemingResp =
                    restTemplate.exchange(urlOnderneming, HttpMethod.GET, requestEntity, String.class);

            int numberOnderneming = mapper.readTree(ondernemingResp.getBody())
                    .path("numberReturned")
                    .asInt(0);

            if (numberOnderneming > 0) {
                return ondernemingResp.getBody();
            }

            // --- 2) Zo niet: zoek op Maatschappelijke Zetel ---
            ResponseEntity<String> maatschappelijkeZetelResp =
                    restTemplate.exchange(urlMaatschappelijkeZetel, HttpMethod.GET, requestEntity, String.class);

            int numberMaatschZetel = mapper.readTree(maatschappelijkeZetelResp.getBody())
                    .path("numberReturned")
                    .asInt(0);

            if (numberMaatschZetel > 0) {
                return maatschappelijkeZetelResp.getBody();
            }

            // --- 3) Geen resultaten gevonden ---
            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("type", "FeatureCollection");
            fallback.put("numberMatched", 0);
            fallback.put("numberReturned", 0);
            fallback.putArray("features"); // lege array

            return mapper.writeValueAsString(fallback);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Fout bij verwerken JSON", e);
        }
    }


    private String rdfToJsonLd(Model model) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.JSONLD);
        return frameJsonLd(writer.toString());
    }

    private String frameJsonLd(String jsonldString) {
        try {
            Object jsonObject = JsonUtils.fromString(jsonldString);
            // Define your frame
            Object frame = jsonldConfiguration.getJsonLDFrame();
            // Frame the JSON-LD
            JsonLdOptions options = new JsonLdOptions();
            Map<String, Object> framed = JsonLdProcessor.frame(jsonObject, frame, options);
            if (framed.containsKey("@graph")) {
                List<?> graph = (List<?>) framed.get("@graph");
                if (graph.size() == 1) {
                    // Promote the node outside of @graph
                    Map<String, Object> singleNode = (Map<String, Object>) graph.get(0);
                    singleNode.put("@context", framed.get("@context"));
                    framed = singleNode;
                }
            }
            return JsonUtils.toPrettyString(framed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Model parseModelFromJsonLD(String jsonld) {
        Model model = ModelFactory.createDefaultModel();
        try (InputStream is = new ByteArrayInputStream(jsonld.getBytes(StandardCharsets.UTF_8))) {
            RDFParser.source(is).lang(Lang.JSONLD).parse(model);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON-LD to RDF", e);
        }
        return inferTriples(model, reasoningModelConfiguration.loadTurtleFromClasspath(), reasoningModelConfiguration.getRules());
    }

    public Model inferTriples(Model dataModel, Model ontologyModel, List<Rule> rules) {
        // Combine both models
        Model combinedModel = ModelFactory.createUnion(ontologyModel, dataModel);
        // Apply a reasoner to the combined model
        // Alternative reasoners
        // Reasoner reasoner = ReasonerRegistry.getRDFSReasoner();
        // Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
        Reasoner reasoner = new GenericRuleReasoner(rules);
        reasoner.setDerivationLogging(true);  // optional

        InfModel infModel = ModelFactory.createInfModel(reasoner, combinedModel);
        return infModel.getDeductionsModel().union(dataModel);
    }
}
