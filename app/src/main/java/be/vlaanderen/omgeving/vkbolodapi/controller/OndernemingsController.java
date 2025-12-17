package be.vlaanderen.omgeving.vkbolodapi.controller;

import be.vlaanderen.omgeving.vkbolodapi.service.OndernemingsService;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.StringWriter;

@RestController
@RequestMapping("/id/organisatie")
@CrossOrigin
public class OndernemingsController {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private OndernemingsService ondernemingsService;

    @GetMapping(value = "/{ondernemingsnr}",
                produces = "application/json")
    public ResponseEntity<String> getOrganisationAsJson(
            @PathVariable String ondernemingsnr ){

        String json = ondernemingsService.getJson(ondernemingsnr);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping(value = "/{ondernemingsnr}",
                produces = "application/ld+json")
    public ResponseEntity<String> getPerceelAsJsonLd(
            @PathVariable String ondernemingsnr) {

        String jsonld = ondernemingsService.getJsonLd(ondernemingsnr);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/ld+json"))
                .body(jsonld);
    }

    @GetMapping(value = "/{ondernemingsnr}",
                produces = "text/turtle")
    public ResponseEntity<String> getPerceelAsTurtle(
            @PathVariable String ondernemingsnr) {

        Model model = ondernemingsService.extractModel(ondernemingsnr);
        String turtle = rdfToLang(model, Lang.TURTLE);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/turtle"))
                .body(turtle);
    }

    @GetMapping(value = "/{ondernemingsnr}",
                produces = "application/rdf+xml")
    public ResponseEntity<String> getPerceelAsRdf(
            @PathVariable String ondernemingsnr)
            {

        Model model = ondernemingsService.extractModel(ondernemingsnr);
        String rdfxml = rdfToLang(model, Lang.RDFXML);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/rdf+xml"))
                .body(rdfxml);
    }

    private String rdfToLang(Model model, Lang lang) {
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, lang);
        return writer.toString();
    }


}