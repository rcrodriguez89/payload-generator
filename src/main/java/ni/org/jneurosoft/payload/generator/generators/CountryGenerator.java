package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;
import ni.org.jneurosoft.payload.generator.data.RealisticData;

public final class CountryGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    int idx = context.randomInt(0, RealisticData.COUNTRIES.size() - 1);
    String country = RealisticData.COUNTRIES.get(idx);
    context.putCurrentValue("country", country);
    return country;
  }
}
