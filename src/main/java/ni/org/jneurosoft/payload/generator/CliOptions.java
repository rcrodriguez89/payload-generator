package ni.org.jneurosoft.payload.generator;

import java.nio.file.Path;

public record CliOptions(Path template, Path out, Long seed, boolean pretty, Long maxBytes) {

  public static final String DEFAULT_OUTPUT_DIR = "generated-payload";
  private static final long ONE_KB = 1024L;
  private static final long ONE_MB = 1024L * 1024L;

  public static CliOptions parse(String[] args) {
    Path template = null;
    Path out = null;
    Long seed = null;
    boolean pretty = false;
    Long maxBytes = null;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      switch (arg) {
        case "--template" -> template = Path.of(requireValue(args, ++i, "--template"));
        case "--out" -> out = Path.of(requireValue(args, ++i, "--out"));
        case "--seed" -> seed = Long.parseLong(requireValue(args, ++i, "--seed"));
        case "--pretty" -> pretty = true;
        case "--max-bytes" -> maxBytes = parseSize(requireValue(args, ++i, "--max-bytes"), 1L, "--max-bytes");
        case "--max-kbs" -> maxBytes = parseSize(requireValue(args, ++i, "--max-kbs"), ONE_KB, "--max-kbs");
        case "--max-mbs" -> maxBytes = parseSize(requireValue(args, ++i, "--max-mbs"), ONE_MB, "--max-mbs");
        default -> throw new IllegalArgumentException("Parametro no soportado: " + arg);
      }
    }

    if (template == null) {
      throw new IllegalArgumentException("Falta parametro obligatorio --template");
    }
    if (out == null) {
      throw new IllegalArgumentException("Falta parametro obligatorio --out");
    }

    return new CliOptions(template, out, seed, pretty, maxBytes);
  }

  public Path resolveOutPath() {
    if (out.isAbsolute() || out.getParent() != null) {
      return out;
    }

    Path templateParent = template.toAbsolutePath().getParent();
    Path base = templateParent == null ? Path.of(".") : templateParent;
    return base.resolve(DEFAULT_OUTPUT_DIR).resolve(out).normalize();
  }

  public static String usage() {
    return "Uso: java -jar payload-generator.jar --template template.yaml --out payload.json "
        + "[--seed 123] [--pretty] [--max-bytes 10485760|--max-kbs 10240|--max-mbs 10] "
        + "(si --out no incluye carpeta, se usa generated-payload/)";
  }

  private static String requireValue(String[] args, int index, String flag) {
    if (index >= args.length) {
      throw new IllegalArgumentException("Falta valor para " + flag);
    }
    return args[index];
  }

  private static long parseSize(String rawValue, long multiplier, String flag) {
    long value;
    try {
      value = Long.parseLong(rawValue);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Valor invalido para " + flag + ": " + rawValue, ex);
    }

    if (value < 0) {
      throw new IllegalArgumentException("El valor de " + flag + " debe ser >= 0");
    }

    try {
      return Math.multiplyExact(value, multiplier);
    } catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Valor demasiado grande para " + flag + ": " + rawValue, ex);
    }
  }
}
