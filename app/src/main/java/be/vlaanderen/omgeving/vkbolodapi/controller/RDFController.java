package be.vlaanderen.omgeving.vkbolodapi.controller;

import be.vlaanderen.omgeving.vkbolodapi.configuration.JsonldConfiguration;
import be.vlaanderen.omgeving.vkbolodapi.configuration.ReasoningModelConfiguration;
import be.vlaanderen.omgeving.vkbolodapi.service.OndernemingsService;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.StringWriter;
import java.util.*;

/** Controller for RDF-based HTML views of organisation data. */
@Controller
public class RDFController {

  // --- URI constants ---
  private static final String URI_RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
  private static final String URI_FOAF_NAME = "http://xmlns.com/foaf/0.1/name";
  private static final String URI_SCHEMA_NAME = "http://schema.org/name";
  private static final String URI_ORG_LEGAL_NAME = "http://www.w3.org/ns/org#legalName";
  private static final String URI_RDF_TYPE =
      "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
  private static final String URI_LOCN_GEOMETRY = "http://www.w3.org/ns/locn#geometry";
  private static final String URI_IMJV_LAMBERT =
      "https://data.imjv.omgeving.vlaanderen.be/ns/imjv#lambertWktString";
  private static final String URI_WGS84_LAT =
      "http://www.w3.org/2003/01/geo/wgs84_pos#lat";
  private static final String URI_WGS84_LONG =
      "http://www.w3.org/2003/01/geo/wgs84_pos#long";
  private static final String URI_WGS84_AS_WKT =
      "http://www.w3.org/2003/01/geo/wgs84_pos#asWKT";
  private static final String URI_GEOSPARQL_AS_WKT =
      "http://www.opengis.net/ont/geosparql#asWKT";
  private static final String URI_WGS84_HAS_SERIALIZATION =
      "http://www.w3.org/2003/01/geo/wgs84_pos#hasSerialization";
  private static final String URI_GEOSPARQL_HAS_SERIALIZATION =
      "http://www.opengis.net/ont/geosparql#hasSerialization";
  private static final String URI_WGS84_HAS_GEOMETRY =
      "http://www.w3.org/2003/01/geo/wgs84_pos#hasGeometry";
  private static final String URI_GEOSPARQL_HAS_GEOMETRY =
      "http://www.opengis.net/ont/geosparql#hasGeometry";
  private static final String URI_ORG_SUB_ORGANIZATION_OF =
      "http://www.w3.org/ns/org#subOrganizationOf";
  private static final String URI_ORG_HAS_SUB_ORGANIZATION =
      "http://www.w3.org/ns/org#hasSubOrganization";
  private static final String URI_ORG_UNIT_OF = "http://www.w3.org/ns/org#unitOf";
  private static final String URI_ORG_HAS_UNIT = "http://www.w3.org/ns/org#hasUnit";
  private static final String URI_ORG_TRANSITIVE_SUB_ORG =
      "http://www.w3.org/ns/org#transitiveSubOrganizationOf";

  @Autowired
  private OndernemingsService ondernemingsService;

  @Autowired
  private JsonldConfiguration jsonldConfiguration;

  @Autowired
  private ReasoningModelConfiguration reasoningModelConfiguration;

  @GetMapping(value = "id/organisatie/{ondernemingsnr}", produces = "text/html")
  public String getOndernemingAsRdfHtml(@PathVariable String ondernemingsnr) {
    return "redirect:/doc/organisatie/" + ondernemingsnr;
  }

