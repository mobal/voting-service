package hu.gov.parliament.voting.repository;

import hu.gov.parliament.voting.model.BallotEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BallotRepository extends JpaRepository<BallotEntity, Long> {

  Optional<BallotEntity> findByVotingSessionIdAndRepresentativeCode(
      String voteId, String representativeCode);
}
