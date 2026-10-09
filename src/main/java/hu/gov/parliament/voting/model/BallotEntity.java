package hu.gov.parliament.voting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "ballot",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_ballot_session_representative",
            columnNames = {"voting_session_id", "representative_code"}))
public class BallotEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "voting_session_id", nullable = false)
  private VotingSessionEntity votingSession;

  @Column(name = "representative_code", nullable = false, columnDefinition = "VARCHAR")
  private String representativeCode;

  @Column(name = "choice_code", nullable = false, length = 1)
  private String choice;

  public BallotEntity(String representativeCode, String choice) {
    this.representativeCode = representativeCode;
    this.choice = choice;
  }

  void setVotingSession(VotingSessionEntity votingSession) {
    this.votingSession = votingSession;
  }
}
