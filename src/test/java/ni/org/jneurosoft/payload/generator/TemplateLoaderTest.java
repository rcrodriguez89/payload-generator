package ni.org.jneurosoft.payload.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateLoaderTest {

  @TempDir
  Path tempDir;

  @Test
  void loadShouldFailWhenTemplateDoesNotExist() {
    Path missing = tempDir.resolve("missing.yaml");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> TemplateLoader.load(missing));
    assertTrue(ex.getMessage().contains("no encontrado"));
  }

  @Test
  void loadShouldFailWhenTemplateIsEmpty() throws IOException {
    Path empty = tempDir.resolve("empty.yaml");
    Files.writeString(empty, "");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> TemplateLoader.load(empty));
    assertTrue(ex.getMessage().contains("vacio"));
  }

  @Test
  void loadShouldParseValidYaml() throws IOException {
    Path valid = tempDir.resolve("valid.yaml");
    Files.writeString(valid, "type: object\nproperties:\n  id:\n    type: int\n    min: 1\n    max: 1\n");

    JsonNode node = TemplateLoader.load(valid);

    assertEquals("object", node.path("type").asText());
    assertTrue(node.path("properties").has("id"));
  }
}
