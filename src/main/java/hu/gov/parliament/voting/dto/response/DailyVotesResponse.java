package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DailyVotesResponse(@JsonProperty("szavazasok") List<DailyVoteResponse> votes) {}
