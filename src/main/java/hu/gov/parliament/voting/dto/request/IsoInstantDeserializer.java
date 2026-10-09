package hu.gov.parliament.voting.dto.request;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class IsoInstantDeserializer extends StdDeserializer<Instant> {

  private static final Pattern ISO_DATE_TIME_WITH_OFFSET =
      Pattern.compile(
          "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})$");

  public IsoInstantDeserializer() {
    super(Instant.class);
  }

  @Override
  public Instant deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      return context.reportInputMismatch(
          this, "The timestamp must be an ISO 8601 date-time string.");
    }

    try {
      String value = parser.getString();
      if (!ISO_DATE_TIME_WITH_OFFSET.matcher(value).matches()) {
        throw new DateTimeParseException(
            "Expected ISO date-time with seconds and timezone", value, 0);
      }
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException exception) {
      return context.reportInputMismatch(
          this, "The timestamp must be an ISO 8601 date-time string.");
    }
  }
}
