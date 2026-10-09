package hu.gov.parliament.voting.exception;

public class VotingResultUnavailableException extends RuntimeException {

  public VotingResultUnavailableException(String message) {
    super(message);
  }
}
