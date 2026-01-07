package be.vlaanderen.omgeving.vkbolodapi.controller;

import be.vlaanderen.omgeving.vkbolodapi.configuration.JsonldConfiguration ;
import be.vlaanderen.omgeving.vkbolodapi.service.OndernemingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ViewResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import java.util.List;
import java.util.Map;

/**
 * <a href="http://localhost:8080/id/organisatie/1010353978">...</a>
 */
@Controller
public class HTMLController {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JsonldConfiguration jsonldConfiguration;

    @Autowired
    List<ViewResolver> viewResolvers;

    @Autowired
    private OndernemingsService ondernemingsService;

    @GetMapping(value = "id/organisatie/{ondernemingsnr}",
                produces = "text/html")
    public String getOndernemingAsHtml(
            @PathVariable String ondernemingsnr) {
        return "redirect:/doc/organisatie/{ondernemingsnr}";
    }

    @GetMapping(value = "doc/organisatie/{ondernemingsnr}")
    public String getOndernemingDoc(
            @PathVariable String ondernemingsnr,
            Model model) {

        String json = ondernemingsService.getJson(ondernemingsnr);
        String jsonld = ondernemingsService.getJsonLd(json, ondernemingsnr);

        Map<String, Object> jsonAsMap = new HashMap<>();
        double lon = 0.0;
        double lat = 0.0;
        List<Map<String, Object>> locations = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode features = root.get("features");
            int featureCount = features.size();

            // Process all features to extract locations
            for (int i = 0; i < featureCount; i++) {
                JsonNode feature = features.get(i);
                JsonNode props = feature.get("properties");
                ArrayNode coords = (ArrayNode) feature.get("geometry").get("coordinates");

                double featureLon = coords.get(0).asDouble();
                double featureLat = coords.get(1).asDouble();

                // Store first feature coordinates for backward compatibility
                if (i == 0) {
                    lon = featureLon;
                    lat = featureLat;
                    jsonAsMap = objectMapper.convertValue(props, Map.class);
                }

                // Create location info for all features
                Map<String, Object> location = new HashMap<>();
                location.put("lon", featureLon);
                location.put("lat", featureLat);
                location.put("name", props.has("Maatschappelijke_naam") ? props.get("Maatschappelijke_naam").asText() : "Onbekend");
                
                // Build address string
                StringBuilder address = new StringBuilder();
                if (props.has("VKBO_Straat")) {
                    address.append(props.get("VKBO_Straat").asText());
                }
                if (props.has("VKBO_Huisnr")) {
                    String huisnr = props.get("VKBO_Huisnr").asText();
                    if (!huisnr.equals(" ")) {
                        address.append(" ").append(huisnr);
                    }
                }
                if (props.has("VKBO_Gemeente")) {
                    if (address.length() > 0) address.append(", ");
                    address.append(props.get("VKBO_Gemeente").asText());
                }
                
                location.put("address", address.toString());
                locations.add(location);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Kon JSON niet verwerken", e);
        }

        // WKT POINT maken (EPSG:4326) - gebruik eerste feature voor backward compatibility
        String wktPoint = "POINT(" + lon + " " + lat + ")";

        JsonNode context = jsonldConfiguration.getJsonLDContext();

        model.addAttribute("uri", context.get("organisation").asText() + ondernemingsnr);
        model.addAttribute("ondernemingsnr", ondernemingsnr);
        model.addAttribute("polygon", wktPoint);
        model.addAttribute("centerX", lon);
        model.addAttribute("centerY", lat);
        model.addAttribute("fields", jsonAsMap);
        model.addAttribute("jsonld", jsonld);
        model.addAttribute("locations", locations);

        return "fiche";
    }
}
