package be.vlaanderen.omgeving.vkbolodapi.controller;

import be.vlaanderen.omgeving.vkbolodapi.configuration.JsonldConfiguration;
import be.vlaanderen.omgeving.vkbolodapi.configuration.ReasoningModelConfiguration;
import be.vlaanderen.omgeving.vkbolodapi.service.OndernemingsService;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.StringWriter;
import java.util.*;

/**
 * Controller for RDF-based HTML views
 */
@Controller
public class RDFController {

    @Autowired
    private OndernemingsService ondernemingsService;

    @Autowired
    private JsonldConfiguration jsonldConfiguration;

    @Autowired
    private ReasoningModelConfiguration reasoningModelConfiguration;

    @GetMapping(value = "rdf/organisatie/{ondernemingsnr}")
    public String getOndernemingAsRdfHtml(
            @PathVariable String ondernemingsnr,
            Model springModel) {

        // Get RDF model from the service
        org.apache.jena.rdf.model.Model rdfModel = ondernemingsService.extractModel(ondernemingsnr);
        
        // Extract data for the template
        RDFDataExtractor extractor = new RDFDataExtractor(rdfModel, ondernemingsnr);
        
        // Set model attributes
        springModel.addAttribute("uri", extractor.getUri());
        springModel.addAttribute("title", extractor.getTitle());
        springModel.addAttribute("hasGeoData", extractor.hasGeoData());
        springModel.addAttribute("wgs84Point", extractor.getWgs84Point());
        springModel.addAttribute("lambertPoint", extractor.getLambertPoint());
        springModel.addAttribute("latitude", extractor.getLatitude());
        springModel.addAttribute("longitude", extractor.getLongitude());
        springModel.addAttribute("wktGeometry", extractor.getWktGeometry());
        
        // Debug: Log RDF sections
        List<RDFSection> sections = extractor.getRdfSections();
        System.out.println("DEBUG: Found " + (sections != null ? sections.size() : 0) + " RDF sections");
        if (sections != null) {
            for (RDFSection section : sections) {
                System.out.println("  Section: " + section.getTitle() + " with " + 
                                  (section.getPredicates() != null ? section.getPredicates().size() : 0) + " predicates");
            }
        }
        
        springModel.addAttribute("rdfSections", sections);
        springModel.addAttribute("hasIncomingRelations", extractor.hasIncomingRelations());
        springModel.addAttribute("incomingRelations", extractor.getIncomingRelations());
        springModel.addAttribute("hasOutgoingRelations", extractor.hasOutgoingRelations());
        springModel.addAttribute("outgoingRelations", extractor.getOutgoingRelations());
        springModel.addAttribute("rawRdfData", extractor.getRawRdfData());
        
        return "fiche-rdf-simple";  // Use the simple template
    }

