package ni.org.jneurosoft.payload.generator.generators;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ni.org.jneurosoft.payload.generator.CountingOutputStream;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.GeneratorRegistry;
import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void shouldGenerateNumericByDefault() {
    AccountNumberGenerator generator = new AccountNumberGenerator();
    String account = generator.generate(MAPPER.createObjectNode(), context(10L), "$.account");

    assertTrue(account.matches("\\d{12}"));
  }

  @Test
  void shouldGenerateAlphanumericWithoutCheckDigit() {
    AccountNumberGenerator generator = new AccountNumberGenerator();
    ObjectNode node = MAPPER.createObjectNode();
    node.put("format", "alphanumeric");
    node.put("length", 16);

    String account = generator.generate(node, context(20L), "$.account");
    assertTrue(account.matches("[0-9A-Z]{16}"));
  }

  @Test
  void shouldApplyPrefixGroupingAndSeparator() {
    AccountNumberGenerator generator = new AccountNumberGenerator();
    ObjectNode node = MAPPER.createObjectNode();
    node.put("length", 12);
    node.put("prefix", "77");
    node.put("grouping", 4);
    node.put("separator", " ");

    String account = generator.generate(node, context(30L), "$.account");

    assertTrue(account.startsWith("77"));
    assertTrue(account.matches("[0-9]{4} [0-9]{4} [0-9]{4}"));
  }

  @Test
  void shouldGenerateValidLuhnCheckDigit() {
    AccountNumberGenerator generator = new AccountNumberGenerator();
    ObjectNode node = MAPPER.createObjectNode();
    node.put("checkDigit", "luhn");
    node.put("length", 14);

    String account = generator.generate(node, context(40L), "$.account");
    assertTrue(account.matches("\\d{14}"));
    assertTrue(isValidLuhn(account));
  }

  @Test
  void shouldGenerateValidMod11CheckDigit() {
    AccountNumberGenerator generator = new AccountNumberGenerator();
    ObjectNode node = MAPPER.createObjectNode();
    node.put("checkDigit", "mod11");
    node.put("length", 14);

    String account = generator.generate(node, context(50L), "$.account");
    assertTrue(account.matches("\\d{14}"));
    assertTrue(isValidMod11(account));
  }

  @Test
  void shouldValidateInvalidOptions() {
    AccountNumberGenerator generator = new AccountNumberGenerator();

    ObjectNode invalidCheck = MAPPER.createObjectNode();
    invalidCheck.put("checkDigit", "xpto");
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(invalidCheck, context(1L), "$.account"));

    ObjectNode invalidFormat = MAPPER.createObjectNode();
    invalidFormat.put("format", "symbols");
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(invalidFormat, context(1L), "$.account"));

    ObjectNode invalidMixed = MAPPER.createObjectNode();
    invalidMixed.put("format", "alphanumeric");
    invalidMixed.put("checkDigit", "luhn");
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(invalidMixed, context(1L), "$.account"));

    ObjectNode invalidLength = MAPPER.createObjectNode();
    invalidLength.put("length", 0);
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(invalidLength, context(1L), "$.account"));

    ObjectNode invalidPrefix = MAPPER.createObjectNode();
    invalidPrefix.put("length", 3);
    invalidPrefix.put("prefix", "1234");
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(invalidPrefix, context(1L), "$.account"));

    ObjectNode insufficientLength = MAPPER.createObjectNode();
    insufficientLength.put("length", 4);
    insufficientLength.put("prefix", "1234");
    insufficientLength.put("checkDigit", "luhn");
    assertThrows(IllegalArgumentException.class,
        () -> generator.generate(insufficientLength, context(1L), "$.account"));
  }

  private static boolean isValidLuhn(String digits) {
    int sum = 0;
    boolean shouldDouble = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (shouldDouble) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      shouldDouble = !shouldDouble;
    }
    return sum % 10 == 0;
  }

  private static boolean isValidMod11(String digits) {
    int provided = digits.charAt(digits.length() - 1) - '0';
    String payload = digits.substring(0, digits.length() - 1);

    int sum = 0;
    int weight = 2;
    for (int i = payload.length() - 1; i >= 0; i--) {
      int d = payload.charAt(i) - '0';
      sum += d * weight;
      weight = (weight == 7) ? 2 : weight + 1;
    }

    int check = (11 - (sum % 11)) % 11;
    if (check == 10) {
      check = 0;
    }
    return check == provided;
  }

  private static GenerationContext context(long seed) {
    return new GenerationContext(seed, null,
        new CountingOutputStream(new ByteArrayOutputStream()),
        GeneratorRegistry.defaultRegistry());
  }
}
