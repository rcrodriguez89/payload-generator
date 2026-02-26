package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;
import ni.org.jneurosoft.payload.generator.data.RealisticData;

public final class LastNameGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    int idx = context.randomInt(0, RealisticData.LAST_NAMES.size() - 1);
    String value = RealisticData.LAST_NAMES.get(idx);
    context.putCurrentValue("lastName", value);
    return value;
  }
}
