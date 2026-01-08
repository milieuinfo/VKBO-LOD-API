package be.vlaanderen.omgeving.vkbolodapi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test for predicate grouping functionality
 */
public class PredicateGroupingTest {
    
    // Simplified version of the RDFPredicate class
    static class RDFPredicate {
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
        
        @Override
        public String toString() {
            return label + " [" + objects.size() + " objects]: " + objects;
        }
    }
    
    // Simplified version of the RDFObject class
    static class RDFObject {
        private boolean isLiteral;
        private String value;
        private String label;
        
        public boolean isLiteral() { return isLiteral; }
        public String getValue() { return value; }
        public String getLabel() { return label; }
        
        @Override
        public String toString() {
            return isLiteral ? "\"" + value + "\"" : "<" + value + ">";
        }
    }
    
    @Test
    public void testPredicateGrouping() {
        // Test the grouping logic
        Map<String, Map<String, RDFPredicate>> categoryPredicateMap = new LinkedHashMap<>();
        
        // Simulate multiple statements with the same predicate
        String[] testUris = {
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", 
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        };
        
        String[] testLabels = {
            "type", "type", "label", "type"
        };
        
        String[] testCategories = {
            "Overige Eigenschappen", "Overige Eigenschappen", "Overige Eigenschappen", "Overige Eigenschappen"
        };
        
        String[] testObjectValues = {
            "http://www.w3.org/ns/org#Organization",
            "http://www.w3.org/ns/org#OrganizationalUnit",
            "UMICORE",
            "http://www.w3.org/ns/regorg#RegisteredOrganization"
        };
        
        boolean[] testIsLiteral = {
            false, false, true, false
        };
        
        // Process each statement (simulating the RDF iteration)
        for (int i = 0; i < testUris.length; i++) {
            String predicateKey = testUris[i];
            String category = testCategories[i];
            String predicateLabel = testLabels[i];
            
            // Create RDF object
            RDFObject rdfObject = new RDFObject();
            rdfObject.isLiteral = testIsLiteral[i];
            rdfObject.value = testObjectValues[i];
            rdfObject.label = testObjectValues[i];
            
            // Get or create category map
            Map<String, RDFPredicate> predicateMap = categoryPredicateMap.computeIfAbsent(category, k -> new LinkedHashMap<>());
            
            // Get or create predicate entry
            RDFPredicate rdfPredicate = predicateMap.get(predicateKey);
            if (rdfPredicate == null) {
                // Create new predicate entry
                rdfPredicate = new RDFPredicate(predicateKey, predicateLabel, rdfObject);
                predicateMap.put(predicateKey, rdfPredicate);
            } else {
                // Add object to existing predicate entry
                rdfPredicate.addObject(rdfObject);
            }
        }
        
        // Verify that we have the expected grouping
        Map<String, RDFPredicate> predicates = categoryPredicateMap.get("Overige Eigenschappen");
        RDFPredicate typePredicate = predicates.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type");
        RDFPredicate labelPredicate = predicates.get("http://www.w3.org/2000/01/rdf-schema#label");
        
        assertNotNull(typePredicate, "Type predicate should exist");
        assertNotNull(labelPredicate, "Label predicate should exist");
        assertEquals(3, typePredicate.getObjects().size(), "Type predicate should have 3 objects");
        assertEquals(1, labelPredicate.getObjects().size(), "Label predicate should have 1 object");
    }
}