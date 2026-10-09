package hu.gov.parliament.voting.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hu.gov.parliament.voting.dto.request.CreateVoteRequest;
import hu.gov.parliament.voting.dto.request.VoteInput;
import hu.gov.parliament.voting.dto.response.ResultResponse;
import hu.gov.parliament.voting.dto.response.SpecialProcedureRow;
import hu.gov.parliament.voting.exception.BadRequestException;
import hu.gov.parliament.voting.exception.NotFoundException;
import hu.gov.parliament.voting.exception.VotingResultUnavailableException;
import hu.gov.parliament.voting.model.BallotEntity;
import hu.gov.parliament.voting.model.VotingSessionEntity;
import hu.gov.parliament.voting.repository.BallotRepository;
import hu.gov.parliament.voting.repository.VotingSessionRepository;
import hu.gov.parliament.voting.service.VotingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VotingService")
class VotingServiceTest {

  @Mock private VotingSessionRepository votingSessionRepository;
  @Mock private BallotRepository ballotRepository;

  private VotingService votingService;

  @BeforeEach
  void setUp() {
    votingService = new VotingService(votingSessionRepository, ballotRepository);
  }

  @Nested
  @DisplayName("createVote")
  class CreateVote {

    @Test
    @DisplayName("saves a valid vote")
    void savesValidVote() {
      // Arrange
      Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
      CreateVoteRequest request = request(timestamp, "j", "n", List.of(vote("R1", "i")));
      when(votingSessionRepository.existsByTimestamp(timestamp)).thenReturn(false);

      // Act
      var response = votingService.createVote(request);

      // Assert
      ArgumentCaptor<VotingSessionEntity> captor =
          ArgumentCaptor.forClass(VotingSessionEntity.class);
      verify(votingSessionRepository).saveAndFlush(captor.capture());
      VotingSessionEntity saved = captor.getValue();
      assertThat(response.voteId())
          .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
      assertThat(saved.getId()).isEqualTo(response.voteId());
      assertThat(saved.getBallots()).hasSize(1);
      assertThat(saved.getBallots().getFirst().getRepresentativeCode()).isEqualTo("R1");
    }

    @Test
    @DisplayName("saves a simple-majority vote without prior attendance")
    void savesSimpleMajorityWithoutPriorAttendance() {
      // Arrange
      Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
      CreateVoteRequest request = request(timestamp, "e", "n", List.of(vote("R1", "i")));
      when(votingSessionRepository.existsByTimestamp(timestamp)).thenReturn(false);

      // Act
      votingService.createVote(request);

      // Assert
      verify(votingSessionRepository).saveAndFlush(any(VotingSessionEntity.class));
      verify(votingSessionRepository, never())
          .findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(any(), any());
    }

