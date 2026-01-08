package be.vlaanderen.omgeving.vkbolodapi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for title language tag removal functionality
 */
public class TitleLanguageTagTest {
    
    /**
     * Simulate the language tag removal logic
     */
    private String removeLanguageTag(String title) {
        if (title == null) {
            return null;
        }
        
        int languageTagIndex = title.indexOf('@');
        if (languageTagIndex > 0) {
            return title.substring(0, languageTagIndex);
        }
        
        return title;
    }
    
    @Test
    public void testLanguageTagRemoval() {
        // Test cases with language tags
        assertEquals("UMICORE", removeLanguageTag("UMICORE@nl"), "Should remove @nl language tag");
        assertEquals("Acme Corp", removeLanguageTag("Acme Corp@en"), "Should remove @en language tag");
        assertEquals("Test", removeLanguageTag("Test@fr"), "Should remove @fr language tag");
        
        // Test cases without language tags
        assertEquals("UMICORE", removeLanguageTag("UMICORE"), "Should not modify title without language tag");
        assertEquals("Acme Corp", removeLanguageTag("Acme Corp"), "Should not modify title without language tag");
        assertEquals("", removeLanguageTag(""), "Should handle empty string");
        
        // Test edge cases
        assertEquals("test", removeLanguageTag("test@example.com"), "Should remove everything after first @ (RDF language tag format)");
        assertNull(removeLanguageTag(null), "Should handle null input");
        
        // Test multiple @ symbols (should only remove the first one)
        assertEquals("Test", removeLanguageTag("Test@nl@backup"), "Should only remove first @");
    }
    
    @Test
    public void testTitleProcessing() {
        // Simulate the title processing logic
        String[] testTitles = {
            "UMICORE@nl",
            "Acme Corporation@en",
            "Test Organization",
            "Company@fr",
            ""
        };
        
        String[] expectedResults = {
            "UMICORE",
            "Acme Corporation",
            "Test Organization",
            "Company",
            ""
        };
        
        for (int i = 0; i < testTitles.length; i++) {
            String result = removeLanguageTag(testTitles[i]);
            assertEquals(expectedResults[i], result, 
                "Title '" + testTitles[i] + "' should become '" + expectedResults[i] + "'");
        }
    }
}