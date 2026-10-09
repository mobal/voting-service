package hu.gov.parliament.voting.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResultResponse(
    @JsonProperty("eredmeny") String result,
    @JsonProperty("kepviselokSzama") int representativeCount,
    @JsonProperty("igenekSzama") int yesCount,
    @JsonProperty("nemekSzama") int noCount,
    @JsonProperty("tartozkodasokSzama") int abstentionCount) {}
