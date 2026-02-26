package ni.org.jneurosoft.payload.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

public final class StreamingPayloadWriter {

  private static final String DEFAULT_CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final Instant DEFAULT_MIN_INSTANT = Instant.parse("2000-01-01T00:00:00Z");
  private static final Instant DEFAULT_MAX_INSTANT = Instant.parse("2035-12-31T23:59:59Z");
  private static final LocalDate DEFAULT_MIN_DATE = LocalDate.parse("2000-01-01");
  private static final LocalDate DEFAULT_MAX_DATE = LocalDate.parse("2035-12-31");

  private StreamingPayloadWriter() {
  }

  public static void write(JsonNode template, JsonGenerator generator, GenerationContext context) throws IOException {
    writeNode(template, generator, context, "$");
  }

  private static void writeNode(JsonNode node, JsonGenerator generator, GenerationContext context, String path)
      throws IOException {
    if (node == null || node.isNull()) {
      generator.writeNull();
      return;
    }

    if (node.has("default")) {
      writeLiteralValue(node.get("default"), generator);
      return;
    }

    if (node.path("nullable").asBoolean(false)) {
      double nullProbability = node.path("nullProbability").asDouble(0.1);
      if (context.shouldWriteNull(nullProbability)) {
        generator.writeNull();
        return;
      }
    }

    String customGenerator = textOrNull(node.get("generator"));
    if (customGenerator != null) {
      String generated = context.generatorRegistry().generate(customGenerator, node, context, path);
      generator.writeString(generated);
      return;
    }

    String type = resolveType(node);
    switch (type) {
      case "object" -> writeObject(node, generator, context, path);
      case "stringTemplate" -> writeStringTemplate(node, generator, context, path);
      case "array" -> writeArray(node, generator, context, path);
      case "string" -> generator.writeString(randomString(node, context));
      case "int" -> generator.writeNumber(randomInt(node, context));
      case "long" -> generator.writeNumber(randomLong(node, context, path));
      case "decimal" -> generator.writeNumber(randomDecimal(node, context));
      case "boolean" -> generator.writeBoolean(context.randomBoolean());
      case "uuid" -> generator.writeString(randomUuid(context));
      case "instant" -> generator.writeString(randomInstant(node, context));
      case "date" -> generator.writeString(randomDate(node, context));
      case "epoch" -> generator.writeNumber(currentEpochMillis());
      default -> throw new IllegalArgumentException("Tipo no soportado: " + type + " en " + path);
    }
  }

