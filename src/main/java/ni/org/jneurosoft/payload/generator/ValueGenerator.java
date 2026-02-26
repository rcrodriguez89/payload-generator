package ni.org.jneurosoft.payload.generator;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface ValueGenerator {

  String generate(JsonNode node, GenerationContext context, String path);
}
