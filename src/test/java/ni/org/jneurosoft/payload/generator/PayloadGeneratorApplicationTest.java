package ni.org.jneurosoft.payload.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PayloadGeneratorApplicationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TempDir
  Path tempDir;

  @Test
  void mainShouldGenerateOutputFile() throws Exception {
    Path template = tempDir.resolve("template.yaml");
    Path output = tempDir.resolve("payload.json");

    Files.writeString(template, """
        type: array
        count: 2
        item:
          type: object
          properties:
            id:
              type: long
              min: 1
              max: 100
              seq: true
            email:
              generator: email
            createdAt:
              type: instant
              min: 0
              max: 0
        """);

    PayloadGeneratorApplication.main(new String[] {
        "--template", template.toString(),
        "--out", output.toString(),
        "--seed", "15",
        "--pretty",
        "--max-kbs", "10"
    });

    assertTrue(Files.exists(output));
    JsonNode payload = MAPPER.readTree(output.toFile());
    assertEquals(2, payload.size());
    assertEquals(1L, payload.get(0).get("id").asLong());
    assertEquals(2L, payload.get(1).get("id").asLong());
    assertTrue(payload.get(0).get("email").asText().contains("@"));
  }

  @Test
  void mainShouldWriteIntoGeneratedPayloadFolderWhenOutHasNoDirectory() throws Exception {
    Path template = tempDir.resolve("template-default-out.yaml");
    Files.writeString(template, """
        type: object
        properties:
          id:
            type: int
            min: 7
            max: 7
        """);

    PayloadGeneratorApplication.main(new String[] {
        "--template", template.toString(),
        "--out", "payload-default.json",
        "--seed", "3"
    });

    Path expected = tempDir.resolve(CliOptions.DEFAULT_OUTPUT_DIR).resolve("payload-default.json");
    assertTrue(Files.exists(expected));
    JsonNode payload = MAPPER.readTree(expected.toFile());
    assertEquals(7, payload.get("id").asInt());
  }

  @Test
  void privateConstructorsShouldBeInvocableForUtilityClasses() throws Exception {
    invokePrivateConstructor(PayloadGeneratorApplication.class);
    invokePrivateConstructor(TemplateLoader.class);
    invokePrivateConstructor(StreamingPayloadWriter.class);
  }

  private static void invokePrivateConstructor(Class<?> type)
      throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
    Constructor<?> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    constructor.newInstance();
  }
}
