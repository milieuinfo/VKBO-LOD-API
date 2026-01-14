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

    @GetMapping(value = "id/organisatie/{ondernemingsnr}",
            produces = "text/html")
    public String getOndernemingAsRdfHtml(
            @PathVariable String ondernemingsnr) {
        return "redirect:/doc/organisatie/{ondernemingsnr}";
    }
    @GetMapping(value = "doc/organisatie/{ondernemingsnr}")
    public String getOndernemingAsRdfHtml(
            @PathVariable String ondernemingsnr,
            Model springModel) {

        // Get RDF model from the service
        org.apache.jena.rdf.model.Model rdfModel = ondernemingsService.extractModel(ondernemingsnr);
        String jsonld = ondernemingsService.getJsonLdFromModel(rdfModel);
        
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
        springModel.addAttribute("jsonld", jsonld);
        
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
        springModel.addAttribute("subjectTypes", extractor.getSubjectTypes());
        springModel.addAttribute("hasIncomingRelations", extractor.hasIncomingRelations());
        springModel.addAttribute("incomingRelations", extractor.getIncomingRelations());
        springModel.addAttribute("hasOutgoingRelations", extractor.hasOutgoingRelations());
        springModel.addAttribute("outgoingRelations", extractor.getOutgoingRelations());
        springModel.addAttribute("rawRdfData", extractor.getRawRdfData());
        
        return "fiche-rdf";  // Use the rdf template
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
         * Get the RDF types of the subject for display in the header
         */
        public List<RDFRelation> getSubjectTypes() {
            List<RDFRelation> types = new ArrayList<>();
            Property typeProperty = jenaModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
            
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
            
            // Separate predicates by object type
            List<RDFPredicate> literalPredicates = new ArrayList<>();
            List<RDFPredicate> namedResourcePredicates = new ArrayList<>();
            Map<String, List<RDFPredicate>> blankNodePredicatesByType = new LinkedHashMap<>();
            
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
                String predicateKey = predicate.getURI(); // Unique key for the predicate
                
                // Create RDF object representation
                RDFObject rdfObject = new RDFObject();
                if (object.isLiteral()) {
                    rdfObject.isLiteral = true;
                    rdfObject.value = object.asLiteral().getString();
                    rdfObject.label = object.asLiteral().getString(); // For literals, label = value
                    
                    // Add to literal predicates
                    RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
                    literalPredicates.add(rdfPredicate);
                    
                } else if (object.isResource()) {
                    if (object.isAnon()) {
                        // Handle blank nodes (anonymous resources) - recursively extract properties
                        rdfObject.isLiteral = false;
                        rdfObject.value = ""; // Blank nodes don't have URIs
                        rdfObject.label = getBlankNodeTypeLabel(object.asResource()); // Use type label instead of generic text
                        rdfObject.nestedProperties = extractBlankNodeProperties(object.asResource());
                        
                        // Group blank nodes by their type label
                        String typeLabel = rdfObject.label;
                        RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
                        blankNodePredicatesByType.computeIfAbsent(typeLabel, k -> new ArrayList<>()).add(rdfPredicate);
                        
                    } else {
                        // Skip rdf:type predicates - they will be shown in the header
                        if (predicateKey.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {
                            continue;
                        }
                        
                        // Handle named resources
                        rdfObject.isLiteral = false;
                        rdfObject.value = object.asResource().getURI();
                        rdfObject.label = getResourceLabel(object.asResource()); // Use reasoning model for labels
                        
                        // Add to named resource predicates
                        RDFPredicate rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
                        namedResourcePredicates.add(rdfPredicate);
                    }
                }
            }
            
            // Create sections based on the new structure
            if (!literalPredicates.isEmpty()) {
                sections.add(new RDFSection("Eigenschappen", literalPredicates));
            }
            
            if (!namedResourcePredicates.isEmpty()) {
                sections.add(new RDFSection("Uitgaande relaties", namedResourcePredicates));
            }
            
            // Add sections for each blank node type
            for (Map.Entry<String, List<RDFPredicate>> entry : blankNodePredicatesByType.entrySet()) {
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
        
        /**
         * Get the type label(s) for a blank node to use as its display label
         */
        private String getBlankNodeTypeLabel(Resource blankNode) {
            if (blankNode == null || !blankNode.isAnon()) {
                return "[Blank Node]";
            }
            
            // Look for rdf:type properties
            Property typeProperty = jenaModel.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
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
                // Return comma-separated type labels
                return String.join(", ", typeLabels);
            }
            
            // Fallback if no types found
            return "[Blank Node]";
        }

        /**
         * Extract incoming links (other resources that link to this subject)
         */
        private List<RDFPredicate> extractIncomingLinks() {
            List<RDFPredicate> incomingLinks = new ArrayList<>();
            Set<String> uniqueRelations = new HashSet<>(); // Track unique predicate-target combinations
            
            // 1. Look for direct incoming links (statements where this subject is the object)
            StmtIterator directIter = jenaModel.listStatements(null, null, subject);
            
            while (directIter.hasNext()) {
                Statement stmt = directIter.next();
                Resource linkingSubject = stmt.getSubject();
                Property predicate = stmt.getPredicate();
                
                // Skip if the linking subject is the same as our subject (self-references)
                if (linkingSubject.equals(subject)) {
                    continue;
                }
                
                // Get predicate label
                String predicateLabel = getPredicateLabel(predicate);
                
                // Create RDF object representation for the linking subject
                RDFObject rdfObject = new RDFObject();
                rdfObject.isLiteral = false;
                rdfObject.value = linkingSubject.getURI();
                rdfObject.label = getResourceLabel(linkingSubject);
                
                // Create unique key for this relation
                String uniqueKey = predicate.getURI() + "|" + linkingSubject.getURI();
                
                // Only add if this relation is not already present
                if (!uniqueRelations.contains(uniqueKey)) {
                    uniqueRelations.add(uniqueKey);
                    
                    // Create predicate and add to incoming links
                    RDFPredicate rdfPredicate = new RDFPredicate(
                        predicate.getURI(), 
                        predicateLabel, 
                        rdfObject
                    );
                    incomingLinks.add(rdfPredicate);
                }
            }
            
            // 2. Infer incoming links from outgoing links (inverse relationships)
            // This handles cases where the RDF model only contains data about the current subject
            // but we can infer incoming links from outgoing relationships
            StmtIterator outgoingIter = jenaModel.listStatements(subject, null, (RDFNode) null);
            
            while (outgoingIter.hasNext()) {
                Statement stmt = outgoingIter.next();
                Property predicate = stmt.getPredicate();
                RDFNode object = stmt.getObject();
                
                // Skip literals and blank nodes - we only want named resources
                if (object.isLiteral() || object.isAnon()) {
                    continue;
                }
                
                Resource targetResource = object.asResource();
                String inversePredicateUri = getInversePredicate(predicate.getURI());
                
                if (inversePredicateUri != null) {
                    // Get the inverse predicate
                    Property inversePredicate = jenaModel.createProperty(inversePredicateUri);
                    String inversePredicateLabel = getPredicateLabel(inversePredicate);
                    
                    // Create RDF object representation for the target resource
                    RDFObject rdfObject = new RDFObject();
                    rdfObject.isLiteral = false;
                    rdfObject.value = targetResource.getURI();
                    rdfObject.label = getResourceLabel(targetResource);
                    
                    // Create unique key for this relation
                    String uniqueKey = inversePredicateUri + "|" + targetResource.getURI();
                    
                    // Only add if this relation is not already present
                    if (!uniqueRelations.contains(uniqueKey)) {
                        uniqueRelations.add(uniqueKey);
                        
                        // Create predicate and add to incoming links
                        RDFPredicate rdfPredicate = new RDFPredicate(
                            inversePredicateUri, 
                            inversePredicateLabel, 
                            rdfObject
                        );
                        incomingLinks.add(rdfPredicate);
                    }
                }
            }
            
            return incomingLinks;
        }
        
        /**
         * Get the inverse predicate URI for common organizational relationships
         */
        private String getInversePredicate(String predicateUri) {
            // Map of predicate URIs to their inverses
            if (predicateUri.equals("http://www.w3.org/ns/org#subOrganizationOf")) {
                return "http://www.w3.org/ns/org#hasSubOrganization";
            } else if (predicateUri.equals("http://www.w3.org/ns/org#unitOf")) {
                return "http://www.w3.org/ns/org#hasUnit";
            } else if (predicateUri.equals("http://www.w3.org/ns/org#transitiveSubOrganizationOf")) {
                return "http://www.w3.org/ns/org#hasSubOrganization"; // Transitive inverse
            }
            // Add more inverse relationships as needed
            return null;
        }

        /**
         * Recursively extract properties from a blank node
         */
        private List<RDFPredicate> extractBlankNodeProperties(Resource blankNode) {
            List<RDFPredicate> properties = new ArrayList<>();
            
            if (blankNode == null || !blankNode.isAnon()) {
                return properties;
            }
            
            // Get all properties of this blank node
            StmtIterator iter = jenaModel.listStatements(blankNode, null, (RDFNode) null);
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                Property predicate = stmt.getPredicate();
                RDFNode object = stmt.getObject();
                
                // Get predicate label
                String predicateLabel = getPredicateLabel(predicate);
                
                // Create RDF object representation
                RDFObject rdfObject = new RDFObject();
                if (object.isLiteral()) {
                    rdfObject.isLiteral = true;
                    rdfObject.value = object.asLiteral().getString();
                    rdfObject.label = object.asLiteral().getString();
                } else if (object.isResource()) {
                    if (object.isAnon()) {
                        // Recursively handle nested blank nodes
                        rdfObject.isLiteral = false;
                        rdfObject.value = "";
                        rdfObject.label = getBlankNodeTypeLabel(object.asResource()); // Use type label
                        rdfObject.nestedProperties = extractBlankNodeProperties(object.asResource());
                    } else {
                        // Handle named resources
                        rdfObject.isLiteral = false;
                        rdfObject.value = object.asResource().getURI();
                        rdfObject.label = getResourceLabel(object.asResource());
                    }
                }
                
                // Create predicate and add to properties
                RDFPredicate rdfPredicate = new RDFPredicate(predicate.getURI(), predicateLabel, rdfObject);
                properties.add(rdfPredicate);
            }
            
            return properties;
        }


        
        public boolean hasIncomingRelations() {
            // Check if there are any incoming links
            List<RDFPredicate> incomingLinks = extractIncomingLinks();
            return !incomingLinks.isEmpty();
        }
        
        public List<RDFRelation> getIncomingRelations() {
            // Return the incoming links as RDFRelations
            List<RDFRelation> relations = new ArrayList<>();
            List<RDFPredicate> incomingLinks = extractIncomingLinks();
            
            for (RDFPredicate predicate : incomingLinks) {
                for (RDFObject object : predicate.getObjects()) {
                    RDFRelation relation = new RDFRelation();
                    relation.label = predicate.getLabel();
                    relation.predicateUri = predicate.getUri(); // Add predicate URI
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
        private List<RDFPredicate> nestedProperties; // For blank nodes: their properties
        
        public boolean isLiteral() { return isLiteral; }
        public String getValue() { return value; }
        public String getLabel() { return label; }
        public List<RDFPredicate> getNestedProperties() { return nestedProperties; }
        public boolean hasNestedProperties() { return nestedProperties != null && !nestedProperties.isEmpty(); }
        
        // Note: Use Thymeleaf status variable (objStat.last) instead of this method
    }
    
    private class RDFRelation {
        private String label;
        private String predicateUri;
        private String target;
        private String targetLabel;
        private String description;
        
        public String getLabel() { return label; }
        public String getPredicateUri() { return predicateUri; }
        public String getTarget() { return target; }
        public String getTargetLabel() { return targetLabel; }
        public String getDescription() { return description; }
    }
}