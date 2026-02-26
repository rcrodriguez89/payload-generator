package ni.org.jneurosoft.payload.generator;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class TemplateLoader {

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private TemplateLoader() {
  }

  public static JsonNode load(Path path) throws IOException {
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("Template no encontrado: " + path);
    }

    JsonNode node;
    try (Reader reader = Files.newBufferedReader(path)) {
      node = YAML_MAPPER.readTree(reader);
    }

    if (node == null || node.isNull() || node.isMissingNode() || node.isEmpty()) {
      throw new IllegalArgumentException("Template YAML vacio: " + path);
    }
    return node;
  }
}
