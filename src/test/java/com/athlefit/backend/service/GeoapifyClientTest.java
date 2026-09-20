package com.athlefit.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeoapifyClientTest {

    @Test
    void mapsGeoapifyPlaceDetails() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeoapifyClient client = new GeoapifyClient(builder.build(), "test-key");
        server.expect(requestTo(
                        "https://api.geoapify.com/v2/place-details?id=place-id&features=details&lang=id&apiKey=test-key"
                ))
                .andRespond(withSuccess("""
                        {
                          "features": [{
                            "properties": {
                              "place_id": "place-id",
                              "name": "Badminton Arena",
                              "formatted": "Jakarta",
                              "lat": -6.2,
                              "lon": 106.8,
                              "categories": ["sport.sports_centre"],
                              "opening_hours": "Mo-Su 08:00-22:00",
                              "contact": {"phone": "021-123"},
                              "datasource": {"raw": {"sport": "badminton;futsal"}}
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoapifyPlace place = client.getPlace("place-id");

        assertEquals("Badminton Arena", place.name());
        assertEquals(new BigDecimal("-6.2"), place.latitude());
        assertEquals(Set.of("badminton", "futsal"), place.taggedSports());
        assertEquals("021-123", place.phone());
        server.verify();
    }
}
