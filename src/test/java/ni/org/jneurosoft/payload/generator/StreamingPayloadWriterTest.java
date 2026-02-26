package ni.org.jneurosoft.payload.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import ni.org.jneurosoft.payload.generator.data.RealisticData;
import org.junit.jupiter.api.Test;

class StreamingPayloadWriterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void writeShouldHandleNullTemplate() throws Exception {
    String json = generate(NullNode.getInstance(), null, 123L);
    assertEquals("null", json);
  }

  @Test
  void writeShouldGenerateAllScalarTypesAndNullableBranches() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "object",
          "properties": {
            "s1": {"type": "string", "length": 5, "charset": "numeric"},
            "s2": {"type": "string", "length": 3, "charset": "XYZ"},
            "i": {"type": "int", "min": 5, "max": 5},
            "l": {"type": "long", "min": 9, "max": 9},
            "d1": {"type": "decimal", "min": 1.5, "max": 1.5, "scale": 2},
            "d2": {"type": "decimal", "min": "2.345", "max": "2.345", "scale": 3},
            "dFallback": {"type": "decimal", "scale": 2},
            "b": {"type": "boolean"},
            "u": {"type": "uuid"},
            "insNum": {"type": "instant", "min": 0, "max": 0},
            "insNumStr": {"type": "instant", "min": "1000", "max": "1000"},
            "insIso": {"type": "instant", "min": "2020-01-01T00:00:00Z", "max": "2020-01-01T00:00:00Z"},
            "insFallback": {"type": "instant"},
            "dateNum": {"type": "date", "min": 0, "max": 0},
            "dateNumStr": {"type": "date", "min": "1", "max": "1"},
            "dateIso": {"type": "date", "min": "2020-02-02", "max": "2020-02-02"},
            "dateFallback": {"type": "date"},
            "epochNow": {"type": "epoch"},
            "nullableAlwaysNull": {"type": "string", "nullable": true, "nullProbability": 1.0},
            "generated": {"generator": "firstName"},
            "blankType": {"type": "   ", "length": 2, "charset": "upper"}
          }
        }
        """);
    Instant beforeEpoch = Instant.now().minusSeconds(1);
    JsonNode root = MAPPER.readTree(generate(template, null, 123L));
    Instant afterEpoch = Instant.now().plusSeconds(1);
    long epochNow = root.get("epochNow").asLong();
    assertTrue(epochNow >= beforeEpoch.toEpochMilli());
    assertTrue(epochNow <= afterEpoch.toEpochMilli());

    assertTrue(root.get("s1").asText().matches("\\d{5}"));
    assertTrue(root.get("s2").asText().matches("[XYZ]{3}"));
    assertEquals(5, root.get("i").asInt());
    assertEquals(9L, root.get("l").asLong());
    assertEquals(0, root.get("d1").decimalValue().compareTo(new BigDecimal("1.50")));
    assertEquals(0, root.get("d2").decimalValue().compareTo(new BigDecimal("2.345")));
    assertTrue(root.get("dFallback").decimalValue().compareTo(BigDecimal.ZERO) >= 0);
    assertTrue(root.get("dFallback").decimalValue().compareTo(BigDecimal.valueOf(1000)) <= 0);
    assertTrue(root.get("b").isBoolean());

    UUID.fromString(root.get("u").asText());
    assertEquals("1970-01-01T00:00:00Z", root.get("insNum").asText());
    assertEquals("1970-01-01T00:00:01Z", root.get("insNumStr").asText());
    assertEquals("2020-01-01T00:00:00Z", root.get("insIso").asText());
    assertEquals("1970-01-01", root.get("dateNum").asText());
    assertEquals("1970-01-02", root.get("dateNumStr").asText());
    assertEquals("2020-02-02", root.get("dateIso").asText());
    assertTrue(root.get("insFallback").asText().contains("T"));
    assertTrue(root.get("dateFallback").asText().matches("\\d{4}-\\d{2}-\\d{2}"));
    assertTrue(root.get("nullableAlwaysNull").isNull());
    assertTrue(RealisticData.FIRST_NAMES.contains(root.get("generated").asText()));
    assertTrue(root.get("blankType").asText().matches("[A-Z]{2}"));
  }

  @Test
  void writeShouldUseDefaultValueInsteadOfRandomCalculation() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "object",
          "properties": {
            "fixedInt": {"type": "int", "min": 1, "max": 999, "default": 42},
            "fixedString": {"type": "string", "length": 8, "default": "constant"},
            "fixedNull": {"type": "uuid", "default": null},
            "fixedArray": {"type": "array", "count": 10, "item": {"type": "int"}, "default": [1,2,3]},
            "fixedObject": {"type": "object", "properties": {"a": {"type": "int"}}, "default": {"a": "x"}},
            "generatedButDefaulted": {"generator": "firstName", "default": "manual"}
          }
        }
        """);

    JsonNode root = MAPPER.readTree(generate(template, null, 44L));
    assertEquals(42, root.get("fixedInt").asInt());
    assertEquals("constant", root.get("fixedString").asText());
    assertTrue(root.get("fixedNull").isNull());
    assertEquals(3, root.get("fixedArray").size());
    assertEquals("x", root.get("fixedObject").get("a").asText());
    assertEquals("manual", root.get("generatedButDefaulted").asText());
  }

  @Test
  void writeShouldGenerateStringTemplateAsJsonString() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "object",
          "properties": {
            "payload": {
              "type": "stringTemplate",
              "properties": {
                "id": {"type": "int", "min": 1, "max": 1},
                "name": {"generator": "firstName"},
                "inner": {
                  "type": "object",
                  "properties": {
                    "active": {"type": "boolean", "default": true}
                  }
                },
                "values": {"type": "array", "count": 2, "item": {"type": "int", "min": 4, "max": 4}}
              }
            }
          }
        }
        """);

    JsonNode root = MAPPER.readTree(generate(template, null, 8L));
    assertTrue(root.get("payload").isTextual());

    JsonNode embedded = MAPPER.readTree(root.get("payload").asText());
    assertEquals(1, embedded.get("id").asInt());
    assertTrue(RealisticData.FIRST_NAMES.contains(embedded.get("name").asText()));
    assertTrue(embedded.get("inner").get("active").asBoolean());
    assertEquals(2, embedded.get("values").size());
    assertEquals(4, embedded.get("values").get(0).asInt());
  }

  @Test
  void writeShouldHandleDateModesPastOrPresentAndFuture() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "object",
          "properties": {
            "pastDate": {"type": "date", "pastOrPresent": true},
            "futureDate": {"type": "date", "future": true},
            "pastInstant": {"type": "instant", "pastOrPresent": true},
            "futureInstant": {"type": "instant", "future": true}
          }
        }
        """);

    Instant before = Instant.now().minusSeconds(1);
    JsonNode root = MAPPER.readTree(generate(template, null, 55L));
    Instant after = Instant.now().plusSeconds(2);

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate pastDate = LocalDate.parse(root.get("pastDate").asText());
    LocalDate futureDate = LocalDate.parse(root.get("futureDate").asText());
    Instant pastInstant = Instant.parse(root.get("pastInstant").asText());
    Instant futureInstant = Instant.parse(root.get("futureInstant").asText());

    assertTrue(!pastDate.isAfter(today));
    assertTrue(futureDate.isAfter(today));
    assertTrue(!pastInstant.isAfter(after));
    assertTrue(futureInstant.isAfter(before));
  }

  @Test
  void writeShouldFailWhenDateModesConflict() throws Exception {
    JsonNode badDate = MAPPER.readTree("""
        {
          "type": "date",
          "pastOrPresent": true,
          "future": true
        }
        """);
    assertThrows(IllegalArgumentException.class, () -> generate(badDate, null, 1L));

    JsonNode badInstant = MAPPER.readTree("""
        {
          "type": "instant",
          "pastOrPresent": true,
          "future": true
        }
        """);
    assertThrows(IllegalArgumentException.class, () -> generate(badInstant, null, 1L));
  }

  @Test
  void writeShouldCoverAdditionalCharsetBranches() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "object",
          "properties": {
            "alphaNum": {"type": "string", "length": 4, "charset": "alphanumeric"},
            "hex": {"type": "string", "length": 6, "charset": "hex"},
            "lower": {"type": "string", "length": 5, "charset": "lower"},
            "fallbackBlank": {"type": "string", "length": 5, "charset": ""}
          }
        }
        """);

    JsonNode root = MAPPER.readTree(generate(template, null, 7L));
    assertTrue(root.get("alphaNum").asText().matches("[A-Za-z0-9]{4}"));
    assertTrue(root.get("hex").asText().matches("[0-9a-f]{6}"));
    assertTrue(root.get("lower").asText().matches("[a-z]{5}"));
    assertEquals(5, root.get("fallbackBlank").asText().length());
  }

  @Test
  void writeShouldResolveImplicitTypesAndSequenceInArrays() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "properties": {
            "name": {"length": 2, "charset": "upper"},
            "items": {
              "count": 3,
              "item": {
                "properties": {
                  "id": {"type": "long", "min": 1, "max": 10, "seq": true}
                }
              }
            }
          }
        }
        """);

    JsonNode root = MAPPER.readTree(generate(template, null, 50L));

    assertTrue(root.get("name").asText().matches("[A-Z]{2}"));
    assertEquals(1L, root.get("items").get(0).get("id").asLong());
    assertEquals(2L, root.get("items").get(1).get("id").asLong());
    assertEquals(3L, root.get("items").get(2).get("id").asLong());
  }

  @Test
  void writeShouldRespectGlobalMaxBytes() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "array",
          "count": 200,
          "item": {"type": "string", "length": 30, "charset": "alpha"}
        }
        """);

    String bounded = generate(template, 220L, 1L);
    String unbounded = generate(template, null, 1L);

    JsonNode boundedArray = MAPPER.readTree(bounded);
    JsonNode unboundedArray = MAPPER.readTree(unbounded);

    assertTrue(bounded.length() < unbounded.length());
    assertFalse(boundedArray.isEmpty());
    assertTrue(boundedArray.size() < unboundedArray.size());
  }

  @Test
  void writeShouldHandleUntilBytesWithoutCount() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "array",
          "untilBytes": 120,
          "item": {"type": "int", "min": 1, "max": 1}
        }
        """);

    JsonNode result = MAPPER.readTree(generate(template, null, 9L));
    assertFalse(result.isEmpty());
    assertTrue(result.size() > 1);
  }

  @Test
  void writeShouldHandleArrayDefaultBranchWhenNoCountOrUntilBytes() throws Exception {
    JsonNode template = MAPPER.readTree("""
        {
          "type": "array",
          "item": {"type": "int", "min": 4, "max": 4}
        }
        """);

    JsonNode result = MAPPER.readTree(generate(template, null, 4L));
    assertEquals(1, result.size());
    assertEquals(4, result.get(0).asInt());
  }

  @Test
  void writeShouldFailForUnsupportedOrInvalidStructures() throws Exception {
    JsonNode badType = MAPPER.readTree("{\"type\":\"unknown\"}");
    assertThrows(IllegalArgumentException.class, () -> generate(badType, null, 1L));

    JsonNode badObject = MAPPER.readTree("{\"type\":\"object\"}");
    assertThrows(IllegalArgumentException.class, () -> generate(badObject, null, 1L));

    JsonNode badStringTemplate = MAPPER.readTree("{\"type\":\"stringTemplate\"}");
    assertThrows(IllegalArgumentException.class, () -> generate(badStringTemplate, null, 1L));

    JsonNode badArray = MAPPER.readTree("{\"type\":\"array\"}");
    assertThrows(IllegalArgumentException.class, () -> generate(badArray, null, 1L));

    JsonNode unknownGenerator = MAPPER.readTree("{\"generator\":\"missing\"}");
    assertThrows(IllegalArgumentException.class, () -> generate(unknownGenerator, null, 1L));
  }

  private static String generate(JsonNode template, Long maxBytes, long seed) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    CountingOutputStream cos = new CountingOutputStream(bos);
    GenerationContext context = new GenerationContext(seed, maxBytes, cos, GeneratorRegistry.defaultRegistry());

    try (JsonGenerator generator = new JsonFactory().createGenerator(cos)) {
      StreamingPayloadWriter.write(template, generator, context);
      generator.flush();
    }

    return bos.toString(StandardCharsets.UTF_8);
  }
}
