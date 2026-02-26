package ni.org.jneurosoft.payload.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CliOptionsTest {

  @Test
  void parseShouldReadFlagsAndUseLastSizeArgument() {
    CliOptions options = CliOptions.parse(new String[] {
        "--template", "template.yaml",
        "--out", "payload.json",
        "--seed", "123",
        "--max-bytes", "100",
        "--max-kbs", "2",
        "--max-mbs", "1",
        "--pretty"
    });

    assertEquals("template.yaml", options.template().toString());
    assertEquals("payload.json", options.out().toString());
    assertEquals(123L, options.seed());
    assertTrue(options.pretty());
    assertEquals(1_048_576L, options.maxBytes());
  }

  @Test
  void parseShouldRespectLastLimitFlagBetweenKbAndMb() {
    CliOptions options = CliOptions.parse(new String[] {
        "--template", "a.yaml",
        "--out", "b.json",
        "--max-mbs", "1",
        "--max-kbs", "1"
    });

    assertEquals(1_024L, options.maxBytes());
  }

  @Test
  void resolveOutPathShouldUseGeneratedPayloadFolderWhenOutHasNoParent() {
    CliOptions options = new CliOptions(
        Path.of("/tmp/sample/template.yaml"),
        Path.of("payload.json"),
        null,
        false,
        null);

    Path resolved = options.resolveOutPath();
    Path expected = Path.of("/tmp/sample/template.yaml")
        .getParent()
        .resolve("generated-payload")
        .resolve("payload.json");
    assertEquals(expected, resolved);
  }

  @Test
  void resolveOutPathShouldKeepOriginalWhenOutAlreadyHasDirectory() {
    CliOptions options = new CliOptions(
        Path.of("/tmp/sample/template.yaml"),
        Path.of("out/payload.json"),
        null,
        false,
        null);

    assertEquals(Path.of("out/payload.json"), options.resolveOutPath());
  }

  @Test
  void parseShouldRejectUnknownFlag() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--template", "a", "--out", "b", "--bad"}));

    assertTrue(ex.getMessage().contains("Parametro no soportado"));
  }

  @Test
  void parseShouldFailWhenTemplateMissing() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--out", "b.json"}));

    assertTrue(ex.getMessage().contains("--template"));
  }

  @Test
  void parseShouldFailWhenOutMissing() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--template", "a.yaml"}));

    assertTrue(ex.getMessage().contains("--out"));
  }

  @Test
  void parseShouldFailWhenFlagValueMissing() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--template", "a.yaml", "--out"}));

    assertTrue(ex.getMessage().contains("Falta valor"));
  }

  @Test
  void parseShouldFailForNegativeOrInvalidSize() {
    IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--template", "a.yaml", "--out", "b.json", "--max-kbs", "abc"}));
    assertTrue(invalid.getMessage().contains("Valor invalido"));

    IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {"--template", "a.yaml", "--out", "b.json", "--max-kbs", "-1"}));
    assertTrue(negative.getMessage().contains(">= 0"));
  }

  @Test
  void parseShouldFailForOverflowInSizeMultiplier() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> CliOptions.parse(new String[] {
            "--template", "a.yaml",
            "--out", "b.json",
            "--max-mbs", String.valueOf(Long.MAX_VALUE)
        }));

    assertTrue(ex.getMessage().contains("demasiado grande"));
  }

  @Test
  void usageShouldMentionAllSizeFlags() {
    String usage = CliOptions.usage();

    assertTrue(usage.contains("--max-bytes"));
    assertTrue(usage.contains("--max-kbs"));
    assertTrue(usage.contains("--max-mbs"));
    assertTrue(usage.contains("generated-payload"));
  }
}