  private static void writeObject(JsonNode node, JsonGenerator generator, GenerationContext context, String path)
      throws IOException {
    JsonNode properties = node.get("properties");
    if (properties == null || !properties.isObject()) {
      throw new IllegalArgumentException("object requiere properties en " + path);
    }

    generator.writeStartObject();
    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      generator.writeFieldName(field.getKey());
      writeNode(field.getValue(), generator, context, path + "." + field.getKey());
    }
    generator.writeEndObject();
  }

  private static void writeStringTemplate(
      JsonNode node,
      JsonGenerator generator,
      GenerationContext context,
      String path
  ) throws IOException {
    JsonNode properties = node.get("properties");
    if (properties == null || !properties.isObject()) {
      throw new IllegalArgumentException("stringTemplate requiere properties en " + path);
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (CountingOutputStream nestedOutput = new CountingOutputStream(buffer);
        JsonGenerator nestedGenerator = new JsonFactory().createGenerator(nestedOutput)) {
      GenerationContext nestedContext = context.forkForOutput(nestedOutput);
      writeObject(node, nestedGenerator, nestedContext, path);
      nestedGenerator.flush();
    }

    generator.writeString(buffer.toString(StandardCharsets.UTF_8));
  }

  private static void writeArray(JsonNode node, JsonGenerator generator, GenerationContext context, String path)
      throws IOException {
    JsonNode item = node.get("item");
    if (item == null || item.isNull()) {
      throw new IllegalArgumentException("array requiere item en " + path);
    }

    int count = node.path("count").asInt(-1);
    long untilBytes = node.path("untilBytes").asLong(-1L);
    long startBytes = context.bytesWritten();
    boolean enforceByteChecks = untilBytes > 0 || context.hasGlobalMaxBytes();

    generator.writeStartArray();

    if (count >= 0) {
      for (int i = 0; i < count; i++) {
        if (context.reachedGlobalMaxBytes()) {
          break;
        }
        if (untilBytes > 0 && context.bytesWritten() - startBytes >= untilBytes) {
          break;
        }

        writeNode(item, generator, context, path + "[" + i + "]");

        if (enforceByteChecks) {
          generator.flush();
          if (context.reachedGlobalMaxBytes()) {
            break;
          }
          if (untilBytes > 0 && context.bytesWritten() - startBytes >= untilBytes) {
            break;
          }
        }
      }
    } else if (untilBytes > 0) {
      int i = 0;
      while (context.bytesWritten() - startBytes < untilBytes) {
        if (context.reachedGlobalMaxBytes()) {
          break;
        }

        writeNode(item, generator, context, path + "[" + i + "]");
        i++;

        generator.flush();
        if (context.reachedGlobalMaxBytes()) {
          break;
        }
      }
    } else {
      if (!context.reachedGlobalMaxBytes()) {
        writeNode(item, generator, context, path + "[0]");
      }
    }

    generator.writeEndArray();
  }

  private static int randomInt(JsonNode node, GenerationContext context) {
    int min = node.path("min").asInt(0);
    int max = node.path("max").asInt(1000);
    return context.randomInt(min, max);
  }

  private static long randomLong(JsonNode node, GenerationContext context, String path) {
    long min = node.path("min").asLong(0L);
    long max = node.path("max").asLong(1_000_000L);
    boolean seq = node.path("seq").asBoolean(false);
    if (seq) {
      return context.nextSequence(normalizeSequencePath(path), min, max);
    }
    return context.randomLong(min, max);
  }

  private static BigDecimal randomDecimal(JsonNode node, GenerationContext context) {
    BigDecimal min = decimalOrDefault(node.get("min"), BigDecimal.ZERO);
    BigDecimal max = decimalOrDefault(node.get("max"), BigDecimal.valueOf(1000));
    int scale = node.path("scale").asInt(2);

    double randomFactor = context.randomDouble(0.0, 1.0);
    BigDecimal span = max.subtract(min);
    BigDecimal value = min.add(span.multiply(BigDecimal.valueOf(randomFactor)));
    return value.setScale(scale, RoundingMode.HALF_UP);
  }

  private static String randomString(JsonNode node, GenerationContext context) {
    int length = node.path("length").asInt(12);
    String charset = resolveCharset(node.path("charset").asText("alphanumeric"));

    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int idx = context.randomInt(0, charset.length() - 1);
      sb.append(charset.charAt(idx));
    }
    return sb.toString();
  }

  private static String randomUuid(GenerationContext context) {
    long msb = context.random().nextLong();
    long lsb = context.random().nextLong();
    return new UUID(msb, lsb).toString();
  }

  private static String randomInstant(JsonNode node, GenerationContext context) {
    Instant min = instantOrDefault(node.get("min"), DEFAULT_MIN_INSTANT);
    Instant max = instantOrDefault(node.get("max"), DEFAULT_MAX_INSTANT);

    boolean pastOrPresent = node.path("pastOrPresent").asBoolean(false);
    boolean future = node.path("future").asBoolean(false);
    if (pastOrPresent && future) {
      throw new IllegalArgumentException("instant no puede tener pastOrPresent y future a la vez");
    }

    Instant now = Instant.now();
    if (pastOrPresent && max.isAfter(now)) {
      max = now;
    }
    if (future) {
      Instant nextSecond = now.plusSeconds(1);
      if (min.isBefore(nextSecond)) {
        min = nextSecond;
      }
    }

    if (max.isBefore(min)) {
      throw new IllegalArgumentException("Rango invalido para instant: min > max");
    }

    long epoch = context.randomLong(min.getEpochSecond(), max.getEpochSecond());
    return Instant.ofEpochSecond(epoch).toString();
  }

  private static String randomDate(JsonNode node, GenerationContext context) {
    LocalDate min = localDateOrDefault(node.get("min"), DEFAULT_MIN_DATE);
    LocalDate max = localDateOrDefault(node.get("max"), DEFAULT_MAX_DATE);

    boolean pastOrPresent = node.path("pastOrPresent").asBoolean(false);
    boolean future = node.path("future").asBoolean(false);
    if (pastOrPresent && future) {
      throw new IllegalArgumentException("date no puede tener pastOrPresent y future a la vez");
    }

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (pastOrPresent && max.isAfter(today)) {
      max = today;
    }
    if (future) {
      LocalDate tomorrow = today.plusDays(1);
      if (min.isBefore(tomorrow)) {
        min = tomorrow;
      }
    }

    if (max.isBefore(min)) {
      throw new IllegalArgumentException("Rango invalido para date: min > max");
    }

    long minEpoch = min.toEpochDay();
    long maxEpoch = max.toEpochDay();
    long day = context.randomLong(minEpoch, maxEpoch);
    return LocalDate.ofEpochDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  private static long currentEpochMillis() {
    return Instant.now().toEpochMilli();
  }

  private static String normalizeSequencePath(String path) {
    return path.replaceAll("\\[\\d+\\]", "[]");
  }

  private static String resolveType(JsonNode node) {
    String explicitType = textOrNull(node.get("type"));
    if (explicitType != null) {
      return explicitType;
    }
    if (node.has("properties")) {
      return "object";
    }
    if (node.has("item")) {
      return "array";
    }
    return "string";
  }

  private static String resolveCharset(String raw) {
    return switch (raw) {
      case "alpha" -> "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
      case "alphanumeric" -> DEFAULT_CHARSET;
      case "numeric" -> "0123456789";
      case "hex" -> "0123456789abcdef";
      case "lower" -> "abcdefghijklmnopqrstuvwxyz";
      case "upper" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
      default -> raw == null || raw.isBlank() ? DEFAULT_CHARSET : raw;
    };
  }

  private static void writeLiteralValue(JsonNode value, JsonGenerator generator) throws IOException {
    if (value == null || value.isNull()) {
      generator.writeNull();
      return;
    }

    if (value.isObject()) {
      generator.writeStartObject();
      Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        generator.writeFieldName(field.getKey());
        writeLiteralValue(field.getValue(), generator);
      }
      generator.writeEndObject();
      return;
    }

    if (value.isArray()) {
      generator.writeStartArray();
      for (JsonNode element : value) {
        writeLiteralValue(element, generator);
      }
      generator.writeEndArray();
      return;
    }

    if (value.isTextual()) {
      generator.writeString(value.asText());
      return;
    }

    if (value.isBoolean()) {
      generator.writeBoolean(value.asBoolean());
      return;
    }

    if (value.isIntegralNumber()) {
      generator.writeNumber(value.longValue());
      return;
    }

    if (value.isFloatingPointNumber() || value.isBigDecimal()) {
      generator.writeNumber(value.decimalValue());
      return;
    }

    generator.writeString(value.asText());
  }

  private static BigDecimal decimalOrDefault(JsonNode node, BigDecimal defaultValue) {
    if (node == null || node.isNull()) {
      return defaultValue;
    }

    if (node.isNumber()) {
      return node.decimalValue();
    }

    String raw = node.asText();
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }

    return new BigDecimal(raw);
  }

  private static Instant instantOrDefault(JsonNode node, Instant defaultValue) {
    if (node == null || node.isNull()) {
      return defaultValue;
    }

    if (node.isIntegralNumber()) {
      return Instant.ofEpochSecond(node.longValue());
    }

    String raw = node.asText();
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }

    if (raw.matches("^-?\\d+$")) {
      return Instant.ofEpochMilli(Long.parseLong(raw));
    }

    return Instant.parse(raw);
  }

  private static LocalDate localDateOrDefault(JsonNode node, LocalDate defaultValue) {
    if (node == null || node.isNull()) {
      return defaultValue;
    }

    if (node.isIntegralNumber()) {
      return LocalDate.ofEpochDay(node.longValue());
    }

    String raw = node.asText();
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }

    if (raw.matches("^-?\\d+$")) {
      return LocalDate.ofEpochDay(Long.parseLong(raw));
    }

    return LocalDate.parse(raw);
  }

  private static String textOrNull(JsonNode node) {
    if (node == null || node.isNull()) {
      return null;
    }
    String text = node.asText();
    return text == null || text.isBlank() ? null : text;
  }
}
