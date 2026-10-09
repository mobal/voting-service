package hu.gov.parliament.voting.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VoteInput(
    @JsonProperty("kepviselo") @NotBlank(message = "The representative code is required.")
        String representativeCode,
    @JsonProperty("szavazat")
        @NotBlank(message = "The vote is required.")
        @Pattern(regexp = "[int]", message = "The vote must be i, n, or t.")
        String choice) {}
