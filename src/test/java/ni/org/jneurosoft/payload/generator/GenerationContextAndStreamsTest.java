package ni.org.jneurosoft.payload.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GenerationContextAndStreamsTest {

  @Test
  void countingOutputStreamShouldCountBytesForSingleAndArrayWrites() throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    CountingOutputStream cos = new CountingOutputStream(bos);

    cos.write('A');
    cos.write(new byte[] {'B', 'C', 'D'}, 0, 3);

    Assertions.assertEquals(4L, cos.getBytesWritten());
    Assertions.assertEquals("ABCD", bos.toString(StandardCharsets.UTF_8));
  }

  @Test
  void generationContextShouldHandleRangesAndRandomHelpers() {
    GenerationContext context = newContext(123L, null);

    Assertions.assertEquals(5L, context.randomLong(5L, 5L));
    Assertions.assertTrue(context.randomLong(1L, 2L) >= 1L);
    Assertions.assertEquals(7, context.randomInt(7, 7));
    Assertions.assertEquals(2.5d, context.randomDouble(2.5d, 2.5d));

    long anyLong = context.randomLong(Long.MIN_VALUE, Long.MAX_VALUE);
    Assertions.assertTrue(anyLong >= Long.MIN_VALUE);
    Assertions.assertTrue(anyLong <= Long.MAX_VALUE);

    Assertions.assertNotNull(context.random());
    context.randomBoolean();
  }

  @Test
  void generationContextShouldSupportDefaultSeedWhenNull() {
    GenerationContext context = newContext(null, null);
    long value = context.randomLong(1L, 10L);
    Assertions.assertTrue(value >= 1L && value <= 10L);
  }

  @Test
  void generationContextShouldRejectInvalidRanges() {
    GenerationContext context = newContext(1L, null);

    Assertions.assertThrows(IllegalArgumentException.class, () -> context.randomLong(10L, 5L));
    Assertions.assertThrows(IllegalArgumentException.class, () -> context.randomInt(10, 5));
    Assertions.assertThrows(IllegalArgumentException.class, () -> context.randomDouble(5.0, 1.0));
  }

  @Test
  void generationContextShouldHandleNullsAndSequenceWrap() {
    GenerationContext context = newContext(7L, null);

    Assertions.assertTrue(context.shouldWriteNull(1.0));
    Assertions.assertFalse(context.shouldWriteNull(0.0));

    Assertions.assertEquals(3L, context.nextSequence("id", 3L, 4L));
    Assertions.assertEquals(4L, context.nextSequence("id", 3L, 4L));
    Assertions.assertEquals(3L, context.nextSequence("id", 3L, 4L));

    context.putCurrentValue("country", "Peru");
    Assertions.assertEquals("Peru", context.getCurrentValue("country"));
  }

  @Test
  void generationContextShouldTrackBytesAgainstGlobalLimit() throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    CountingOutputStream cos = new CountingOutputStream(bos);
    GenerationContext context = new GenerationContext(5L, 3L, cos, GeneratorRegistry.defaultRegistry());

    Assertions.assertTrue(context.hasGlobalMaxBytes());
    Assertions.assertFalse(context.reachedGlobalMaxBytes());

    cos.write(new byte[] {'x', 'y'}, 0, 2);
    Assertions.assertEquals(2L, context.bytesWritten());
    Assertions.assertFalse(context.reachedGlobalMaxBytes());

    cos.write('z');
    Assertions.assertTrue(context.reachedGlobalMaxBytes());
    Assertions.assertNotNull(context.generatorRegistry());
  }

  @Test
  void generationContextForkShouldShareStateAndUseIndependentOutputLimit() throws IOException {
    ByteArrayOutputStream mainBos = new ByteArrayOutputStream();
    CountingOutputStream mainCos = new CountingOutputStream(mainBos);
    GenerationContext parent = new GenerationContext(10L, 1L, mainCos, GeneratorRegistry.defaultRegistry());

    mainCos.write('a');
    Assertions.assertTrue(parent.reachedGlobalMaxBytes());

    CountingOutputStream nestedCos = new CountingOutputStream(new ByteArrayOutputStream());
    GenerationContext nested = parent.forkForOutput(nestedCos);

    Assertions.assertFalse(nested.hasGlobalMaxBytes());
    Assertions.assertFalse(nested.reachedGlobalMaxBytes());

    parent.putCurrentValue("k", "v");
    Assertions.assertEquals("v", nested.getCurrentValue("k"));

    Assertions.assertEquals(1L, parent.nextSequence("s", 1L, 9L));
    Assertions.assertEquals(2L, nested.nextSequence("s", 1L, 9L));

    nestedCos.write(new byte[] {'b', 'c'}, 0, 2);
    Assertions.assertEquals(2L, nested.bytesWritten());
    Assertions.assertEquals(1L, parent.bytesWritten());
  }

  private static GenerationContext newContext(Long seed, Long maxBytes) {
    return new GenerationContext(
        seed,
        maxBytes,
        new CountingOutputStream(new ByteArrayOutputStream()),
        GeneratorRegistry.defaultRegistry());
  }
}
