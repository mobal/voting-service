# Voting Service

Spring Boot REST API for registering parliamentary votes, querying individual ballots, calculating vote results and producing voting statistics.

## Tech stack

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Data JPA / Hibernate
- H2
- Liquibase
- Gradle
- JUnit 5 + Mockito
- Spotless + Google Java Format
- JaCoCo

## Run

The project contains the Gradle wrapper, so only Java 21 is required.

```bash
./gradlew bootRun
```

The application starts on:

```text
http://localhost:8080
```

H2 runs in memory. Liquibase creates the database schema on startup and Hibernate validates it with `ddl-auto: validate`.

## API

All endpoints are under `/szavazasok`.

| Method | Path | Description |
| --- | --- | --- |
| POST | `/szavazasok/szavazas` | Register a vote |
| GET | `/szavazasok/szavazat` | Get a representative's vote |
| GET | `/szavazasok/eredmeny` | Calculate the result of a vote |
| GET | `/szavazasok/napi-szavazasok` | List votes and results for a day |
| GET | `/szavazasok/kepviselo-reszvetel-atlag` | Calculate average representative participation |
| GET | `/szavazasok/kulonleges-eljarasok-szama` | Get special-procedure statistics |

### Register a vote

```http
POST /szavazasok/szavazas
Content-Type: application/json
```

Example:

```json
{
  "idopont": "2023-09-28T11:06:25Z",
  "targy": "Vote subject",
  "tipus": "e",
  "eljaras": "n",
  "elnok": "Representative1",
  "szavazatok": [
    {"kepviselo": "Representative1", "szavazat": "i"},
    {"kepviselo": "Representative2", "szavazat": "n"},
    {"kepviselo": "Representative3", "szavazat": "t"}
  ]
}
```

Response:

```json
{
  "szavazasId": "6d3d9e2e79084f058cf029ea57ad5df8"
}
```

Validation includes:

- required nonblank textual fields;
- vote type: `j`, `e` or `m`;
- procedure: `n`, `s`, `k` or `e` when provided;
- vote choice: `i`, `n` or `t`;
- maximum 200 representatives per vote;
- one vote per representative within a voting session;
- the chairperson must also vote;
- only one voting session may exist at a given timestamp;
- voting timestamps must have second precision.

### Get a representative's vote

```http
GET /szavazasok/szavazat?szavazas={voteId}&kepviselo={representativeCode}
```

Returns `404` when the vote does not exist or the representative did not vote.

### Get the result

```http
GET /szavazasok/eredmeny?szavazas={voteId}
```

Result rules:

- `j` — attendance vote: always accepted;
- `e` — simple majority: accepted when more than half of the representatives present voted yes;
- `m` — qualified majority: accepted when more than half of all 200 representatives voted yes.

For a simple-majority vote, attendance is taken from the latest preceding attendance vote.
If none exists, the vote is still registered, but its result is unavailable: the result endpoint returns `409 Conflict` with an explanation.

### Daily votes

```http
GET /szavazasok/napi-szavazasok?nap=2023-12-13
```

Returns an empty `szavazasok` array when there are no votes on the requested day.
Votes without a calculable result remain in the response; their `eredmeny` and `kepviselokSzama` fields are omitted.
The requested date is interpreted as a UTC calendar day.

### Average participation

```http
GET /szavazasok/kepviselo-reszvetel-atlag?kezdet=2023-01-01T00:00:00Z&veg=2023-12-31T23:59:59Z
```

Attendance votes are excluded. The result is rounded to two decimal places.
The numerator is the total number of ballots in non-attendance votes, and the denominator is the fixed 200 representative seats. The `kezdet` and `veg` period bounds are inclusive.

### Special-procedure statistics

```http
GET /szavazasok/kulonleges-eljarasok-szama?kezdet=2023-01-01T00:00:00Z&veg=2023-12-31T23:59:59Z
```

Statistics are returned for emergency (`s`), exceptional (`k`) and rules-deviating (`e`) procedures, including accepted/rejected totals.
The `kezdet` and `veg` period bounds are inclusive. Simple-majority votes without a preceding attendance vote are excluded because their result cannot be determined.

## Errors

Application errors use Spring `ProblemDetail` and `application/problem+json`.
Unknown JSON properties are ignored, consistent with the request schema.

Typical statuses:

- `400 Bad Request` — malformed input, validation failure or violated business constraint;
- `404 Not Found` — requested vote or representative vote does not exist;
- `409 Conflict` — a vote exists but its result cannot be calculated because no preceding attendance vote exists.

Malformed JSON responses include the parser's cause when available. Validation errors identify the invalid request field.

## Database

Liquibase migrations are stored under:

```text
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── 001-create-voting-schema.yaml
```

The database also enforces:

- unique voting timestamps;
- unique `(voting_session_id, representative_code)` ballot pairs;
- the ballot-to-voting-session foreign key.

There are no update or delete endpoints and no explicit locking mechanism.

## Tests and quality checks

Tests are separated into:

```text
src/test/java/hu/gov/parliament/voting/
├── unit/
└── integration/
```

Unit tests use JUnit 5 and Mockito without starting Spring. Integration tests start the application and exercise the REST API with `RestTestClient` against H2.

Run all verification tasks:

```bash
./gradlew clean check
```

`check` includes:

- unit and integration tests;
- Spotless formatting verification;
- JaCoCo coverage verification.

The build requires at least **80% branch coverage**. The HTML coverage report is generated at:

```text
build/reports/jacoco/test/html/index.html
```

Apply formatting with:

```bash
./gradlew spotlessApply
```

## Docker

Build the application image from the project root:

```bash
docker build -t voting-service:local .
```

The Gradle task can build and tag the image using the project name and version:

```bash
./gradlew dockerBuild
```

Run it with port 8080 published:

```bash
docker run --rm --name voting-service -p 8080:8080 voting-service:local
```

Wait for Spring Boot to finish starting. The application is then available at `http://localhost:8080`.

The default profile uses an in-memory H2 database. Stopping the container removes it and its data.

## Postman

The collection is at:

```text
postman/Voting-Service.postman_collection.json
```

With the application running in Docker, use a second terminal and run the full collection with Newman via `npx`:

```bash
npx --yes newman run postman/Voting-Service.postman_collection.json \
  --env-var 'baseUrl=http://localhost:8080'
```

This requires Node.js and npm. The collection's `baseUrl` already defaults to `http://localhost:8080`, so the `--env-var` option is only needed when using a different address. The collection creates its own fixtures and runs them in order. It also checks that numeric epoch timestamps are rejected, so its successful timestamp fixtures use ISO 8601 strings.
