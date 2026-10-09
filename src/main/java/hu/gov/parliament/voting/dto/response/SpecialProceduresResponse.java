package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SpecialProceduresResponse(
    @JsonProperty("szavazasok") List<SpecialProcedureRow> votes) {}
