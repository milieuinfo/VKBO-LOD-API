package be.vlaanderen.omgeving.vkbolodapi;

import be.vlaanderen.omgeving.vkbolodapi.controller.OndernemingsController;
import be.vlaanderen.omgeving.vkbolodapi.service.OndernemingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OndernemingsControllerTestSimple {

    private MockMvc mockMvc;

    @Mock
    private OndernemingsService ondernemingsService;

    @InjectMocks
    private OndernemingsController ondernemingsController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(ondernemingsController).build();
    }

    @Test
    void testJsonResponse() throws Exception {
        String mockJson = "{"
                + "\"type\":\"FeatureCollection\","
                + "\"numberMatched\":1,"
                + "\"numberReturned\":1,"
                + "\"features\":[{"
                + "\"type\":\"Feature\","
                + "\"properties\":{"
                + "\"Ondernemingsnr\":\"0401574852\""
                + "}"
                + "}]"
                + "}";

        when(ondernemingsService.getJson("0401574852")).thenReturn(mockJson);

        mockMvc.perform(get("/id/organisatie/0401574852")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.features[0].properties.Ondernemingsnr").value("0401574852"));
    }

    @Test
    void testJsonLdResponse() throws Exception {
        String mockJsonLd = "{"
                + "\"@context\":{},"
                + "\"@id\":\"organisation:0401574852\","
                + "\"registratie\":{"
                + "\"ondermemingsnr\":\"0401574852\""
                + "}"
                + "}";

        when(ondernemingsService.getJsonLd("0401574852")).thenReturn(mockJsonLd);

        mockMvc.perform(get("/id/organisatie/0401574852")
                .accept("application/ld+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/ld+json"))
                .andExpect(jsonPath("registratie.ondermemingsnr").value("0401574852"));
    }

    @Test
    void testInternalServerError() {
        when(ondernemingsService.getJson("FAKE12345")).thenThrow(new RuntimeException("An error has occurred"));

        try {
            mockMvc.perform(get("/id/organisatie/FAKE12345")
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError());
        } catch (Exception e) {
            // Expected exception
            assertThat(e).hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void testUnsupportedAcceptHeader() throws Exception {
        mockMvc.perform(get("/id/organisatie/0401574852")
                .accept("application/xml"))
                .andExpect(status().isNotAcceptable());
    }
}