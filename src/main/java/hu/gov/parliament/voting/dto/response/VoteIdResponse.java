package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoteIdResponse(@JsonProperty("szavazasId") String voteId) {}
