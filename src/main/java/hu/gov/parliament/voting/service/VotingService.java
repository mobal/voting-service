package hu.gov.parliament.voting.service;

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
import hu.gov.parliament.voting.exception.BadRequestException;
import hu.gov.parliament.voting.exception.NotFoundException;
import hu.gov.parliament.voting.exception.VotingResultUnavailableException;
import hu.gov.parliament.voting.model.BallotEntity;
import hu.gov.parliament.voting.model.VotingSessionEntity;
import hu.gov.parliament.voting.repository.BallotRepository;
import hu.gov.parliament.voting.repository.VotingSessionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class VotingService {

  private static final int TOTAL_REPRESENTATIVES = 200;

  // API vote types: j = attendance, e = simple majority, m = qualified majority.
  private static final String ATTENDANCE_TYPE = "j";
  private static final String SIMPLE_MAJORITY_TYPE = "e";
  private static final String QUALIFIED_MAJORITY_TYPE = "m";

  // API ballot choices: i = yes, n = no, t = abstention.
  private static final String YES = "i";
  private static final String NO = "n";
  private static final String ABSTENTION = "t";

  // API result codes: F = accepted, U = rejected.
  private static final String ACCEPTED = "F";
  private static final String REJECTED = "U";
  private static final String TOTAL_LABEL = "összes";
  private static final List<String> SPECIAL_PROCEDURES = List.of("s", "k", "e");

  private final VotingSessionRepository votingSessionRepository;
  private final BallotRepository ballotRepository;

  @Transactional
  public VoteIdResponse createVote(CreateVoteRequest request) {
    validateBusinessRules(request);

    String voteId = UUID.randomUUID().toString();
    VotingSessionEntity votingSession =
        new VotingSessionEntity(
            voteId,
            request.timestamp(),
            request.subject(),
            request.type(),
            request.procedure(),
            request.chairperson());

    for (VoteInput vote : request.votes()) {
      votingSession.addBallot(new BallotEntity(vote.representativeCode(), vote.choice()));
    }

    votingSessionRepository.saveAndFlush(votingSession);
    log.info(
        "Registered voting session {} (type {}, {} ballots)",
        voteId,
        request.type(),
        request.votes().size());
    return new VoteIdResponse(voteId);
  }

  public VoteResponse getRepresentativeVote(String voteId, String representativeCode) {
    return ballotRepository
        .findByVotingSessionIdAndRepresentativeCode(voteId, representativeCode)
        .map(ballot -> new VoteResponse(ballot.getChoice()))
        .orElseThrow(
            () ->
                new NotFoundException(
                    "The vote does not exist, or the representative did not cast a vote."));
  }

  public ResultResponse getResult(String voteId) {
    VotingSessionEntity votingSession =
        votingSessionRepository
            .findById(voteId)
            .orElseThrow(
                () -> new NotFoundException("No vote exists with the provided identifier."));
    return calculateResult(votingSession);
  }

  public DailyVotesResponse getDailyVotes(LocalDate date) {
    // Use a UTC half-open interval so midnight belongs to exactly one date.
    Instant fromInclusive = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant toExclusive = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

    List<DailyVoteResponse> dailyVotes =
        votingSessionRepository
            .findAllByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
                fromInclusive, toExclusive)
            .stream()
            .map(this::toDailyVoteResponse)
            .toList();

    return new DailyVotesResponse(dailyVotes);
  }

  public AverageResponse getAverageRepresentativeParticipation(Instant start, Instant end) {
    validatePeriod(start, end);
    long participationCount =
        votingSessionRepository.countParticipationInNonAttendanceVotes(start, end);

    double average =
        BigDecimal.valueOf(participationCount)
            .divide(BigDecimal.valueOf(TOTAL_REPRESENTATIVES), 2, RoundingMode.HALF_UP)
            .doubleValue();

    return new AverageResponse(average);
  }

  public SpecialProceduresResponse getSpecialProcedureStatistics(Instant start, Instant end) {
    validatePeriod(start, end);
    // Initialize every supported procedure so the response includes zero-count rows.
    Map<String, Map<String, Long>> countsByProcedure = new HashMap<>();
    SPECIAL_PROCEDURES.forEach(procedure -> countsByProcedure.put(procedure, newResultCountMap()));

    for (VotingSessionEntity votingSession :
        votingSessionRepository.findAllByTimestampBetweenOrderByTimestampAsc(start, end)) {
      Map<String, Long> resultCounts = countsByProcedure.get(votingSession.getProcedure());
      if (resultCounts != null) {
        calculateResultIfAvailable(votingSession)
            .ifPresent(result -> incrementResultCount(resultCounts, result.result()));
      }
    }

    List<SpecialProcedureRow> rows = new ArrayList<>();
    long totalAccepted = 0;
    long totalRejected = 0;

    for (String procedure : SPECIAL_PROCEDURES) {
      long acceptedCount = countsByProcedure.get(procedure).get(ACCEPTED);
      long rejectedCount = countsByProcedure.get(procedure).get(REJECTED);
      rows.add(new SpecialProcedureRow(procedure, ACCEPTED, acceptedCount));
      rows.add(new SpecialProcedureRow(procedure, REJECTED, rejectedCount));
      totalAccepted += acceptedCount;
      totalRejected += rejectedCount;
    }

    rows.add(new SpecialProcedureRow(TOTAL_LABEL, ACCEPTED, totalAccepted));
    rows.add(new SpecialProcedureRow(TOTAL_LABEL, REJECTED, totalRejected));
    rows.add(new SpecialProcedureRow(TOTAL_LABEL, TOTAL_LABEL, totalAccepted + totalRejected));

    return new SpecialProceduresResponse(rows);
  }

  private void validateBusinessRules(CreateVoteRequest request) {
    validateTimestampPrecision(request.timestamp());
    validateTimestampIsUnique(request.timestamp());
    validateBallots(request);
  }

  private void validateTimestampPrecision(Instant timestamp) {
    if (timestamp.getNano() != 0) {
      throw new BadRequestException("The timestamp must have second precision.");
    }
  }

  private void validateTimestampIsUnique(Instant timestamp) {
    if (votingSessionRepository.existsByTimestamp(timestamp)) {
      throw new BadRequestException("A vote has already been registered for this timestamp.");
    }
  }

  private void validateBallots(CreateVoteRequest request) {
    Set<String> representativeCodes = new HashSet<>();
    boolean chairpersonVoted = false;

    for (VoteInput vote : request.votes()) {
      if (!representativeCodes.add(vote.representativeCode())) {
        throw new BadRequestException(
            "A representative may vote only once in the same vote: " + vote.representativeCode());
      }
      if (request.chairperson().equals(vote.representativeCode())) {
        chairpersonVoted = true;
      }
    }

    if (!chairpersonVoted) {
      throw new BadRequestException("The chairperson must also cast a vote.");
    }
  }

  private ResultResponse calculateResult(VotingSessionEntity votingSession) {
    int yesCount = countChoices(votingSession, YES);
    int noCount = countChoices(votingSession, NO);
    int abstentionCount = countChoices(votingSession, ABSTENTION);
    int representativeCount = determineRepresentativeCount(votingSession);

    boolean accepted =
        switch (votingSession.getType()) {
          case ATTENDANCE_TYPE -> true;
          // A strict majority is required, so a tie is rejected.
          case SIMPLE_MAJORITY_TYPE, QUALIFIED_MAJORITY_TYPE -> yesCount * 2 > representativeCount;
          default ->
              throw new IllegalStateException("Unknown vote type: " + votingSession.getType());
        };

    return new ResultResponse(
        accepted ? ACCEPTED : REJECTED, representativeCount, yesCount, noCount, abstentionCount);
  }

  private int determineRepresentativeCount(VotingSessionEntity votingSession) {
    return switch (votingSession.getType()) {
      case ATTENDANCE_TYPE -> votingSession.getBallots().size();
      // Simple-majority votes use the latest preceding attendance session as their denominator.
      case SIMPLE_MAJORITY_TYPE -> getPresentRepresentativeCount(votingSession.getTimestamp());
      case QUALIFIED_MAJORITY_TYPE -> TOTAL_REPRESENTATIVES;
      default -> throw new IllegalStateException("Unknown vote type: " + votingSession.getType());
    };
  }

  private int getPresentRepresentativeCount(Instant voteTimestamp) {
    return votingSessionRepository
        .findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(ATTENDANCE_TYPE, voteTimestamp)
        .map(session -> session.getBallots().size())
        .orElseThrow(
            () -> {
              log.debug("No prior attendance session exists before {}", voteTimestamp);
              return new VotingResultUnavailableException(
                  "A result cannot be calculated because no prior attendance session exists.");
            });
  }

  private int countChoices(VotingSessionEntity votingSession, String choice) {
    return (int)
        votingSession.getBallots().stream()
            .filter(ballot -> choice.equals(ballot.getChoice()))
            .count();
  }

  private DailyVoteResponse toDailyVoteResponse(VotingSessionEntity votingSession) {
    Optional<ResultResponse> result = calculateResultIfAvailable(votingSession);
    String resultCode = result.map(ResultResponse::result).orElse(null);
    Integer representativeCount = result.map(ResultResponse::representativeCount).orElse(null);
    List<VoteInput> votes =
        votingSession.getBallots().stream()
            .map(ballot -> new VoteInput(ballot.getRepresentativeCode(), ballot.getChoice()))
            .toList();

    return new DailyVoteResponse(
        votingSession.getTimestamp(),
        votingSession.getSubject(),
        votingSession.getType(),
        votingSession.getProcedure(),
        votingSession.getChairperson(),
        resultCode,
        representativeCount,
        votes);
  }

  private Optional<ResultResponse> calculateResultIfAvailable(VotingSessionEntity votingSession) {
    try {
      return Optional.of(calculateResult(votingSession));
    } catch (VotingResultUnavailableException exception) {
      return Optional.empty();
    }
  }

  private void validatePeriod(Instant start, Instant end) {
    if (start.isAfter(end)) {
      throw new BadRequestException("The start of the period cannot be later than the end.");
    }
  }

  private Map<String, Long> newResultCountMap() {
    return new HashMap<>(Map.of(ACCEPTED, 0L, REJECTED, 0L));
  }

  private void incrementResultCount(Map<String, Long> resultCounts, String result) {
    resultCounts.put(result, resultCounts.get(result) + 1);
  }
}
