package hu.gov.parliament.voting.controller;

import hu.gov.parliament.voting.dto.request.CreateVoteRequest;
import hu.gov.parliament.voting.dto.response.AverageResponse;
import hu.gov.parliament.voting.dto.response.DailyVotesResponse;
import hu.gov.parliament.voting.dto.response.ResultResponse;
import hu.gov.parliament.voting.dto.response.SpecialProceduresResponse;
import hu.gov.parliament.voting.dto.response.VoteIdResponse;
import hu.gov.parliament.voting.dto.response.VoteResponse;
import hu.gov.parliament.voting.service.VotingService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/szavazasok")
@RequiredArgsConstructor
public class VotingController {

  private final VotingService votingService;

  @PostMapping("/szavazas")
  public VoteIdResponse createVote(@Valid @RequestBody CreateVoteRequest request) {
    return votingService.createVote(request);
  }

  @GetMapping("/szavazat")
  public VoteResponse getRepresentativeVote(
      @RequestParam("szavazas") String voteId,
      @RequestParam("kepviselo") String representativeCode) {
    return votingService.getRepresentativeVote(voteId, representativeCode);
  }

  @GetMapping("/eredmeny")
  public ResultResponse getResult(@RequestParam("szavazas") String voteId) {
    return votingService.getResult(voteId);
  }

  @GetMapping("/napi-szavazasok")
  public DailyVotesResponse getDailyVotes(
      @RequestParam("nap") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return votingService.getDailyVotes(date);
  }

  @GetMapping("/kepviselo-reszvetel-atlag")
  public AverageResponse getAverageRepresentativeParticipation(
      @RequestParam("kezdet") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
      @RequestParam("veg") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
    return votingService.getAverageRepresentativeParticipation(start, end);
  }

  @GetMapping("/kulonleges-eljarasok-szama")
  public SpecialProceduresResponse getSpecialProcedureStatistics(
      @RequestParam("kezdet") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
      @RequestParam("veg") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
    return votingService.getSpecialProcedureStatistics(start, end);
  }
}
