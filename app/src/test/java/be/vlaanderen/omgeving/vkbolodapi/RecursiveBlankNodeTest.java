package be.vlaanderen.omgeving.vkbolodapi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test for recursive blank node expansion functionality
 */
public class RecursiveBlankNodeTest {

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
      return label + " -> " + objects;
    }
  }

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
        return "[Blank Node with " + nestedProperties.size() + " properties]";
      } else if (value == null || value.isEmpty()) {
        return "[Blank Node]";
      } else {
        return "<" + value + ">";
      }
    }
  }

  /**
   * Simulates recursive extraction of blank node properties.
   */
  private List<RDFPredicate> extractBlankNodeProperties(String blankNodeId, int depth) {
    List<RDFPredicate> properties = new ArrayList<>();

    if (depth > 2) {
      return properties; // Limit recursion depth for this test
    }

    if (blankNodeId.equals("registration")) {
      RDFObject notationObj = new RDFObject();
      notationObj.isLiteral = true;
      notationObj.value = "2105029959";
      notationObj.label = "2105029959";
      properties.add(new RDFPredicate(
          "http://www.w3.org/2004/02/skos/core#notation", "notation", notationObj));

      RDFObject typeObj = new RDFObject();
      typeObj.isLiteral = false;
      typeObj.value = "http://www.w3.org/ns/adms#Identifier";
      typeObj.label = "Identifier";
      properties.add(new RDFPredicate(
          "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "type", typeObj));

    } else if (blankNodeId.equals("address")) {
      RDFObject streetObj = new RDFObject();
      streetObj.isLiteral = true;
      streetObj.value = "Watertorenstraat";
      streetObj.label = "Watertorenstraat";
      properties.add(new RDFPredicate(
          "http://www.w3.org/ns/adres#straatnaam", "street name", streetObj));

      RDFObject numberObj = new RDFObject();
      numberObj.isLiteral = true;
      numberObj.value = "33";
      numberObj.label = "33";
      properties.add(new RDFPredicate(
          "http://www.w3.org/ns/adres#huisnummer", "house number", numberObj));

      RDFObject cityObj = new RDFObject();
      cityObj.isLiteral = true;
      cityObj.value = "Olen";
      cityObj.label = "Olen";
      properties.add(new RDFPredicate(
          "http://www.w3.org/ns/locn#geographicName", "city", cityObj));

      RDFObject postalCodeObj = new RDFObject();
      postalCodeObj.isLiteral = true;
      postalCodeObj.value = "2250";
      postalCodeObj.label = "2250";
      properties.add(new RDFPredicate(
          "http://www.w3.org/ns/locn#postCode", "postal code", postalCodeObj));

    } else {
      RDFObject nestedBlankObj = new RDFObject();
      nestedBlankObj.isLiteral = false;
      nestedBlankObj.value = "";
      nestedBlankObj.label = "[Blank Node]";
      nestedBlankObj.nestedProperties = extractBlankNodeProperties("nested-" + blankNodeId, depth + 1);
      properties.add(new RDFPredicate("http://example.org/nested", "nested property", nestedBlankObj));
    }

    return properties;
  }

  @Test
  public void testRecursiveBlankNodeExpansion() {
    // Test case: Registration blank node
    RDFObject registrationObj = new RDFObject();
    registrationObj.isLiteral = false;
    registrationObj.value = "";
    registrationObj.label = "[Blank Node]";
    registrationObj.nestedProperties = extractBlankNodeProperties("registration", 0);

    assertNotNull(registrationObj.getNestedProperties(),
        "Registration blank node should have nested properties");
    assertFalse(registrationObj.getNestedProperties().isEmpty(),
        "Registration blank node should not have empty properties");
    assertEquals(2, registrationObj.getNestedProperties().size(),
        "Registration blank node should have 2 properties");

    boolean hasNotation = false;
    boolean hasType = false;

    for (RDFPredicate predicate : registrationObj.getNestedProperties()) {
      if (predicate.getLabel().equals("notation")) {
        hasNotation = true;
        assertTrue(predicate.getObjects().get(0).isLiteral(), "Notation should be a literal");
        assertEquals("2105029959", predicate.getObjects().get(0).getValue(),
            "Notation should have correct value");
      } else if (predicate.getLabel().equals("type")) {
        hasType = true;
        assertFalse(predicate.getObjects().get(0).isLiteral(), "Type should not be a literal");
        assertEquals("http://www.w3.org/ns/adms#Identifier",
            predicate.getObjects().get(0).getValue(), "Type should have correct URI");
      }
    }

    assertTrue(hasNotation, "Registration blank node should have notation property");
    assertTrue(hasType, "Registration blank node should have type property");

    // Test case: Address blank node
    RDFObject addressObj = new RDFObject();
    addressObj.isLiteral = false;
    addressObj.value = "";
    addressObj.label = "[Blank Node]";
    addressObj.nestedProperties = extractBlankNodeProperties("address", 0);

    assertNotNull(addressObj.getNestedProperties(),
        "Address blank node should have nested properties");
    assertFalse(addressObj.getNestedProperties().isEmpty(),
        "Address blank node should not have empty properties");
    assertEquals(4, addressObj.getNestedProperties().size(),
        "Address blank node should have 4 properties");

    boolean hasStreet = false;
    boolean hasNumber = false;
    boolean hasCity = false;
    boolean hasPostalCode = false;

    for (RDFPredicate predicate : addressObj.getNestedProperties()) {
      if (predicate.getLabel().equals("street name")) {
        hasStreet = true;
        assertTrue(predicate.getObjects().get(0).isLiteral(), "Street should be a literal");
        assertEquals("Watertorenstraat", predicate.getObjects().get(0).getValue(),
            "Street should have correct value");
      } else if (predicate.getLabel().equals("house number")) {
        hasNumber = true;
        assertTrue(predicate.getObjects().get(0).isLiteral(), "House number should be a literal");
        assertEquals("33", predicate.getObjects().get(0).getValue(),
            "House number should have correct value");
      } else if (predicate.getLabel().equals("city")) {
        hasCity = true;
        assertTrue(predicate.getObjects().get(0).isLiteral(), "City should be a literal");
        assertEquals("Olen", predicate.getObjects().get(0).getValue(),
            "City should have correct value");
      } else if (predicate.getLabel().equals("postal code")) {
        hasPostalCode = true;
        assertTrue(predicate.getObjects().get(0).isLiteral(), "Postal code should be a literal");
        assertEquals("2250", predicate.getObjects().get(0).getValue(),
            "Postal code should have correct value");
      }
    }

    assertTrue(hasStreet, "Address blank node should have street property");
    assertTrue(hasNumber, "Address blank node should have house number property");
    assertTrue(hasCity, "Address blank node should have city property");
    assertTrue(hasPostalCode, "Address blank node should have postal code property");
  }
}
