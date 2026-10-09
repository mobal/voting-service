package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import hu.gov.parliament.voting.dto.request.VoteInput;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailyVoteResponse(
    @JsonProperty("idopont") Instant timestamp,
    @JsonProperty("targy") String subject,
    @JsonProperty("tipus") String type,
    @JsonProperty("eljaras") String procedure,
    @JsonProperty("elnok") String chairperson,
    @JsonProperty("eredmeny") String result,
    @JsonProperty("kepviselokSzama") Integer representativeCount,
    @JsonProperty("szavazatok") List<VoteInput> votes) {}
