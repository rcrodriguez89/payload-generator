package ni.org.jneurosoft.payload.generator;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import ni.org.jneurosoft.payload.generator.generators.AccountNumberGenerator;
import ni.org.jneurosoft.payload.generator.generators.ChileanRutGenerator;
import ni.org.jneurosoft.payload.generator.generators.CityGenerator;
import ni.org.jneurosoft.payload.generator.generators.CountryGenerator;
import ni.org.jneurosoft.payload.generator.generators.EmailGenerator;
import ni.org.jneurosoft.payload.generator.generators.FirstNameGenerator;
import ni.org.jneurosoft.payload.generator.generators.FullNameGenerator;
import ni.org.jneurosoft.payload.generator.generators.LastNameGenerator;

public final class GeneratorRegistry {

  private final Map<String, ValueGenerator> generators = new HashMap<>();

  public static GeneratorRegistry defaultRegistry() {
    GeneratorRegistry registry = new GeneratorRegistry();
    registry.register("firstName", new FirstNameGenerator());
    registry.register("lastName", new LastNameGenerator());
    registry.register("fullName", new FullNameGenerator());
    registry.register("email", new EmailGenerator());
    registry.register("country", new CountryGenerator());
    registry.register("city", new CityGenerator());
    registry.register("chileanRut", new ChileanRutGenerator());
    registry.register("accountNumber", new AccountNumberGenerator());
    return registry;
  }

  public void register(String name, ValueGenerator generator) {
    generators.put(name, generator);
  }

  public String generate(String name, JsonNode node, GenerationContext context, String path) {
    ValueGenerator generator = generators.get(name);
    if (generator == null) {
      throw new IllegalArgumentException("Generator no soportado: " + name);
    }
    return generator.generate(node, context, path);
  }
}