  @GetMapping(value = "doc/organisatie/{ondernemingsnr}")
  public String getOndernemingAsRdfHtml(
      @PathVariable String ondernemingsnr,
      Model springModel) {

    org.apache.jena.rdf.model.Model rdfModel = ondernemingsService.extractModel(ondernemingsnr);
    String jsonld = ondernemingsService.getJsonLdFromModel(rdfModel);

    RDFDataExtractor extractor = new RDFDataExtractor(rdfModel, ondernemingsnr);

    springModel.addAttribute("uri", extractor.getUri());
    springModel.addAttribute("title", extractor.getTitle());
    springModel.addAttribute("hasGeoData", extractor.hasGeoData());
    springModel.addAttribute("wgs84Point", extractor.getWgs84Point());
    springModel.addAttribute("lambertPoint", extractor.getLambertPoint());
    springModel.addAttribute("latitude", extractor.getLatitude());
    springModel.addAttribute("longitude", extractor.getLongitude());
    springModel.addAttribute("wktGeometry", extractor.getWktGeometry());
    springModel.addAttribute("jsonld", jsonld);
    springModel.addAttribute("rdfSections", extractor.getRdfSections());
    springModel.addAttribute("subjectTypes", extractor.getSubjectTypes());
    springModel.addAttribute("hasIncomingRelations", extractor.hasIncomingRelations());
    springModel.addAttribute("incomingRelations", extractor.getIncomingRelations());
    springModel.addAttribute("hasOutgoingRelations", extractor.hasOutgoingRelations());
    springModel.addAttribute("outgoingRelations", extractor.getOutgoingRelations());
    springModel.addAttribute("rawRdfData", extractor.getRawRdfData());

    return "fiche-rdf";
  }

  /**
   * Extracts structured data from an RDF model for use in Thymeleaf templates.
   */
  private class RDFDataExtractor {
    private final org.apache.jena.rdf.model.Model jenaModel;
    private final String ondernemingsnr;
    private final Resource subject;
    private final org.apache.jena.rdf.model.Model reasoningModel;

    public RDFDataExtractor(org.apache.jena.rdf.model.Model jenaModel, String ondernemingsnr) {
      this.jenaModel = jenaModel;
      this.ondernemingsnr = ondernemingsnr;
      this.subject = jenaModel.createResource(getUri());
      this.reasoningModel = reasoningModelConfiguration.loadTurtleFromClasspath();
    }

    public String getUri() {
      return jsonldConfiguration.getJsonLDContext().get("organisation").asText() + ondernemingsnr;
    }

    public String getTitle() {
      Property[] nameProperties = {
        jenaModel.createProperty(URI_RDFS_LABEL),
        jenaModel.createProperty(URI_FOAF_NAME),
        jenaModel.createProperty(URI_SCHEMA_NAME),
        jenaModel.createProperty(URI_ORG_LEGAL_NAME)
      };

      for (Property prop : nameProperties) {
        StmtIterator iter = jenaModel.listStatements(subject, prop, (RDFNode) null);
        if (iter.hasNext()) {
          String title = iter.next().getObject().toString();
          // Remove language tag if present (e.g., "UMICORE@nl" -> "UMICORE")
          int languageTagIndex = title.indexOf('@');
          if (languageTagIndex > 0) {
            title = title.substring(0, languageTagIndex);
          }
          return title;
        }
      }

      return "Organisatie " + ondernemingsnr;
    }

    /**
     * Returns the RDF types of the subject for display in the header.
     */
    public List<RDFRelation> getSubjectTypes() {
      List<RDFRelation> types = new ArrayList<>();
      Property typeProperty = jenaModel.createProperty(URI_RDF_TYPE);

      StmtIterator iter = jenaModel.listStatements(subject, typeProperty, (RDFNode) null);
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        RDFNode object = stmt.getObject();

        if (object.isResource()) {
          RDFRelation typeRelation = new RDFRelation();
          typeRelation.label = getResourceLabel(object.asResource());
          typeRelation.target = object.asResource().getURI();
          typeRelation.targetLabel = typeRelation.label;

          if (typeRelation.label != null && !typeRelation.label.isEmpty()) {
            types.add(typeRelation);
          }
        }
      }

      return types;
    }

    public boolean hasGeoData() {
      return (getLatitude() != null && getLongitude() != null) || getWktGeometry() != null;
    }

    public String getWgs84Point() {
      Property wgs84Prop = jenaModel.createProperty(URI_LOCN_GEOMETRY);
      StmtIterator iter = jenaModel.listStatements(subject, wgs84Prop, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }
      return null;
    }

    public String getLambertPoint() {
      Property lambertProp = jenaModel.createProperty(URI_IMJV_LAMBERT);
      StmtIterator iter = jenaModel.listStatements(subject, lambertProp, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }
      return null;
    }

    public Double getLatitude() {
      Property latProp = jenaModel.createProperty(URI_WGS84_LAT);
      StmtIterator iter = jenaModel.listStatements(subject, latProp, (RDFNode) null);
      if (iter.hasNext()) {
        Statement stmt = iter.next();
        if (stmt.getObject().isLiteral()) {
          try {
            return stmt.getObject().asLiteral().getDouble();
          } catch (NumberFormatException e) {
            return null;
          }
        }
      }
      return null;
    }

