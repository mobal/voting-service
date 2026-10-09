package hu.gov.parliament.voting.integration;

import static org.assertj.core.api.Assertions.assertThat;

import hu.gov.parliament.voting.dto.request.CreateVoteRequest;
import hu.gov.parliament.voting.dto.request.VoteInput;
import hu.gov.parliament.voting.dto.response.AverageResponse;
import hu.gov.parliament.voting.dto.response.DailyVoteResponse;
import hu.gov.parliament.voting.dto.response.DailyVotesResponse;
import hu.gov.parliament.voting.dto.response.ResultResponse;
import hu.gov.parliament.voting.dto.response.SpecialProcedureRow;
import hu.gov.parliament.voting.dto.response.SpecialProceduresResponse;
import hu.gov.parliament.voting.dto.response.VoteIdResponse;
import hu.gov.parliament.voting.dto.response.VoteResponse;
import hu.gov.parliament.voting.repository.VotingSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@DisplayName("Voting API integration")
class VotingApiIntegrationTest {

  @Autowired private RestTestClient client;
  @Autowired private VotingSessionRepository votingSessionRepository;

  @BeforeEach
  void cleanDatabase() {
    votingSessionRepository.deleteAll();
  }

  @Nested
  @DisplayName("POST /szavazasok/szavazas")
  class CreateVote {

    @Test
    @DisplayName("creates a vote and returns its identifier")
    void createsVote() {
      // Arrange
      CreateVoteRequest request =
          request(
              "2026-01-01T09:00:00Z",
              "j",
              "n",
              List.of(new VoteInput("R1", "i"), new VoteInput("R2", "n")));

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      VoteIdResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(VoteIdResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.voteId()).hasSize(36);
    }

    @Test
    @DisplayName("ignores unknown JSON properties")
    void ignoresUnknownJsonProperties() {
      // Arrange
      String requestBody =
          """
          {
            "idopont": "2026-01-01T09:00:00Z",
            "targy": "Subject",
            "tipus": "j",
            "elnok": "R1",
            "szavazatok": [{"kepviselo": "R1", "szavazat": "i", "extra": true}],
            "unexpected": "value"
          }
          """;

      // Act
      RestTestClient.ResponseSpec response =
          client
              .post()
              .uri("/szavazasok/szavazas")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .exchange();

      // Assert
      response.expectStatus().isOk();
    }

    @Test
    @DisplayName("describes the cause of a malformed timestamp")
    void describesMalformedTimestamp() {
      String requestBody =
          """
          {
            "idopont": "not-a-date",
            "targy": "Subject",
            "tipus": "j",
            "eljaras": "n",
            "elnok": "R1",
            "szavazatok": [{"kepviselo": "R1", "szavazat": "i"}]
          }
          """;

      RestTestClient.ResponseSpec response =
          client
              .post()
              .uri("/szavazasok/szavazas")
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .exchange();

      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getDetail()).contains("ISO 8601");
    }

