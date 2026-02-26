package ni.org.jneurosoft.payload.generator.generators;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.GenerationContext;
import ni.org.jneurosoft.payload.generator.ValueGenerator;
import ni.org.jneurosoft.payload.generator.data.RealisticData;

public final class FullNameGenerator implements ValueGenerator {

  @Override
  public String generate(JsonNode node, GenerationContext context, String path) {
    String firstName = context.getCurrentValue("firstName");
    if (firstName == null) {
      firstName = RealisticData.FIRST_NAMES.get(context.randomInt(0, RealisticData.FIRST_NAMES.size() - 1));
      context.putCurrentValue("firstName", firstName);
    }

    String lastName = context.getCurrentValue("lastName");
    if (lastName == null) {
      lastName = RealisticData.LAST_NAMES.get(context.randomInt(0, RealisticData.LAST_NAMES.size() - 1));
      context.putCurrentValue("lastName", lastName);
    }

    return firstName + " " + lastName;
  }
}