    public Double getLongitude() {
      Property lonProp = jenaModel.createProperty(URI_WGS84_LONG);
      StmtIterator iter = jenaModel.listStatements(subject, lonProp, (RDFNode) null);
      if (iter.hasNext()) {
        Statement stmt = iter.next();
        if (stmt.getObject().isLiteral()) {
          try {
            return stmt.getObject().asLiteral().getDouble();
          } catch (NumberFormatException e) {
            return null;
          }
        }
      }
      return null;
    }

    public String getWktGeometry() {
      Property[] geoProps = {
        jenaModel.createProperty(URI_LOCN_GEOMETRY),
        jenaModel.createProperty(URI_IMJV_LAMBERT),
        jenaModel.createProperty(URI_WGS84_AS_WKT),
        jenaModel.createProperty(URI_GEOSPARQL_AS_WKT),
        jenaModel.createProperty(URI_WGS84_HAS_SERIALIZATION),
        jenaModel.createProperty(URI_GEOSPARQL_HAS_SERIALIZATION)
      };

      for (Property prop : geoProps) {
        StmtIterator iter = jenaModel.listStatements(subject, prop, (RDFNode) null);
        if (iter.hasNext()) {
          Statement stmt = iter.next();
          if (stmt.getObject().isLiteral()) {
            String value = stmt.getObject().asLiteral().getString();
            if (value.startsWith("POINT(") || value.startsWith("POLYGON(")) {
              return value;
            }
          }
        }
      }

      // Also check for geo:hasGeometry pattern where geometry is a blank node with asWKT
      Property[] hasGeometryProps = {
        jenaModel.createProperty(URI_WGS84_HAS_GEOMETRY),
        jenaModel.createProperty(URI_GEOSPARQL_HAS_GEOMETRY)
      };

      for (Property hasGeometryProp : hasGeometryProps) {
        StmtIterator geomIter = jenaModel.listStatements(subject, hasGeometryProp, (RDFNode) null);

        while (geomIter.hasNext()) {
          Statement geomStmt = geomIter.next();
          if (geomStmt.getObject().isResource()) {
            Resource geometryResource = geomStmt.getObject().asResource();

            Property[] wktProps = {
              jenaModel.createProperty(URI_WGS84_AS_WKT),
              jenaModel.createProperty(URI_GEOSPARQL_AS_WKT),
              jenaModel.createProperty(URI_WGS84_HAS_SERIALIZATION),
              jenaModel.createProperty(URI_GEOSPARQL_HAS_SERIALIZATION)
            };

            for (Property wktProp : wktProps) {
              StmtIterator wktIter =
                  jenaModel.listStatements(geometryResource, wktProp, (RDFNode) null);
              while (wktIter.hasNext()) {
                Statement wktStmt = wktIter.next();
                if (wktStmt.getObject().isLiteral()) {
                  String wktValue = wktStmt.getObject().asLiteral().getString();
                  if (wktValue.startsWith("POINT(") || wktValue.startsWith("POLYGON(")) {
                    return wktValue;
                  }
                }
              }
            }
          }
        }
      }

      // Fallback: create WKT from lat/lon if available
      if (getLatitude() != null && getLongitude() != null) {
        return "POINT(" + getLongitude() + " " + getLatitude() + ")";
      }

      return null;
    }

