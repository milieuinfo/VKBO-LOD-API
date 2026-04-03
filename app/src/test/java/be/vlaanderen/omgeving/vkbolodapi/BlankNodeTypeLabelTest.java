package be.vlaanderen.omgeving.vkbolodapi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final String uri;
    private final String label;
    private final List<RDFObject> objects;

    public RDFPredicate(String uri, String label, RDFObject object) {
      this.uri = uri;
      this.label = label;
      this.objects = new ArrayList<>();
      this.objects.add(object);
    }

    public String getLabel() { return label; }
    public List<RDFObject> getObjects() { return objects; }
  }

  /**
   * Simulates type label extraction for blank nodes.
   */
  private String getBlankNodeTypeLabel(String blankNodeId, Map<String, List<String>> mockTypes) {
    if (blankNodeId == null) {
      return "[Blank Node]";
    }

    List<String> typeLabels = mockTypes.getOrDefault(blankNodeId, new ArrayList<>());

    if (!typeLabels.isEmpty()) {
      return String.join(", ", typeLabels);
    }

    return "[Blank Node]";
  }

  @Test
  public void testBlankNodeTypeLabelExtraction() {
    Map<String, List<String>> mockTypes = new HashMap<>();

    mockTypes.put("registration", List.of("Identifier"));
    mockTypes.put("address", List.of("Address"));
    mockTypes.put("complex", List.of("Organization", "LegalEntity"));
    mockTypes.put("untyped", new ArrayList<>());

    String registrationLabel = getBlankNodeTypeLabel("registration", mockTypes);
    assertEquals("Identifier", registrationLabel, "Registration blank node should show 'Identifier'");

    String addressLabel = getBlankNodeTypeLabel("address", mockTypes);
    assertEquals("Address", addressLabel, "Address blank node should show 'Address'");

    String complexLabel = getBlankNodeTypeLabel("complex", mockTypes);
    assertEquals("Organization, LegalEntity", complexLabel,
        "Complex blank node should show comma-separated types");

    String untypedLabel = getBlankNodeTypeLabel("untyped", mockTypes);
    assertEquals("[Blank Node]", untypedLabel, "Untyped blank node should show fallback label");

    String nullLabel = getBlankNodeTypeLabel(null, mockTypes);
    assertEquals("[Blank Node]", nullLabel, "Null blank node should show fallback label");

    String unknownLabel = getBlankNodeTypeLabel("unknown", mockTypes);
    assertEquals("[Blank Node]", unknownLabel, "Unknown blank node should show fallback label");
  }

  @Test
  public void testBlankNodeWithTypeLabelInTemplate() {
    RDFObject registrationObj = new RDFObject();
    registrationObj.isLiteral = false;
    registrationObj.value = "";
    registrationObj.label = "Identifier";

    RDFObject notationObj = new RDFObject();
    notationObj.isLiteral = true;
    notationObj.value = "2105029959";
    notationObj.label = "2105029959";

    RDFPredicate notationPredicate = new RDFPredicate(
        "http://www.w3.org/2004/02/skos/core#notation",
        "notation",
        notationObj);

    registrationObj.nestedProperties = List.of(notationPredicate);

    assertEquals("Identifier", registrationObj.getLabel(), "Blank node should use type label");
    assertTrue(registrationObj.hasNestedProperties(), "Blank node should have nested properties");
    assertEquals(1, registrationObj.getNestedProperties().size(),
        "Blank node should have 1 nested property");

    RDFPredicate nestedPredicate = registrationObj.getNestedProperties().getFirst();
    assertEquals("notation", nestedPredicate.getLabel(), "Nested predicate should be 'notation'");
    assertEquals("2105029959", nestedPredicate.getObjects().getFirst().getValue(),
        "Notation should have correct value");
  }
}