    @Test
    @DisplayName("rejects timestamps with sub-second precision")
    void rejectsSubSecondTimestamp() {
      // Arrange
      CreateVoteRequest request =
          request(Instant.parse("2026-01-01T10:00:00.123Z"), "j", "n", List.of(vote("R1", "i")));

      // Act / Assert
      assertThatThrownBy(() -> votingService.createVote(request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("second precision");
      verify(votingSessionRepository, never()).existsByTimestamp(any());
      verify(votingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rejects a duplicate timestamp")
    void rejectsDuplicateTimestamp() {
      // Arrange
      Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
      CreateVoteRequest request = request(timestamp, "j", "n", List.of(vote("R1", "i")));
      when(votingSessionRepository.existsByTimestamp(timestamp)).thenReturn(true);

      // Act / Assert
      assertThatThrownBy(() -> votingService.createVote(request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("already been registered");
      verify(votingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rejects duplicate representative votes")
    void rejectsDuplicateRepresentativeVotes() {
      // Arrange
      Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
      CreateVoteRequest request =
          request(timestamp, "j", "n", List.of(vote("R1", "i"), vote("R1", "n")));
      when(votingSessionRepository.existsByTimestamp(timestamp)).thenReturn(false);

      // Act / Assert
      assertThatThrownBy(() -> votingService.createVote(request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("only once");
      verify(votingSessionRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("rejects a vote when the chairperson did not vote")
    void rejectsMissingChairpersonVote() {
      // Arrange
      Instant timestamp = Instant.parse("2026-01-01T10:00:00Z");
      CreateVoteRequest request =
          new CreateVoteRequest(timestamp, "Subject", "j", "n", "CHAIR", List.of(vote("R1", "i")));
      when(votingSessionRepository.existsByTimestamp(timestamp)).thenReturn(false);

      // Act / Assert
      assertThatThrownBy(() -> votingService.createVote(request))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("chairperson");
      verify(votingSessionRepository, never()).saveAndFlush(any());
    }
  }

  @Nested
  @DisplayName("getRepresentativeVote")
  class GetRepresentativeVote {

    @Test
    @DisplayName("returns the representative vote when it exists")
    void returnsRepresentativeVote() {
      // Arrange
      BallotEntity ballot = new BallotEntity("R1", "i");
      when(ballotRepository.findByVotingSessionIdAndRepresentativeCode("vote-1", "R1"))
          .thenReturn(Optional.of(ballot));

      // Act
      var response = votingService.getRepresentativeVote("vote-1", "R1");

      // Assert
      assertThat(response.choice()).isEqualTo("i");
    }

    @Test
    @DisplayName("throws not found when the representative vote does not exist")
    void throwsNotFoundForMissingRepresentativeVote() {
      // Arrange
      when(ballotRepository.findByVotingSessionIdAndRepresentativeCode("vote-1", "R1"))
          .thenReturn(Optional.empty());

      // Act / Assert
      assertThatThrownBy(() -> votingService.getRepresentativeVote("vote-1", "R1"))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getResult")
  class GetResult {

    @Test
    @DisplayName("reports an unavailable result when prior attendance is missing")
    void reportsUnavailableResultWithoutPriorAttendance() {
      VotingSessionEntity session = session("vote-1", "e", "s", "2026-01-01T10:00:00Z");
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));

      assertThatThrownBy(() -> votingService.getResult("vote-1"))
          .isInstanceOf(VotingResultUnavailableException.class)
          .hasMessageContaining("no prior attendance session");
    }

    @Test
    @DisplayName("always accepts an attendance vote")
    void acceptsAttendanceVote() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "j", "n", "2026-01-01T10:00:00Z");
      session.addBallot(new BallotEntity("R1", "i"));
      session.addBallot(new BallotEntity("R2", "n"));
      session.addBallot(new BallotEntity("R3", "t"));
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));

      // Act
      ResultResponse result = votingService.getResult("vote-1");

      // Assert
      assertThat(result.result()).isEqualTo("F");
      assertThat(result.representativeCount()).isEqualTo(3);
      assertThat(result.yesCount()).isEqualTo(1);
      assertThat(result.noCount()).isEqualTo(1);
      assertThat(result.abstentionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "accepts a simple-majority vote when more than half of present representatives voted yes")
    void acceptsSimpleMajority() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "e", "n", "2026-01-01T10:00:00Z");
      session.addBallot(new BallotEntity("R1", "i"));
      session.addBallot(new BallotEntity("R2", "i"));
      session.addBallot(new BallotEntity("R3", "n"));
      VotingSessionEntity attendance = attendanceSession(3, "2026-01-01T09:00:00Z");
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));
      when(votingSessionRepository.findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(
              "j", session.getTimestamp()))
          .thenReturn(Optional.of(attendance));

      // Act
      ResultResponse result = votingService.getResult("vote-1");

      // Assert
      assertThat(result.result()).isEqualTo("F");
      assertThat(result.representativeCount()).isEqualTo(3);
      assertThat(result.yesCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejects a simple-majority vote at exactly half of present representatives")
    void rejectsSimpleMajorityAtExactlyHalf() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "e", "n", "2026-01-01T10:00:00Z");
      session.addBallot(new BallotEntity("R1", "i"));
      session.addBallot(new BallotEntity("R2", "i"));
      VotingSessionEntity attendance = attendanceSession(4, "2026-01-01T09:00:00Z");
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));
      when(votingSessionRepository.findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(
              "j", session.getTimestamp()))
          .thenReturn(Optional.of(attendance));

      // Act
      ResultResponse result = votingService.getResult("vote-1");

      // Assert
      assertThat(result.result()).isEqualTo("U");
      assertThat(result.representativeCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("rejects a qualified-majority vote with one hundred yes votes")
    void rejectsQualifiedMajorityWithOneHundredYesVotes() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "m", "n", "2026-01-01T10:00:00Z");
      addYesVotes(session, 100);
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));

      // Act
      ResultResponse result = votingService.getResult("vote-1");

      // Assert
      assertThat(result.result()).isEqualTo("U");
      assertThat(result.yesCount()).isEqualTo(100);
      assertThat(result.representativeCount()).isEqualTo(200);
    }

    @Test
    @DisplayName("accepts a qualified-majority vote with one hundred and one yes votes")
    void acceptsQualifiedMajorityWithOneHundredAndOneYesVotes() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "m", "n", "2026-01-01T10:00:00Z");
      addYesVotes(session, 101);
      when(votingSessionRepository.findById("vote-1")).thenReturn(Optional.of(session));

