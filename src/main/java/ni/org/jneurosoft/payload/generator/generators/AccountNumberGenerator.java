package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;

public final class AccountNumberGenerator implements ValueGenerator {

  private static final String DIGITS = "0123456789";
  private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    int length = node.path("length").asInt(12);
    String prefix = node.path("prefix").asText("");
    String format = node.path("format").asText("numeric").toLowerCase();
    String checkDigitMode = node.path("checkDigit").asText("none").toLowerCase();

    validateLength(length, prefix);
    validateCheckDigitMode(checkDigitMode);
    validateFormat(format);

    if (!"none".equals(checkDigitMode) && !"numeric".equals(format)) {
      throw new IllegalArgumentException("checkDigit requiere format=numeric para accountNumber");
    }

    int checkDigitLength = "none".equals(checkDigitMode) ? 0 : 1;
    int randomLength = length - prefix.length() - checkDigitLength;
    if (randomLength < 0) {
      throw new IllegalArgumentException("length insuficiente para prefix + checkDigit en accountNumber");
    }

    String charset = "numeric".equals(format) ? DIGITS : ALPHANUMERIC;
    String raw = prefix + randomPart(context, randomLength, charset);

    if (!"none".equals(checkDigitMode)) {
      String digit = switch (checkDigitMode) {
        case "luhn" -> luhnDigit(raw);
        case "mod11" -> mod11Digit(raw);
        default -> throw new IllegalArgumentException("checkDigit no soportado: " + checkDigitMode);
      };
      raw = raw + digit;
    }

    int grouping = node.path("grouping").asInt(0);
    String separator = node.path("separator").asText("-");
    return grouping > 0 ? group(raw, grouping, separator) : raw;
  }

  private static void validateLength(int length, String prefix) {
    if (length <= 0) {
      throw new IllegalArgumentException("length debe ser > 0 para accountNumber");
    }
    if (prefix.length() > length) {
      throw new IllegalArgumentException("prefix no puede ser mayor que length para accountNumber");
    }
  }

  private static void validateCheckDigitMode(String checkDigitMode) {
    if (!"none".equals(checkDigitMode)
        && !"luhn".equals(checkDigitMode)
        && !"mod11".equals(checkDigitMode)) {
      throw new IllegalArgumentException(
          "checkDigit no soportado: " + checkDigitMode + ". Valores: none, luhn, mod11");
    }
  }

  private static void validateFormat(String format) {
    if (!"numeric".equals(format) && !"alphanumeric".equals(format)) {
      throw new IllegalArgumentException("format no soportado para accountNumber: " + format);
    }
  }

  private static String randomPart(GenerationContext context, int length, String charset) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int idx = context.randomInt(0, charset.length() - 1);
      builder.append(charset.charAt(idx));
    }
    return builder.toString();
  }

  private static String luhnDigit(String digits) {
    ensureNumeric(digits, "luhn");

    int sum = 0;
    boolean shouldDouble = true;
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

    int check = (10 - (sum % 10)) % 10;
    return Integer.toString(check);
  }

  private static String mod11Digit(String digits) {
    ensureNumeric(digits, "mod11");

    int sum = 0;
    int weight = 2;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      sum += d * weight;
      weight = (weight == 7) ? 2 : weight + 1;
    }

    int remainder = sum % 11;
    int check = (11 - remainder) % 11;
    if (check == 10) {
      check = 0;
    }
    return Integer.toString(check);
  }

  private static String group(String raw, int grouping, String separator) {
    if (separator == null) {
      separator = "-";
    }

    StringBuilder grouped = new StringBuilder(raw.length() + raw.length() / grouping);
    for (int i = 0; i < raw.length(); i++) {
      if (i > 0 && i % grouping == 0) {
        grouped.append(separator);
      }
      grouped.append(raw.charAt(i));
    }
    return grouped.toString();
  }

  private static void ensureNumeric(String value, String algorithm) {
    if (!value.matches("\\d+")) {
      throw new IllegalArgumentException("El algoritmo " + algorithm + " requiere cuenta numerica");
    }
  }
}