    public List<RDFSection> getRdfSections() {
      List<RDFSection> sections = new ArrayList<>();

      List<RDFPredicate> literalPredicates = new ArrayList<>();
      List<RDFPredicate> namedResourcePredicates = new ArrayList<>();
      Map<String, List<RDFPredicate>> blankNodePredicatesByType = new LinkedHashMap<>();

      StmtIterator iter = jenaModel.listStatements(subject, null, (RDFNode) null);
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        Property predicate = stmt.getPredicate();
        RDFNode object = stmt.getObject();

        // Skip geo properties (handled separately)
        if (predicate.getURI().contains("geo/wgs84_pos")
            || predicate.getURI().contains("locn#geometry")
            || predicate.getURI().contains("lambert")) {
          continue;
        }

        String predicateLabel = getPredicateLabel(predicate);
        String predicateKey = predicate.getURI();

        RDFObject rdfObject = new RDFObject();
        if (object.isLiteral()) {
          rdfObject.isLiteral = true;
          rdfObject.value = object.asLiteral().getString();
          rdfObject.label = object.asLiteral().getString();

          RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
          literalPredicates.add(rdfPredicate);

        } else if (object.isResource()) {
          if (object.isAnon()) {
            rdfObject.isLiteral = false;
            rdfObject.value = "";
            rdfObject.label = getBlankNodeTypeLabel(object.asResource());
            rdfObject.nestedProperties = extractBlankNodeProperties(object.asResource());

            String typeLabel = rdfObject.label;
            RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
            blankNodePredicatesByType
                .computeIfAbsent(typeLabel, k -> new ArrayList<>())
                .add(rdfPredicate);

          } else {
            // Skip rdf:type predicates — shown in the header
            if (predicateKey.equals(URI_RDF_TYPE)) {
              continue;
            }

            rdfObject.isLiteral = false;
            rdfObject.value = object.asResource().getURI();
            rdfObject.label = getResourceLabel(object.asResource());

            RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
            namedResourcePredicates.add(rdfPredicate);
          }
        }
      }

      if (!literalPredicates.isEmpty()) {
        sections.add(new RDFSection("Eigenschappen", literalPredicates));
      }

      if (!namedResourcePredicates.isEmpty()) {
        sections.add(new RDFSection("Uitgaande relaties", namedResourcePredicates));
      }

      for (Map.Entry<String, List<RDFPredicate>> entry : blankNodePredicatesByType.entrySet()) {
        sections.add(new RDFSection(entry.getKey(), entry.getValue()));
      }