      // Act
      ResultResponse result = votingService.getResult("vote-1");

      // Assert
      assertThat(result.result()).isEqualTo("F");
      assertThat(result.yesCount()).isEqualTo(101);
      assertThat(result.representativeCount()).isEqualTo(200);
    }

    @Test
    @DisplayName("throws not found when the vote does not exist")
    void throwsNotFoundWhenVoteDoesNotExist() {
      // Arrange
      when(votingSessionRepository.findById("missing")).thenReturn(Optional.empty());

      // Act / Assert
      assertThatThrownBy(() -> votingService.getResult("missing"))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getDailyVotes")
  class GetDailyVotes {

    @Test
    @DisplayName("returns votes from the requested UTC day")
    void returnsVotesFromRequestedDay() {
      // Arrange
      VotingSessionEntity session = session("vote-1", "j", null, "2026-01-01T10:00:00Z");
      session.addBallot(new BallotEntity("R1", "i"));
      Instant from = Instant.parse("2026-01-01T00:00:00Z");
      Instant to = Instant.parse("2026-01-02T00:00:00Z");
      when(votingSessionRepository
              .findAllByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(from, to))
          .thenReturn(List.of(session));

      // Act
      var response = votingService.getDailyVotes(LocalDate.parse("2026-01-01"));

      // Assert
      assertThat(response.votes()).hasSize(1);
      assertThat(response.votes().getFirst().subject()).isEqualTo("Subject");
      assertThat(response.votes().getFirst().procedure()).isNull();
      assertThat(response.votes().getFirst().result()).isEqualTo("F");
    }

    @Test
    @DisplayName("includes a vote without prior attendance and leaves its result unavailable")
    void includesVoteWithoutPriorAttendance() {
      VotingSessionEntity session = session("vote-1", "e", "s", "2026-01-01T10:00:00Z");
      Instant from = Instant.parse("2026-01-01T00:00:00Z");
      Instant to = Instant.parse("2026-01-02T00:00:00Z");
      when(votingSessionRepository
              .findAllByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(from, to))
          .thenReturn(List.of(session));

      var response = votingService.getDailyVotes(LocalDate.parse("2026-01-01"));

      assertThat(response.votes()).hasSize(1);
      assertThat(response.votes().getFirst().result()).isNull();
      assertThat(response.votes().getFirst().representativeCount()).isNull();
    }

    @Test
    @DisplayName("returns an empty list when no vote exists on the requested day")
    void returnsEmptyList() {
      // Arrange
      Instant from = Instant.parse("2026-01-01T00:00:00Z");
      Instant to = Instant.parse("2026-01-02T00:00:00Z");
      when(votingSessionRepository
              .findAllByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(from, to))
          .thenReturn(List.of());

      // Act
      var response = votingService.getDailyVotes(LocalDate.parse("2026-01-01"));

      // Assert
      assertThat(response.votes()).isEmpty();
    }
  }

  @Nested
  @DisplayName("statistics")
  class Statistics {

    @Test
    @DisplayName("calculates average participation with two-decimal precision")
    void calculatesAverageParticipation() {
      // Arrange
      Instant start = Instant.parse("2026-01-01T00:00:00Z");
      Instant end = Instant.parse("2026-01-31T23:59:59Z");
      when(votingSessionRepository.countParticipationInNonAttendanceVotes(start, end))
          .thenReturn(850L);

      // Act
      var response = votingService.getAverageRepresentativeParticipation(start, end);

      // Assert
      assertThat(response.average()).isEqualTo(4.25);
    }

    @Test
    @DisplayName("rounds an exact half up for odd participation counts")
    void roundsHalfUp() {
      Instant start = Instant.parse("2026-01-01T00:00:00Z");
      Instant end = Instant.parse("2026-01-31T23:59:59Z");
      when(votingSessionRepository.countParticipationInNonAttendanceVotes(start, end))
          .thenReturn(29L);

      var response = votingService.getAverageRepresentativeParticipation(start, end);

      assertThat(response.average()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("rejects an inverted period")
    void rejectsInvertedPeriod() {
      // Arrange
      Instant start = Instant.parse("2026-02-01T00:00:00Z");
      Instant end = Instant.parse("2026-01-01T00:00:00Z");

      // Act / Assert
      assertThatThrownBy(() -> votingService.getAverageRepresentativeParticipation(start, end))
          .isInstanceOf(BadRequestException.class)
          .hasMessageContaining("start of the period cannot be later");
      verify(votingSessionRepository, never()).countParticipationInNonAttendanceVotes(any(), any());
    }

    @Test
    @DisplayName("returns all special-procedure result buckets and totals")
    void returnsSpecialProcedureStatistics() {
      // Arrange
      Instant start = Instant.parse("2026-01-01T00:00:00Z");
      Instant end = Instant.parse("2026-01-31T23:59:59Z");
      VotingSessionEntity accepted = session("vote-1", "e", "s", "2026-01-01T10:00:00Z");
      accepted.addBallot(new BallotEntity("R1", "i"));
      accepted.addBallot(new BallotEntity("R2", "i"));
      VotingSessionEntity rejected = session("vote-2", "e", "k", "2026-01-01T11:00:00Z");
      rejected.addBallot(new BallotEntity("R1", "i"));
      VotingSessionEntity normal = session("vote-3", "j", "n", "2026-01-01T12:00:00Z");
      normal.addBallot(new BallotEntity("R1", "i"));
      VotingSessionEntity unavailable = session("vote-4", "e", "e", "2026-01-01T13:00:00Z");
      VotingSessionEntity attendance = attendanceSession(3, "2026-01-01T09:00:00Z");

      when(votingSessionRepository.findAllByTimestampBetweenOrderByTimestampAsc(start, end))
          .thenReturn(List.of(accepted, rejected, normal, unavailable));
      when(votingSessionRepository.findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(
              eq("j"), any(Instant.class)))
          .thenAnswer(
              invocation ->
                  invocation.getArgument(1, Instant.class).equals(unavailable.getTimestamp())
                      ? Optional.empty()
                      : Optional.of(attendance));

      // Act
      var response = votingService.getSpecialProcedureStatistics(start, end);

      // Assert
      assertThat(response.votes())
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

  private CreateVoteRequest request(
      Instant timestamp, String type, String procedure, List<VoteInput> votes) {
    return new CreateVoteRequest(timestamp, "Subject", type, procedure, "R1", votes);
  }

  private VoteInput vote(String representative, String choice) {
    return new VoteInput(representative, choice);
  }

  private VotingSessionEntity attendanceSession(int representativeCount, String timestamp) {
    VotingSessionEntity session = session("attendance", "j", "n", timestamp);
    for (int index = 1; index <= representativeCount; index++) {
      session.addBallot(new BallotEntity("R" + index, "i"));
    }
    return session;
  }

  private VotingSessionEntity session(String id, String type, String procedure, String timestamp) {
    return new VotingSessionEntity(id, Instant.parse(timestamp), "Subject", type, procedure, "R1");
  }

  private void addYesVotes(VotingSessionEntity session, int count) {
    for (int index = 1; index <= count; index++) {
      session.addBallot(new BallotEntity("R" + index, "i"));
    }
  }
}
