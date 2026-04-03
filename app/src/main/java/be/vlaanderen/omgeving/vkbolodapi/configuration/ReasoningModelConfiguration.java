package be.vlaanderen.omgeving.vkbolodapi.configuration;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.reasoner.rulesys.Rule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/** Loads RDF ontology models and reasoning rules from the classpath as Spring beans. */
@Configuration
public class ReasoningModelConfiguration {

  @Value("classpath:be/vlaanderen/data/id/organisatie/domain-range-subproperty.rules")
  private Resource rules;

  @Value("classpath:org/w3/www/ns/adms/adms.ttl")
  private Resource adms;

  @Value("classpath:org/w3/www/2004/02/skos/core/skos.ttl")
  private Resource skos;

  @Value("classpath:net/opengis/www/ont/geosparql/geosparql_vocab_all.ttl")
  private Resource geosparql;

  @Value("classpath:org/w3/www/ns/locn/locn.ttl")
  private Resource locn;

  @Value("classpath:org/w3/www/ns/org/org.ttl")
  private Resource org;

  @Value("classpath:org/w3/www/ns/regorg/regorg.ttl")
  private Resource regorg;

  @Value("classpath:eu/europa/data/ux2/nace/NACE_Rev.2.1-nederlandse-labels.ttl")
  private Resource nace;

  @Bean
  public Model loadTurtleFromClasspath() {
    Model modelAdms = ModelFactory.createDefaultModel();
    Resource adms = loadAdms();
    try {
      modelAdms.read(adms.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ADMS ontology", e);
    }

    Model modelOrg = ModelFactory.createDefaultModel();
    Resource org = loadOrg();
    try {
      modelOrg.read(org.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load ORG ontology", e);
    }

    Model modelSkos = ModelFactory.createDefaultModel();
    Resource skos = loadSkos();
    try {
      modelSkos.read(skos.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load SKOS ontology", e);
    }

    Model modelRegorg = ModelFactory.createDefaultModel();
    Resource regorg = loadRegorg();
    try {
      modelAdms.read(regorg.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load RegOrg ontology", e);
    }

    Model modelGeosparql = ModelFactory.createDefaultModel();
    Resource geosparql = loadGeosparql();
    try {
      modelGeosparql.read(geosparql.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load GeoSPARQL ontology", e);
    }

    Model modelNace = ModelFactory.createDefaultModel();
    Resource nace = loadNace();
    try {
      modelNace.read(nace.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load NACE ontology", e);
    }

    Model modelLocn = ModelFactory.createDefaultModel();
    Resource locn = loadLocn();
    try {
      modelLocn.read(locn.getInputStream(), null, "TURTLE");
    } catch (IOException e) {
      throw new RuntimeException("Failed to load LOCN ontology", e);
    }

    Model m = modelLocn.union(modelGeosparql);
    return m.union(modelAdms).union(modelOrg).union(modelRegorg).union(modelSkos).union(modelNace);
  }

  @Bean
  public List<Rule> getRules() {
    Resource ruleresource = loadRules();
    try {
      InputStream ruleStream = ruleresource.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(ruleStream));
      return Rule.parseRules(Rule.rulesParserFromReader(reader));
    } catch (IOException e) {
      throw new RuntimeException("Failed to construct rules", e);
    }
  }

  private Resource loadOrg() {
    return org;
  }

  private Resource loadSkos() {
    return skos;
  }

  private Resource loadRegorg() {
    return regorg;
  }

  private Resource loadLocn() {
    return locn;
  }

  private Resource loadGeosparql() {
    return geosparql;
  }

  private Resource loadAdms() {
    return adms;
  }

  private Resource loadNace() {
    return nace;
  }

  private Resource loadRules() {
    return rules;
  }
}
