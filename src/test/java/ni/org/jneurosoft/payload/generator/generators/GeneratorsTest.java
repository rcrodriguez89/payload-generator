package ni.org.jneurosoft.payload.generator.generators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ni.org.jneurosoft.payload.generator.CountingOutputStream;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.GeneratorRegistry;
import ni.org.jneurosoft.payload.generator.data.RealisticData;
import org.junit.jupiter.api.Test;

class GeneratorsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void registryShouldGenerateDefaultValuesAndSupportCustomGenerator() {
    GenerationContext context = context(99L);
    GeneratorRegistry registry = GeneratorRegistry.defaultRegistry();
    ObjectNode empty = MAPPER.createObjectNode();

    String firstName = registry.generate("firstName", empty, context, "$.firstName");
    assertTrue(RealisticData.FIRST_NAMES.contains(firstName));

    String lastName = registry.generate("lastName", empty, context, "$.lastName");
    assertTrue(RealisticData.LAST_NAMES.contains(lastName));

    String fullName = registry.generate("fullName", empty, context, "$.fullName");
    assertEquals(firstName + " " + lastName, fullName);

    String country = registry.generate("country", empty, context, "$.country");
    assertTrue(RealisticData.COUNTRIES.contains(country));

    String city = registry.generate("city", empty, context, "$.city");
    assertTrue(RealisticData.CITIES_BY_COUNTRY.get(country).contains(city));

    String rut = registry.generate("chileanRut", empty, context, "$.rut");
    assertTrue(isValidRut(rut));
    assertTrue(rut.contains("."));

    ObjectNode rutNoDotsNode = MAPPER.createObjectNode();
    rutNoDotsNode.put("omitGroupSeparator", true);
    String rutNoDots = registry.generate("chileanRut", rutNoDotsNode, context, "$.rut");
    assertTrue(isValidRut(rutNoDots));
    assertFalse(rutNoDots.contains("."));

    ObjectNode accountNode = MAPPER.createObjectNode();
    accountNode.put("checkDigit", "luhn");
    accountNode.put("length", 14);
    String account = registry.generate("accountNumber", accountNode, context, "$.account");
    assertTrue(account.matches("\\d{14}"));

    registry.register("custom", (node, ctx, path) -> "ok");
    assertEquals("ok", registry.generate("custom", empty, context, "$.x"));
  }

  @Test
  void registryShouldFailForUnknownGenerator() {
    GenerationContext context = context(1L);
    GeneratorRegistry registry = GeneratorRegistry.defaultRegistry();

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> registry.generate("doesNotExist", MAPPER.createObjectNode(), context, "$.x"));

    assertTrue(ex.getMessage().contains("no soportado"));
  }

  @Test
  void fullNameGeneratorShouldBackfillNamesWhenMissing() {
    GenerationContext context = context(7L);
    FullNameGenerator generator = new FullNameGenerator();

    String fullName = generator.generate(MAPPER.createObjectNode(), context, "$.fullName");

    String[] parts = fullName.split(" ");
    assertEquals(2, parts.length);
    assertEquals(parts[0], context.getCurrentValue("firstName"));
    assertEquals(parts[1], context.getCurrentValue("lastName"));
  }

  @Test
  void emailGeneratorShouldUseOverrideDomainWhenProvided() {
    GenerationContext context = context(17L);
    context.putCurrentValue("firstName", "Jöhn");
    context.putCurrentValue("lastName", "Doe-Smith");

    ObjectNode emailNode = MAPPER.createObjectNode();
    emailNode.put("domain", "acme.org");

    String email = new EmailGenerator().generate(emailNode, context, "$.email");

    assertTrue(email.endsWith("@acme.org"));
    assertTrue(email.startsWith("jhn.doesmith"));
  }

  @Test
  void emailGeneratorShouldUseFallbackDomainListWhenDomainIsMissing() {
    GenerationContext context = context(33L);
    context.putCurrentValue("firstName", "Ava");
    context.putCurrentValue("lastName", "Johnson");

    String email = new EmailGenerator().generate(MAPPER.createObjectNode(), context, "$.email");

    String domain = email.substring(email.indexOf('@') + 1);
    assertTrue(RealisticData.EMAIL_DOMAINS.contains(domain));
  }

  @Test
  void cityGeneratorShouldRecoverWhenCurrentCountryIsInvalid() {
    GenerationContext context = context(101L);
    context.putCurrentValue("country", "Atlantis");

    CityGenerator generator = new CityGenerator();
    String city = generator.generate(MAPPER.createObjectNode(), context, "$.city");

    String resolvedCountry = context.getCurrentValue("country");
    assertTrue(RealisticData.COUNTRIES.contains(resolvedCountry));
    List<String> cities = RealisticData.CITIES_BY_COUNTRY.get(resolvedCountry);
    assertTrue(cities.contains(city));
  }

  private static boolean isValidRut(String rut) {
    String normalized = rut.replace(".", "");
    String[] parts = normalized.split("-");
    if (parts.length != 2) {
      return false;
    }

    String body = parts[0];
    String verifier = parts[1].toUpperCase();
    if (!body.matches("\\d{7,8}")) {
      return false;
    }

    int sum = 0;
    int multiplier = 2;
    for (int i = body.length() - 1; i >= 0; i--) {
      sum += (body.charAt(i) - '0') * multiplier;
      multiplier = (multiplier == 7) ? 2 : multiplier + 1;
    }

    int expected = 11 - (sum % 11);
    String expectedVerifier = switch (expected) {
      case 11 -> "0";
      case 10 -> "K";
      default -> Integer.toString(expected);
    };

    return expectedVerifier.equals(verifier);
  }

  private static GenerationContext context(long seed) {
    return new GenerationContext(seed, null,
        new CountingOutputStream(new ByteArrayOutputStream()),
        GeneratorRegistry.defaultRegistry());
  }
}
