package ni.org.jneurosoft.payload.generator.generators;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;
import ni.org.jneurosoft.payload.generator.data.RealisticData;

public final class CityGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    String country = context.getCurrentValue("country");
    if (country == null || !RealisticData.CITIES_BY_COUNTRY.containsKey(country)) {
      country = RealisticData.COUNTRIES.get(context.randomInt(0, RealisticData.COUNTRIES.size() - 1));
      context.putCurrentValue("country", country);
    }

    List<String> cities = RealisticData.CITIES_BY_COUNTRY.get(country);
    return cities.get(context.randomInt(0, cities.size() - 1));
  }
}
