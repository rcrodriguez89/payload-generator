package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;
import ni.org.jneurosoft.payload.generator.data.RealisticData;

public final class EmailGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    String firstName = context.getCurrentValue("firstName");
    if (firstName == null) {
      firstName = RealisticData.FIRST_NAMES.get(context.randomInt(0, RealisticData.FIRST_NAMES.size() - 1));
      context.putCurrentValue("firstName", firstName);
    }

    String lastName = context.getCurrentValue("lastName");
    if (lastName == null) {
      lastName = RealisticData.LAST_NAMES.get(context.randomInt(0, RealisticData.LAST_NAMES.size() - 1));
      context.putCurrentValue("lastName", lastName);
    }

    String domain = resolveDomain(node, context);
    int randomNumber = context.randomInt(10, 9999);
    return normalize(firstName) + "." + normalize(lastName) + randomNumber + "@" + domain;
  }

  private static String resolveDomain(JsonNode node, GenerationContext context) {
    String overrideDomain = node.path("domain").asText("").trim();
    if (!overrideDomain.isEmpty()) {
      return overrideDomain;
    }

    int idx = context.randomInt(0, RealisticData.EMAIL_DOMAINS.size() - 1);
    return RealisticData.EMAIL_DOMAINS.get(idx);
  }

  private static String normalize(String input) {
    return input.toLowerCase().replaceAll("[^a-z0-9]", "");
  }
}
