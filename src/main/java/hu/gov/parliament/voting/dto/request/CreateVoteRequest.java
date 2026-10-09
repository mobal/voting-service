package hu.gov.parliament.voting.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateVoteRequest(
    @JsonProperty("idopont")
        @JsonDeserialize(using = IsoInstantDeserializer.class)
        @NotNull(message = "The timestamp is required.")
        Instant timestamp,
    @JsonProperty("targy") @NotBlank(message = "The subject is required.") String subject,
    @JsonProperty("tipus")
        @NotBlank(message = "The vote type is required.")
        @Pattern(regexp = "[jem]", message = "The vote type must be j, e, or m.")
        String type,
    @JsonProperty("eljaras")
        @Pattern(regexp = "[nske]", message = "The procedure must be n, s, k, or e.")
        String procedure,
    @JsonProperty("elnok") @NotBlank(message = "The chairperson is required.") String chairperson,
    @JsonProperty("szavazatok")
        @NotEmpty(message = "At least one vote is required.")
        @Size(max = 200, message = "At most 200 representatives may vote.")
        List<@NotNull(message = "Vote entries cannot be null.") @Valid VoteInput> votes) {}
