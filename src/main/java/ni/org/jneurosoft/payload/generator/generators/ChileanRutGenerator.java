package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;

public final class ChileanRutGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    int rutBody = context.randomInt(1_000_000, 99_999_999);
    String verifier = calculateVerifierDigit(rutBody);
    boolean omitGroupSeparator = node.path("omitGroupSeparator").asBoolean(false);

    String formattedBody = omitGroupSeparator
        ? Integer.toString(rutBody)
        : formatRutWithGrouping(rutBody);

    return formattedBody + "-" + verifier;
  }

  private static String calculateVerifierDigit(int rutBody) {
    int sum = 0;
    int multiplier = 2;
    int value = rutBody;

    while (value > 0) {
      int digit = value % 10;
      sum += digit * multiplier;
      value /= 10;
      multiplier = (multiplier == 7) ? 2 : multiplier + 1;
    }

    int result = 11 - (sum % 11);
    if (result == 11) {
      return "0";
    }
    if (result == 10) {
      return "K";
    }
    return String.valueOf(result);
  }

  private static String formatRutWithGrouping(int rutBody) {
    String digits = Integer.toString(rutBody);
    StringBuilder reversed = new StringBuilder();
    int groupCount = 0;

    for (int i = digits.length() - 1; i >= 0; i--) {
      reversed.append(digits.charAt(i));
      groupCount++;
      if (groupCount == 3 && i > 0) {
        reversed.append('.');
        groupCount = 0;
      }
    }

    return reversed.reverse().toString();
  }
}
