package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpecialProcedureRow(
    @JsonProperty("eljaras") String procedure,
    @JsonProperty("eredmeny") String result,
    @JsonProperty("szam") long count) {}