    /**
     * Helper class to extract data from RDF model for template
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
            // Try to get a name/title from the RDF data
            Property[] nameProperties = {
                jenaModel.createProperty("http://www.w3.org/2000/01/rdf-schema#label"),
                jenaModel.createProperty("http://xmlns.com/foaf/0.1/name"),
                jenaModel.createProperty("http://schema.org/name"),
                jenaModel.createProperty("http://www.w3.org/ns/org#legalName")
            };
            
            for (Property prop : nameProperties) {
                StmtIterator iter = jenaModel.listStatements(subject, prop, (RDFNode) null);
                if (iter.hasNext()) {
                    return iter.next().getObject().toString();
                }
            }
            
            return "Organisatie " + ondernemingsnr;
        }
        
        public boolean hasGeoData() {
            return (getLatitude() != null && getLongitude() != null) || getWktGeometry() != null;
        }
        
        public String getWgs84Point() {
            Property wgs84Prop = jenaModel.createProperty("http://www.w3.org/ns/locn#geometry");
            StmtIterator iter = jenaModel.listStatements(subject, wgs84Prop, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            return null;
        }
        
        public String getLambertPoint() {
            Property lambertProp = jenaModel.createProperty("https://data.imjv.omgeving.vlaanderen.be/ns/imjv#lambertWktString");
            StmtIterator iter = jenaModel.listStatements(subject, lambertProp, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            return null;
        }
        
        public Double getLatitude() {
            Property latProp = jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#lat");
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
            Property lonProp = jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#long");
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
            // Try to get WKT geometry
            Property[] geoProps = {
                jenaModel.createProperty("http://www.w3.org/ns/locn#geometry"),
                jenaModel.createProperty("https://data.imjv.omgeving.vlaanderen.be/ns/imjv#lambertWktString"),
                jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#asWKT"),
                jenaModel.createProperty("http://www.opengis.net/ont/geosparql#asWKT"),
                jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#hasSerialization"),
                jenaModel.createProperty("http://www.opengis.net/ont/geosparql#hasSerialization")
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
            // Try both W3C Basic Geo and GeoSPARQL hasGeometry properties
            Property[] hasGeometryProps = {
                jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#hasGeometry"),
                jenaModel.createProperty("http://www.opengis.net/ont/geosparql#hasGeometry")
            };
            
            for (Property hasGeometryProp : hasGeometryProps) {
                StmtIterator geomIter = jenaModel.listStatements(subject, hasGeometryProp, (RDFNode) null);
                
                while (geomIter.hasNext()) {
                    Statement geomStmt = geomIter.next();
                    if (geomStmt.getObject().isResource()) {
                        Resource geometryResource = geomStmt.getObject().asResource();
                        
                        // Check for asWKT property on the geometry resource
                        Property[] wktProps = {
                            jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#asWKT"),
                            jenaModel.createProperty("http://www.opengis.net/ont/geosparql#asWKT"),
                            jenaModel.createProperty("http://www.w3.org/2003/01/geo/wgs84_pos#hasSerialization"),
                            jenaModel.createProperty("http://www.opengis.net/ont/geosparql#hasSerialization")
                        };
                        
                        for (Property wktProp : wktProps) {
                            StmtIterator wktIter = jenaModel.listStatements(geometryResource, wktProp, (RDFNode) null);
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
            
            // Group properties by category
            Map<String, List<RDFPredicate>> categories = new LinkedHashMap<>();
            
            // Get all properties of the subject
            StmtIterator iter = jenaModel.listStatements(subject, null, (RDFNode) null);
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                Property predicate = stmt.getPredicate();
                RDFNode object = stmt.getObject();
                
                // Skip geo properties (handled separately)
                if (predicate.getURI().contains("geo/wgs84_pos") || 
                    predicate.getURI().contains("locn#geometry") ||
                    predicate.getURI().contains("lambert")) {
                    continue;
                }
                
                // Get predicate label
                String predicateLabel = getPredicateLabel(predicate);
                String category = getCategoryForPredicate(predicate);
                
                // Create RDF object representation
                RDFObject rdfObject = new RDFObject();
                if (object.isLiteral()) {
                    rdfObject.isLiteral = true;
                    rdfObject.value = object.asLiteral().getString();
                    rdfObject.label = object.asLiteral().getString(); // For literals, label = value
                } else if (object.isResource()) {
                    rdfObject.isLiteral = false;
                    rdfObject.value = object.asResource().getURI();
                    rdfObject.label = getResourceLabel(object.asResource()); // Use reasoning model for labels
                }
                
                // Add to appropriate category
                categories.computeIfAbsent(category, k -> new ArrayList<>())
                    .add(new RDFPredicate(predicate.getURI(), predicateLabel, rdfObject));
            }
            
            // Convert categories to sections
            for (Map.Entry<String, List<RDFPredicate>> entry : categories.entrySet()) {
                sections.add(new RDFSection(entry.getKey(), entry.getValue()));
            }
            
            return sections;
        }
        
        private String getPredicateLabel(Property predicate) {
            // Try to find rdfs:label with language preference: @nl first, then @en, then any
            String label = findLabelByLanguage(predicate, reasoningModel, "nl");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            label = findLabelByLanguage(predicate, reasoningModel, "en");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            // Fallback: any label from reasoning model
            StmtIterator iter = reasoningModel.listStatements(predicate, RDFS.label, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            
            // Fallback: try in main model with language preference
            label = findLabelByLanguage(predicate, jenaModel, "nl");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            label = findLabelByLanguage(predicate, jenaModel, "en");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            // Fallback: any label from main model
            iter = jenaModel.listStatements(predicate, RDFS.label, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            
            // Fallback: use local name
            return predicate.getLocalName();
        }
        
        private String getResourceLabel(Resource resource) {
            // Handle null resource
            if (resource == null) {
                return "";
            }
            
            // Try to find rdfs:label with language preference: @nl first, then @en, then any
            String label = findLabelByLanguage(resource, reasoningModel, "nl");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            label = findLabelByLanguage(resource, reasoningModel, "en");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            // Fallback: any label from reasoning model
            StmtIterator iter = reasoningModel.listStatements(resource, RDFS.label, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            
            // Fallback: try in the main model with language preference
            label = findLabelByLanguage(resource, jenaModel, "nl");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            label = findLabelByLanguage(resource, jenaModel, "en");
            if (label != null && !label.isEmpty()) {
                return label;
            }
            
            // Fallback: any label from main model
            iter = jenaModel.listStatements(resource, RDFS.label, (RDFNode) null);
            if (iter.hasNext()) {
                return iter.next().getObject().toString();
            }
            
            // Fallback: extract local name from URI
            String uri = resource.getURI();
            if (uri == null) {
                return ""; // Handle null URI
            }
            
            if (uri.contains("#")) {
                return uri.substring(uri.indexOf("#") + 1);
            } else if (uri.contains("/")) {
                return uri.substring(uri.lastIndexOf("/") + 1);
            }
            
            // Final fallback: use full URI
            return uri;
        }
        
        /**
         * Find a label with a specific language tag
         */
        private String findLabelByLanguage(Resource resource, org.apache.jena.rdf.model.Model model, String language) {
            if (resource == null || model == null || language == null) {
                return null;
            }
            
            // Look for rdfs:label with language tag
            StmtIterator iter = model.listStatements(
                resource, 
                RDFS.label, 
                (RDFNode) null
            );
            
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
        
        private String getLabelFromReasoningModel(String uri) {
            if (uri == null || uri.isEmpty()) {
                return "";
            }
            
            Resource resource = jenaModel.createResource(uri);
            return getResourceLabel(resource);
        }
        
        private String getCategoryForPredicate(Property predicate) {
            String uri = predicate.getURI();
            
            // Categorize based on namespace/URI patterns
            if (uri.contains("org#") || uri.contains("foaf#") || uri.contains("schema.org")) {
                return "Organisatiegegevens";
            } else if (uri.contains("adres") || uri.contains("locn#")) {
                return "Adresgegevens";
            } else if (uri.contains("identifier") || uri.contains("registratie")) {
                return "Identificatie";
            } else if (uri.contains("activiteit") || uri.contains("nace")) {
                return "Activiteiten";
            } else {
                return "Overige Eigenschappen";
            }
        }
        
        public boolean hasIncomingRelations() {
            // Check for incoming relations (this would require SPARQL endpoint or more complex reasoning)
            // For now, return false as this is not implemented in the basic version
            return false;
        }
        
        public List<RDFRelation> getIncomingRelations() {
            return new ArrayList<>();
        }
        
        public boolean hasOutgoingRelations() {
            // Check for outgoing relations that are not simple literals
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
            // Return Turtle format as raw RDF data
            StringWriter writer = new StringWriter();
            jenaModel.write(writer, "TURTLE");
            return writer.toString();
        }
    }
    
