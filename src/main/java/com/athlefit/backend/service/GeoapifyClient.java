package com.athlefit.backend.service;

import com.athlefit.backend.exception.ExternalServiceException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GeoapifyClient {

    private final RestClient restClient;
    private final String apiKey;

    @Autowired
    public GeoapifyClient(@Value("${athlefit.geoapify.api-key:}") String apiKey) {
        this(RestClient.create("https://api.geoapify.com"), apiKey);
    }

    GeoapifyClient(RestClient restClient, String apiKey) {
        this.restClient = restClient;
        this.apiKey = apiKey;
    }

    public List<GeoapifyPlace> search(String query) {
        requireConfigured();
        try {
            FeatureCollection response = restClient.get()
                    .uri(builder -> builder
                            .path("/v1/geocode/search")
                            .queryParam("text", query)
                            .queryParam("filter", "countrycode:id")
                            .queryParam("lang", "id")
                            .queryParam("limit", 10)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(FeatureCollection.class);
            return response == null || response.features() == null
                    ? List.of()
                    : response.features().stream().map(this::toPlace).toList();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public GeoapifyPlace getPlace(String placeId) {
        requireConfigured();
        try {
            FeatureCollection response = restClient.get()
                    .uri(builder -> builder
                            .path("/v2/place-details")
                            .queryParam("id", placeId)
                            .queryParam("features", "details")
                            .queryParam("lang", "id")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .body(FeatureCollection.class);
            if (response == null || response.features() == null
                    || response.features().isEmpty()) {
                throw new ExternalServiceException("Geoapify returned an empty place");
            }
            return toPlace(response.features().get(0));
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private GeoapifyPlace toPlace(Feature feature) {
        Properties properties = feature.properties();
        if (properties == null) {
            throw new ExternalServiceException("Geoapify returned invalid place data");
        }
        Raw raw = properties.datasource() == null ? null : properties.datasource().raw();
        return new GeoapifyPlace(
                properties.placeId(),
                firstPresent(properties.name(), raw == null ? null : raw.name()),
                properties.formatted(),
                properties.lat(),
                properties.lon(),
                properties.categories() == null
                        ? Set.of()
                        : Set.copyOf(properties.categories()),
                splitSports(raw == null ? null : raw.sport()),
                firstPresent(
                        properties.openingHours(),
                        raw == null ? null : raw.openingHours()
                ),
                firstPresent(
                        properties.contact() == null ? null : properties.contact().phone(),
                        properties.phone(),
                        raw == null ? null : raw.contactPhone(),
                        raw == null ? null : raw.phone()
                )
        );
    }

    private Set<String> splitSports(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> sports = new LinkedHashSet<>();
        Arrays.stream(value.split("[;,]"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(sports::add);
        return Set.copyOf(sports);
    }

    private String firstPresent(String... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException("GEOAPIFY_API_KEY is not configured");
        }
    }

    private ExternalServiceException unavailable(RestClientException exception) {
        return new ExternalServiceException("Geoapify is currently unavailable", exception);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FeatureCollection(List<Feature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Feature(Properties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Properties(
            @JsonProperty("place_id") String placeId,
            String name,
            String formatted,
            BigDecimal lat,
            BigDecimal lon,
            List<String> categories,
            @JsonProperty("opening_hours") String openingHours,
            Contact contact,
            String phone,
            Datasource datasource
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Contact(String phone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Datasource(Raw raw) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Raw(
            String name,
            String sport,
            String phone,
            @JsonProperty("contact:phone") String contactPhone,
            @JsonProperty("opening_hours") String openingHours
    ) {
    }
}
