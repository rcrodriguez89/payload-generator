package ni.org.jneurosoft.payload.generator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

public final class PayloadGeneratorApplication {

  private PayloadGeneratorApplication() {
  }

  public static void main(String[] args) {
    try {
      CliOptions options = CliOptions.parse(args);
      run(options);
    } catch (Exception ex) {
      System.err.println(ex.getMessage());
      System.err.println(CliOptions.usage());
      System.exit(1);
    }
  }

  private static void run(CliOptions options) throws IOException {
    JsonNode template = TemplateLoader.load(options.template());
    GeneratorRegistry registry = GeneratorRegistry.defaultRegistry();
    Path outPath = options.resolveOutPath();

    Path parent = outPath.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(outPath));
        CountingOutputStream cos = new CountingOutputStream(bos);
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(cos)) {

      if (options.pretty()) {
        jsonGenerator.useDefaultPrettyPrinter();
      }

      GenerationContext context = new GenerationContext(options.seed(), options.maxBytes(), cos, registry);
      StreamingPayloadWriter.write(template, jsonGenerator, context);
      jsonGenerator.flush();
    }
  }
}
