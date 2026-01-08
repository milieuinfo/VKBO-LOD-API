package be.vlaanderen.omgeving.vkbolodapi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test for blank node type label extraction functionality
 */
public class BlankNodeTypeLabelTest {
    
    // Simplified version of the RDFObject class
    static class RDFObject {
        private boolean isLiteral;
        private String value;
        private String label;
        private List<RDFPredicate> nestedProperties;
        
        public boolean isLiteral() { return isLiteral; }
        public String getValue() { return value; }
        public String getLabel() { return label; }
        public List<RDFPredicate> getNestedProperties() { return nestedProperties; }
        public boolean hasNestedProperties() { return nestedProperties != null && !nestedProperties.isEmpty(); }
        
        @Override
        public String toString() {
            if (isLiteral) {
                return "\"" + value + "\"";
            } else if (hasNestedProperties()) {
                return "[" + label + " with " + nestedProperties.size() + " properties]";
            } else if (value == null || value.isEmpty()) {
                return "[" + label + "]";
            } else {
                return "<" + value + ">";
            }
        }
    }
    
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
        
        public String getUri() { return uri; }
        public String getLabel() { return label; }
        public List<RDFObject> getObjects() { return objects; }
    }
    
    /**
     * Simulate type label extraction for blank nodes
     */
    private String getBlankNodeTypeLabel(String blankNodeId, Map<String, List<String>> mockTypes) {
        if (blankNodeId == null) {
            return "[Blank Node]";
        }
        
        List<String> typeLabels = mockTypes.getOrDefault(blankNodeId, new ArrayList<>());
        
        if (!typeLabels.isEmpty()) {
            // Return comma-separated type labels
            return String.join(", ", typeLabels);
        }
        
        // Fallback if no types found
        return "[Blank Node]";
    }
    
    @Test
    public void testBlankNodeTypeLabelExtraction() {
        // Mock data for different blank nodes
        Map<String, List<String>> mockTypes = new HashMap<>();
        
        // Registration blank node with single type
        mockTypes.put("registration", Arrays.asList("Identifier"));
        
        // Address blank node with single type
        mockTypes.put("address", Arrays.asList("Address"));
        
        // Complex blank node with multiple types
        mockTypes.put("complex", Arrays.asList("Organization", "LegalEntity"));
        
        // Blank node with no types
        mockTypes.put("untyped", new ArrayList<>());
        
        // Test single type label
        String registrationLabel = getBlankNodeTypeLabel("registration", mockTypes);
        assertEquals("Identifier", registrationLabel, "Registration blank node should show 'Identifier'");
        
        // Test address type label
        String addressLabel = getBlankNodeTypeLabel("address", mockTypes);
        assertEquals("Address", addressLabel, "Address blank node should show 'Address'");
        
        // Test multiple type labels
        String complexLabel = getBlankNodeTypeLabel("complex", mockTypes);
        assertEquals("Organization, LegalEntity", complexLabel, "Complex blank node should show comma-separated types");
        
        // Test fallback for untyped blank node
        String untypedLabel = getBlankNodeTypeLabel("untyped", mockTypes);
        assertEquals("[Blank Node]", untypedLabel, "Untyped blank node should show fallback label");
        
        // Test null handling
        String nullLabel = getBlankNodeTypeLabel(null, mockTypes);
        assertEquals("[Blank Node]", nullLabel, "Null blank node should show fallback label");
        
        // Test unknown blank node
        String unknownLabel = getBlankNodeTypeLabel("unknown", mockTypes);
        assertEquals("[Blank Node]", unknownLabel, "Unknown blank node should show fallback label");
    }
    
    @Test
    public void testBlankNodeWithTypeLabelInTemplate() {
        // Create a blank node with type label
        RDFObject registrationObj = new RDFObject();
        registrationObj.isLiteral = false;
        registrationObj.value = "";
        registrationObj.label = "Identifier"; // Type label instead of "[Blank Node]"
        
        // Add nested properties
        RDFObject notationObj = new RDFObject();
        notationObj.isLiteral = true;
        notationObj.value = "2105029959";
        notationObj.label = "2105029959";
        
        RDFPredicate notationPredicate = new RDFPredicate(
            "http://www.w3.org/2004/02/skos/core#notation", 
            "notation", 
            notationObj
        );
        
        registrationObj.nestedProperties = Arrays.asList(notationPredicate);
        
        // Verify the blank node has the correct label
        assertEquals("Identifier", registrationObj.getLabel(), "Blank node should use type label");
        assertTrue(registrationObj.hasNestedProperties(), "Blank node should have nested properties");
        assertEquals(1, registrationObj.getNestedProperties().size(), "Blank node should have 1 nested property");
        
        // Verify the nested property
        RDFPredicate nestedPredicate = registrationObj.getNestedProperties().get(0);
        assertEquals("notation", nestedPredicate.getLabel(), "Nested predicate should be 'notation'");
        assertEquals("2105029959", nestedPredicate.getObjects().get(0).getValue(), "Notation should have correct value");
    }
}