    // Data classes for template
    private class RDFSection {
        private String title;
        private List<RDFPredicate> predicates;
        
        public RDFSection(String title, List<RDFPredicate> predicates) {
            this.title = title;
            this.predicates = predicates;
        }
        
        public String getTitle() { return title; }
        public List<RDFPredicate> getPredicates() { return predicates; }
    }
    
    private class RDFPredicate {
        private String uri;
        private String label;
        private List<RDFObject> objects;
        
        public RDFPredicate(String uri, String label, RDFObject object) {
            this.uri = uri;
            this.label = label;
            this.objects = new ArrayList<>();
            this.objects.add(object);
        }
        
        public void addObject(RDFObject object) {
            this.objects.add(object);
        }
        
        public String getUri() { return uri; }
        public String getLabel() { return label; }
        public List<RDFObject> getObjects() { return objects; }
    }
    
    private class RDFObject {
        private boolean isLiteral;
        private String value;
        private String label;
        
        public boolean isLiteral() { return isLiteral; }
        public String getValue() { return value; }
        public String getLabel() { return label; }
        // Note: Use Thymeleaf status variable (objStat.last) instead of this method
    }
    
    private class RDFRelation {
        private String label;
        private String target;
        private String targetLabel;
        private String description;
        
        public String getLabel() { return label; }
        public String getTarget() { return target; }
        public String getTargetLabel() { return targetLabel; }
        public String getDescription() { return description; }
    }
}