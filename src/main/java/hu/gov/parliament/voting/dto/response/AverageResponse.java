package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AverageResponse(@JsonProperty("atlag") double average) {}