      return sections;
    }

    private String getPredicateLabel(Property predicate) {
      // Try rdfs:label with language preference: @nl first, then @en, then any
      String label = findLabelByLanguage(predicate, reasoningModel, "nl");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      label = findLabelByLanguage(predicate, reasoningModel, "en");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      StmtIterator iter = reasoningModel.listStatements(predicate, RDFS.label, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }

      label = findLabelByLanguage(predicate, jenaModel, "nl");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      label = findLabelByLanguage(predicate, jenaModel, "en");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      iter = jenaModel.listStatements(predicate, RDFS.label, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }

      return predicate.getLocalName();
    }

    private String getResourceLabel(Resource resource) {
      if (resource == null) {
        return "";
      }

      String label = findLabelByLanguage(resource, reasoningModel, "nl");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      label = findLabelByLanguage(resource, reasoningModel, "en");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      StmtIterator iter = reasoningModel.listStatements(resource, RDFS.label, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }

      label = findLabelByLanguage(resource, jenaModel, "nl");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      label = findLabelByLanguage(resource, jenaModel, "en");
      if (label != null && !label.isEmpty()) {
        return label;
      }

      iter = jenaModel.listStatements(resource, RDFS.label, (RDFNode) null);
      if (iter.hasNext()) {
        return iter.next().getObject().toString();
      }

      String uri = resource.getURI();
      if (uri == null) {
        return "";
      }

      if (uri.contains("#")) {
        return uri.substring(uri.indexOf("#") + 1);
      } else if (uri.contains("/")) {
        return uri.substring(uri.lastIndexOf("/") + 1);
      }

      return uri;
    }

    /**
     * Finds a label with a specific language tag in the given model.
     */
    private String findLabelByLanguage(
        Resource resource, org.apache.jena.rdf.model.Model model, String language) {
      if (resource == null || model == null || language == null) {
        return null;
      }

      StmtIterator iter = model.listStatements(resource, RDFS.label, (RDFNode) null);

      while (iter.hasNext()) {
        Statement stmt = iter.next();
        RDFNode object = stmt.getObject();
        if (object.isLiteral()) {
          Literal literal = object.asLiteral();
          if (language.equals(literal.getLanguage())) {
            return literal.getString();
          }
        }
      }
      return null;
    }

    /**
     * Returns the type label(s) of a blank node to use as its display label.
     */
    private String getBlankNodeTypeLabel(Resource blankNode) {
      if (blankNode == null || !blankNode.isAnon()) {
        return "[Blank Node]";
      }

      Property typeProperty = jenaModel.createProperty(URI_RDF_TYPE);
      List<String> typeLabels = new ArrayList<>();

      StmtIterator typeIter = jenaModel.listStatements(blankNode, typeProperty, (RDFNode) null);
      while (typeIter.hasNext()) {
        Statement typeStmt = typeIter.next();
        RDFNode typeObject = typeStmt.getObject();

        if (typeObject.isResource()) {
          String typeLabel = getResourceLabel(typeObject.asResource());
          if (typeLabel != null && !typeLabel.isEmpty()) {
            typeLabels.add(typeLabel);
          }
        }
      }

      if (!typeLabels.isEmpty()) {
        return String.join(", ", typeLabels);
      }

      return "[Blank Node]";
    }

    /**
     * Extracts incoming links (other resources that link to this subject).
     */
    private List<RDFPredicate> extractIncomingLinks() {
      List<RDFPredicate> incomingLinks = new ArrayList<>();
      Set<String> uniqueRelations = new HashSet<>();

      // 1. Direct incoming links
      StmtIterator directIter = jenaModel.listStatements(null, null, subject);

      while (directIter.hasNext()) {
        Statement stmt = directIter.next();
        Resource linkingSubject = stmt.getSubject();
        Property predicate = stmt.getPredicate();

        if (linkingSubject.equals(subject)) {
          continue;
        }

        String predicateLabel = getPredicateLabel(predicate);

        RDFObject rdfObject = new RDFObject();
        rdfObject.isLiteral = false;
        rdfObject.value = linkingSubject.getURI();
        rdfObject.label = getResourceLabel(linkingSubject);

        String uniqueKey = predicate.getURI() + "|" + linkingSubject.getURI();

        if (!uniqueRelations.contains(uniqueKey)) {
          uniqueRelations.add(uniqueKey);
          RDFPredicate rdfPredicate = new RDFPredicate(predicate.getURI(), predicateLabel, rdfObject);
          incomingLinks.add(rdfPredicate);
        }
      }

      // 2. Infer incoming links from inverse outgoing relationships
      StmtIterator outgoingIter = jenaModel.listStatements(subject, null, (RDFNode) null);

      while (outgoingIter.hasNext()) {
        Statement stmt = outgoingIter.next();
        Property predicate = stmt.getPredicate();
        RDFNode object = stmt.getObject();

        if (object.isLiteral() || object.isAnon()) {
          continue;
        }

        Resource targetResource = object.asResource();
        String inversePredicateUri = getInversePredicate(predicate.getURI());

        if (inversePredicateUri != null) {
          Property inversePredicate = jenaModel.createProperty(inversePredicateUri);
          String inversePredicateLabel = getPredicateLabel(inversePredicate);

          RDFObject rdfObject = new RDFObject();
          rdfObject.isLiteral = false;
          rdfObject.value = targetResource.getURI();
          rdfObject.label = getResourceLabel(targetResource);

          String uniqueKey = inversePredicateUri + "|" + targetResource.getURI();

          if (!uniqueRelations.contains(uniqueKey)) {
            uniqueRelations.add(uniqueKey);
            RDFPredicate rdfPredicate =
                new RDFPredicate(inversePredicateUri, inversePredicateLabel, rdfObject);
            incomingLinks.add(rdfPredicate);
          }
        }
      }

      return incomingLinks;
    }

    /**
     * Returns the inverse predicate URI for common organisational relationships.
     */
    private String getInversePredicate(String predicateUri) {
      return switch (predicateUri) {
        case URI_ORG_SUB_ORGANIZATION_OF, URI_ORG_TRANSITIVE_SUB_ORG -> URI_ORG_HAS_SUB_ORGANIZATION;
        case URI_ORG_UNIT_OF -> URI_ORG_HAS_UNIT;
        default -> null;
      };
    }

    /**
     * Recursively extracts properties from a blank node.
     */
    private List<RDFPredicate> extractBlankNodeProperties(Resource blankNode) {
      List<RDFPredicate> properties = new ArrayList<>();

      if (blankNode == null || !blankNode.isAnon()) {
        return properties;
      }

      StmtIterator iter = jenaModel.listStatements(blankNode, null, (RDFNode) null);
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        Property predicate = stmt.getPredicate();
        RDFNode object = stmt.getObject();

        String predicateLabel = getPredicateLabel(predicate);

        RDFObject rdfObject = new RDFObject();
        if (object.isLiteral()) {
          rdfObject.isLiteral = true;
          rdfObject.value = object.asLiteral().getString();
          rdfObject.label = object.asLiteral().getString();
        } else if (object.isResource()) {
          if (object.isAnon()) {
            rdfObject.isLiteral = false;
            rdfObject.value = "";
            rdfObject.label = getBlankNodeTypeLabel(object.asResource());
            rdfObject.nestedProperties = extractBlankNodeProperties(object.asResource());
          } else {
            rdfObject.isLiteral = false;
            rdfObject.value = object.asResource().getURI();
            rdfObject.label = getResourceLabel(object.asResource());
          }
        }

        RDFPredicate rdfPredicate =
            new RDFPredicate(predicate.getURI(), predicateLabel, rdfObject);
        properties.add(rdfPredicate);
      }

      return properties;
    }

    public boolean hasIncomingRelations() {
      return !extractIncomingLinks().isEmpty();
    }

    public List<RDFRelation> getIncomingRelations() {
      List<RDFRelation> relations = new ArrayList<>();
      List<RDFPredicate> incomingLinks = extractIncomingLinks();

      for (RDFPredicate predicate : incomingLinks) {
        for (RDFObject object : predicate.getObjects()) {
          RDFRelation relation = new RDFRelation();
          relation.label = predicate.getLabel();
          relation.predicateUri = predicate.getUri();
          if (!object.isLiteral() && object.getValue() != null && !object.getValue().isEmpty()) {
            relation.target = object.getValue();
            relation.targetLabel = object.getLabel();
          } else {
            relation.targetLabel = object.getLabel();
          }
          relations.add(relation);
        }
      }

      return relations;
    }

    public boolean hasOutgoingRelations() {
      StmtIterator iter = jenaModel.listStatements(subject, null, (RDFNode) null);
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        if (!stmt.getObject().isLiteral() && !stmt.getObject().isAnon()) {
          return true;
        }
      }
      return false;
    }

    public List<RDFRelation> getOutgoingRelations() {
      List<RDFRelation> relations = new ArrayList<>();

      StmtIterator iter = jenaModel.listStatements(subject, null, (RDFNode) null);
      while (iter.hasNext()) {
        Statement stmt = iter.next();
        RDFNode object = stmt.getObject();

        if (!object.isLiteral() && !object.isAnon()) {
          Resource target = object.asResource();
          RDFRelation relation = new RDFRelation();
          relation.label = getPredicateLabel(stmt.getPredicate());
          relation.target = target.getURI();
          relation.targetLabel = getResourceLabel(target);
          relations.add(relation);
        }
      }

      return relations;
    }

    public String getRawRdfData() {
      StringWriter writer = new StringWriter();
      jenaModel.write(writer, "TURTLE");
      return writer.toString();
    }
  }

  // Data classes for Thymeleaf template — getters are accessed via reflection
  @SuppressWarnings("unused")
  private class RDFSection {
    private final String title;
    private final List<RDFPredicate> predicates;

    public RDFSection(String title, List<RDFPredicate> predicates) {
      this.title = title;
      this.predicates = predicates;
    }

    public String getTitle() { return title; }
    public List<RDFPredicate> getPredicates() { return predicates; }
  }

  @SuppressWarnings("unused")
  private class RDFPredicate {
    private final String uri;
    private final String label;
    private final List<RDFObject> objects;

    public RDFPredicate(String uri, String label, RDFObject object) {
      this.uri = uri;
      this.label = label;
      this.objects = new ArrayList<>();
      this.objects.add(object);
    }

    public String getUri() { return uri; }
    public String getLabel() { return label; }
    public List<RDFObject> getObjects() { return objects; }
  }

  @SuppressWarnings("unused")
  private class RDFObject {
    private boolean isLiteral;
    private String value;
    private String label;
    private List<RDFPredicate> nestedProperties; // For blank nodes: their properties

    public boolean isLiteral() { return isLiteral; }
    public String getValue() { return value; }
    public String getLabel() { return label; }
    public List<RDFPredicate> getNestedProperties() { return nestedProperties; }
    public boolean hasNestedProperties() { return nestedProperties != null && !nestedProperties.isEmpty(); }
  }

  @SuppressWarnings("unused")
  private static class RDFRelation {
    private String label;
    private String predicateUri;
    private String target;
    private String targetLabel;

    public String getLabel() { return label; }
    public String getPredicateUri() { return predicateUri; }
    public String getTarget() { return target; }
    public String getTargetLabel() { return targetLabel; }
  }
}