    @Test
    @DisplayName("returns ProblemDetail when a required string is blank")
    void rejectsBlankSubject() {
      // Arrange
      CreateVoteRequest request =
          new CreateVoteRequest(
              Instant.parse("2026-01-01T09:00:00Z"),
              "   ",
              "j",
              "n",
              "R1",
              List.of(new VoteInput("R1", "i")));

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getTitle()).isEqualTo("Validation failed");
      assertThat(body.getProperties()).containsKey("errors");
      assertThat(body.getProperties().get("errors").toString()).contains("subject");
    }

    @Test
    @DisplayName("returns ProblemDetail when the vote list is empty")
    void rejectsEmptyVoteList() {
      // Arrange
      CreateVoteRequest request =
          new CreateVoteRequest(
              Instant.parse("2026-01-01T09:00:00Z"), "Subject", "j", "n", "R1", List.of());

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getTitle()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("rejects more than two hundred representative votes")
    void rejectsMoreThanTwoHundredVotes() {
      // Arrange
      List<VoteInput> votes =
          IntStream.rangeClosed(1, 201).mapToObj(index -> new VoteInput("R" + index, "i")).toList();
      CreateVoteRequest request = request("2026-01-01T09:00:00Z", "j", "n", votes);

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getTitle()).isEqualTo("Validation failed");
      assertThat(body.getProperties().get("errors").toString())
          .contains("At most 200 representatives may vote.");
    }

    @Test
    @DisplayName("creates a simple-majority vote without prior attendance")
    void createsSimpleMajorityWithoutPriorAttendance() {
      // Arrange
      CreateVoteRequest request =
          request("2026-01-01T10:00:00Z", "e", "n", List.of(new VoteInput("R1", "i")));

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      response.expectStatus().isOk();
    }

    @Test
    @DisplayName("returns ProblemDetail for a sub-second timestamp")
    void rejectsSubSecondTimestamp() {
      // Arrange
      CreateVoteRequest request =
          request("2026-01-01T09:00:00.123Z", "j", "n", List.of(new VoteInput("R1", "i")));

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getDetail()).contains("second precision");
    }

    @Test
    @DisplayName("returns ProblemDetail when the chairperson did not vote")
    void rejectsMissingChairpersonVote() {
      // Arrange
      CreateVoteRequest request =
          new CreateVoteRequest(
              Instant.parse("2026-01-01T09:00:00Z"),
              "Subject",
              "j",
              "n",
              "CHAIR",
              List.of(new VoteInput("R1", "i")));

      // Act
      RestTestClient.ResponseSpec response = postVote(request);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getTitle()).isEqualTo("Invalid request");
      assertThat(body.getDetail()).contains("chairperson");
    }

    @Test
    @DisplayName("returns ProblemDetail for a duplicate timestamp")
    void rejectsDuplicateTimestamp() {
      // Arrange
      CreateVoteRequest first =
          request("2026-01-01T09:00:00Z", "j", "n", List.of(new VoteInput("R1", "i")));
      CreateVoteRequest second =
          request("2026-01-01T09:00:00Z", "j", "n", List.of(new VoteInput("R1", "n")));
      createVote(first);

      // Act
      RestTestClient.ResponseSpec response = postVote(second);

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getDetail()).contains("already been registered");
    }
  }

  @Nested
  @DisplayName("GET /szavazasok/szavazat")
  class GetRepresentativeVote {

    @Test
    @DisplayName("returns the requested representative vote")
    void returnsRepresentativeVote() {
      // Arrange
      VoteIdResponse vote =
          createVote(
              request(
                  "2026-01-01T09:00:00Z",
                  "j",
                  "n",
                  List.of(new VoteInput("R1", "i"), new VoteInput("R2", "t"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/szavazat")
                          .queryParam("szavazas", vote.voteId())
                          .queryParam("kepviselo", "R2")
                          .build())
              .exchange();

      // Assert
      VoteResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(VoteResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.choice()).isEqualTo("t");
    }

    @Test
    @DisplayName("returns 404 when the representative did not vote")
    void returnsNotFoundForMissingRepresentativeVote() {
      // Arrange
      VoteIdResponse vote =
          createVote(request("2026-01-01T09:00:00Z", "j", "n", List.of(new VoteInput("R1", "i"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/szavazat")
                          .queryParam("szavazas", vote.voteId())
                          .queryParam("kepviselo", "R2")
                          .build())
              .exchange();

      // Assert
      response.expectStatus().isNotFound();
    }
  }

  @Nested
  @DisplayName("GET /szavazasok/eredmeny")
  class GetResult {

    @Test
    @DisplayName("keeps reports available when a simple-majority vote has no prior attendance")
    void reportsVoteWithoutPriorAttendance() {
      VoteIdResponse vote =
          createVote(request("2026-01-01T10:00:00Z", "e", "s", List.of(new VoteInput("R1", "i"))));

      ProblemDetail result =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/eredmeny")
                          .queryParam("szavazas", vote.voteId())
                          .build())
              .exchange()
              .expectStatus()
              .isEqualTo(409)
              .expectBody(ProblemDetail.class)
              .returnResult()
              .getResponseBody();
      assertThat(result).isNotNull();
      assertThat(result.getTitle()).isEqualTo("Result unavailable");

      DailyVotesResponse daily =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/napi-szavazasok")
                          .queryParam("nap", "2026-01-01")
                          .build())
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(DailyVotesResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(daily).isNotNull();
      assertThat(daily.votes()).hasSize(1);
      DailyVoteResponse dailyVote = daily.votes().getFirst();
      assertThat(dailyVote.result()).isNull();
      assertThat(dailyVote.representativeCount()).isNull();

      SpecialProceduresResponse statistics =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/kulonleges-eljarasok-szama")
                          .queryParam("kezdet", "2026-01-01T00:00:00Z")
                          .queryParam("veg", "2026-01-01T23:59:59Z")
                          .build())
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(SpecialProceduresResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(statistics).isNotNull();
      assertThat(statistics.votes())
          .contains(
              new SpecialProcedureRow("s", "F", 0),
              new SpecialProcedureRow("s", "U", 0),
              new SpecialProcedureRow("összes", "összes", 0));
    }

    @Test
    @DisplayName("calculates a simple-majority result through HTTP")
    void calculatesSimpleMajorityResult() {
      // Arrange
      createVote(
          request(
              "2026-01-01T09:00:00Z",
              "j",
              "n",
              List.of(
                  new VoteInput("R1", "i"), new VoteInput("R2", "i"), new VoteInput("R3", "i"))));
      VoteIdResponse simpleVote =
          createVote(
              request(
                  "2026-01-01T10:00:00Z",
                  "e",
                  "n",
                  List.of(new VoteInput("R1", "i"), new VoteInput("R2", "i"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/eredmeny")
                          .queryParam("szavazas", simpleVote.voteId())
                          .build())
              .exchange();

      // Assert
      ResultResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(ResultResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.result()).isEqualTo("F");
      assertThat(body.representativeCount()).isEqualTo(3);
      assertThat(body.yesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("returns ProblemDetail when the vote does not exist")
    void returnsProblemDetailWhenVoteDoesNotExist() {
      // Arrange
      String missingVoteId = "missing";

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/eredmeny")
                          .queryParam("szavazas", missingVoteId)
                          .build())
              .exchange();

      // Assert
      ProblemDetail body =
          response
              .expectStatus()
              .isNotFound()
              .expectBody(ProblemDetail.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.getStatus()).isEqualTo(404);
      assertThat(body.getTitle()).isEqualTo("Resource not found");
    }
  }

  @Nested
  @DisplayName("GET /szavazasok/napi-szavazasok")
  class GetDailyVotes {

    @Test
    @DisplayName("identifies the missing date parameter")
    void identifiesMissingDateParameter() {
      RestTestClient.ResponseSpec response =
          client.get().uri("/szavazasok/napi-szavazasok").exchange();

      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getDetail()).contains("nap");
    }

    @Test
    @DisplayName("assigns offset timestamps to their UTC calendar day")
    void assignsOffsetTimestampToUtcDay() {
      String requestBody =
          """
          {
            "idopont": "2026-01-01T00:30:00+02:00",
            "targy": "Offset timestamp",
            "tipus": "j",
            "eljaras": "n",
            "elnok": "R1",
            "szavazatok": [{"kepviselo": "R1", "szavazat": "i"}]
          }
          """;

      client
          .post()
          .uri("/szavazasok/szavazas")
          .contentType(MediaType.APPLICATION_JSON)
          .body(requestBody)
          .exchange()
          .expectStatus()
          .isOk();

      DailyVotesResponse utcDateResponse =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/napi-szavazasok")
                          .queryParam("nap", "2025-12-31")
                          .build())
              .exchange()
              .expectStatus()
              .isOk()
              .expectBody(DailyVotesResponse.class)
              .returnResult()
              .getResponseBody();

      assertThat(utcDateResponse).isNotNull();
      assertThat(utcDateResponse.votes()).hasSize(1);
      assertThat(utcDateResponse.votes().getFirst().timestamp())
          .isEqualTo(Instant.parse("2025-12-31T22:30:00Z"));
    }

    @Test
    @DisplayName("returns only votes from the requested UTC day")
    void returnsVotesFromRequestedDay() {
      // Arrange
      createVote(request("2026-01-01T09:00:00Z", "j", null, List.of(new VoteInput("R1", "i"))));
      createVote(request("2026-01-02T09:00:00Z", "j", "n", List.of(new VoteInput("R1", "i"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/napi-szavazasok")
                          .queryParam("nap", "2026-01-01")
                          .build())
              .exchange();

      // Assert
      DailyVotesResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(DailyVotesResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.votes()).hasSize(1);
      assertThat(body.votes().getFirst().timestamp())
          .isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
      assertThat(body.votes().getFirst().procedure()).isNull();
    }

    @Test
    @DisplayName("returns an empty list when the day has no votes")
    void returnsEmptyList() {
      // Arrange
      String day = "2026-01-01";

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder.path("/szavazasok/napi-szavazasok").queryParam("nap", day).build())
              .exchange();

      // Assert
      DailyVotesResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(DailyVotesResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.votes()).isEmpty();
    }
  }

  @Nested
  @DisplayName("GET /szavazasok/kepviselo-reszvetel-atlag")
  class GetAverageParticipation {

    @Test
    @DisplayName("rejects an inverted period")
    void rejectsInvertedPeriod() {
      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/kepviselo-reszvetel-atlag")
                          .queryParam("kezdet", "2026-02-01T00:00:00Z")
                          .queryParam("veg", "2026-01-01T00:00:00Z")
                          .build())
              .exchange();

      // Assert
      ProblemDetail body = expectBadRequest(response);
      assertThat(body.getTitle()).isEqualTo("Invalid request");
      assertThat(body.getDetail()).contains("start of the period cannot be later");
    }

    @Test
    @DisplayName("excludes attendance votes and returns the average")
    void returnsAverageParticipation() {
      // Arrange
      createVote(
          request(
              "2026-01-01T09:00:00Z",
              "j",
              "n",
              List.of(new VoteInput("R1", "i"), new VoteInput("R2", "i"))));
      createVote(
          request(
              "2026-01-01T10:00:00Z",
              "e",
              "n",
              List.of(new VoteInput("R1", "i"), new VoteInput("R2", "n"))));
      createVote(
          request(
              "2026-01-01T11:00:00Z",
              "m",
              "n",
              List.of(new VoteInput("R1", "i"), new VoteInput("R2", "t"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/kepviselo-reszvetel-atlag")
                          .queryParam("kezdet", "2026-01-01T00:00:00Z")
                          .queryParam("veg", "2026-01-01T23:59:59Z")
                          .build())
              .exchange();

      // Assert
      AverageResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(AverageResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.average()).isEqualTo(0.02);
    }
  }

  @Nested
  @DisplayName("GET /szavazasok/kulonleges-eljarasok-szama")
  class GetSpecialProcedureStatistics {

    @Test
    @DisplayName("returns special-procedure result buckets and totals")
    void returnsSpecialProcedureStatistics() {
      // Arrange
      createVote(
          request(
              "2026-01-01T09:00:00Z",
              "j",
              "n",
              List.of(
                  new VoteInput("R1", "i"), new VoteInput("R2", "i"), new VoteInput("R3", "i"))));
      createVote(
          request(
              "2026-01-01T10:00:00Z",
              "e",
              "s",
              List.of(new VoteInput("R1", "i"), new VoteInput("R2", "i"))));
      createVote(
          request(
              "2026-01-01T11:00:00Z",
              "e",
              "k",
              List.of(
                  new VoteInput("R1", "i"), new VoteInput("R2", "n"), new VoteInput("R3", "n"))));

      // Act
      RestTestClient.ResponseSpec response =
          client
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/szavazasok/kulonleges-eljarasok-szama")
                          .queryParam("kezdet", "2026-01-01T00:00:00Z")
                          .queryParam("veg", "2026-01-01T23:59:59Z")
                          .build())
              .exchange();

      // Assert
      SpecialProceduresResponse body =
          response
              .expectStatus()
              .isOk()
              .expectBody(SpecialProceduresResponse.class)
              .returnResult()
              .getResponseBody();
      assertThat(body).isNotNull();
      assertThat(body.votes())
          .contains(
              new SpecialProcedureRow("s", "F", 1),
              new SpecialProcedureRow("s", "U", 0),
              new SpecialProcedureRow("k", "F", 0),
              new SpecialProcedureRow("k", "U", 1),
              new SpecialProcedureRow("e", "F", 0),
              new SpecialProcedureRow("e", "U", 0),
              new SpecialProcedureRow("összes", "F", 1),
              new SpecialProcedureRow("összes", "U", 1),
              new SpecialProcedureRow("összes", "összes", 2));
    }
  }

  private RestTestClient.ResponseSpec postVote(CreateVoteRequest request) {
    return client
        .post()
        .uri("/szavazasok/szavazas")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange();
  }

  private ProblemDetail expectBadRequest(RestTestClient.ResponseSpec response) {
    ProblemDetail body =
        response
            .expectStatus()
            .isBadRequest()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    return body;
  }

  private VoteIdResponse createVote(CreateVoteRequest request) {
    VoteIdResponse response =
        postVote(request)
            .expectStatus()
            .isOk()
            .expectBody(VoteIdResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(response).isNotNull();
    return response;
  }

  private CreateVoteRequest request(
      String timestamp, String type, String procedure, List<VoteInput> votes) {
    return new CreateVoteRequest(
        Instant.parse(timestamp), "Integration test vote", type, procedure, "R1", votes);
  }
}
