package hu.gov.parliament.voting.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "voting_session",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_voting_session_timestamp", columnNames = "vote_timestamp"))
public class VotingSessionEntity {

  @Id
  @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "vote_timestamp", nullable = false)
  private Instant timestamp;

  @Column(name = "subject_text", nullable = false, columnDefinition = "VARCHAR")
  private String subject;

  @Column(name = "vote_type", nullable = false, length = 1)
  private String type;

  @Column(name = "procedure_code", length = 1)
  private String procedure;

  @Column(name = "chairperson_code", nullable = false, columnDefinition = "VARCHAR")
  private String chairperson;

  @OneToMany(
      mappedBy = "votingSession",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("id ASC")
  private List<BallotEntity> ballots = new ArrayList<>();

  public VotingSessionEntity(
      String id,
      Instant timestamp,
      String subject,
      String type,
      String procedure,
      String chairperson) {
    this.id = id;
    this.timestamp = timestamp;
    this.subject = subject;
    this.type = type;
    this.procedure = procedure;
    this.chairperson = chairperson;
  }

  public void addBallot(BallotEntity ballot) {
    ballots.add(ballot);
    ballot.setVotingSession(this);
  }
}
