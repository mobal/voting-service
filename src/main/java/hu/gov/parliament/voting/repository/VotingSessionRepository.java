package hu.gov.parliament.voting.repository;

import hu.gov.parliament.voting.model.VotingSessionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotingSessionRepository extends JpaRepository<VotingSessionEntity, String> {

  boolean existsByTimestamp(Instant timestamp);

  List<VotingSessionEntity>
      findAllByTimestampGreaterThanEqualAndTimestampLessThanOrderByTimestampAsc(
          Instant fromInclusive, Instant toExclusive);

  List<VotingSessionEntity> findAllByTimestampBetweenOrderByTimestampAsc(
      Instant start, Instant end);

  Optional<VotingSessionEntity> findFirstByTypeAndTimestampBeforeOrderByTimestampDesc(
      String type, Instant timestamp);

  @Query(
      """
      select count(b) from BallotEntity b
      join b.votingSession s
      where s.type <> 'j'
        and s.timestamp between :start and :end
      """)
  long countParticipationInNonAttendanceVotes(
      @Param("start") Instant start, @Param("end") Instant end);
}
