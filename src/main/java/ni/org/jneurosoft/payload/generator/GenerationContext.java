package ni.org.jneurosoft.payload.generator;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

public final class GenerationContext {

  private static final long DEFAULT_SEED = 20260226L;
  private final SplittableRandom random;
  private final Long maxBytes;
  private final CountingOutputStream output;
  private final GeneratorRegistry generatorRegistry;
  private final Map<String, String> currentValues;
  private final Map<String, Long> sequences;

  public GenerationContext(Long seed, Long maxBytes, CountingOutputStream output, GeneratorRegistry generatorRegistry) {
    this(
        new SplittableRandom(seed == null ? DEFAULT_SEED : seed),
        maxBytes,
        output,
        generatorRegistry,
        new HashMap<>(),
        new HashMap<>());
  }

  private GenerationContext(
      SplittableRandom random,
      Long maxBytes,
      CountingOutputStream output,
      GeneratorRegistry generatorRegistry,
      Map<String, String> currentValues,
      Map<String, Long> sequences) {
    this.random = random;
    this.maxBytes = maxBytes;
    this.output = output;
    this.generatorRegistry = generatorRegistry;
    this.currentValues = currentValues;
    this.sequences = sequences;
  }

  public GenerationContext forkForOutput(CountingOutputStream nestedOutput) {
    return new GenerationContext(random, null, nestedOutput, generatorRegistry, currentValues, sequences);
  }

  public SplittableRandom random() {
    return random;
  }

  public long randomLong(long minInclusive, long maxInclusive) {
    if (maxInclusive < minInclusive) {
      throw new IllegalArgumentException("Rango invalido: min > max");
    }
    if (maxInclusive == minInclusive) {
      return minInclusive;
    }
    long bound = maxInclusive - minInclusive + 1;
    if (bound <= 0) {
      while (true) {
        long candidate = random.nextLong();
        if (candidate >= minInclusive && candidate <= maxInclusive) {
          return candidate;
        }
      }
    }
    return random.nextLong(bound) + minInclusive;
  }

  public int randomInt(int minInclusive, int maxInclusive) {
    if (maxInclusive < minInclusive) {
      throw new IllegalArgumentException("Rango invalido: min > max");
    }
    if (maxInclusive == minInclusive) {
      return minInclusive;
    }
    return random.nextInt(minInclusive, maxInclusive + 1);
  }

  public double randomDouble(double minInclusive, double maxExclusive) {
    if (maxExclusive < minInclusive) {
      throw new IllegalArgumentException("Rango invalido: min > max");
    }
    if (maxExclusive == minInclusive) {
      return minInclusive;
    }
    return random.nextDouble(minInclusive, maxExclusive);
  }

  public boolean randomBoolean() {
    return random.nextBoolean();
  }

  public boolean shouldWriteNull(double nullProbability) {
    return random.nextDouble() < nullProbability;
  }

  public long nextSequence(String path, long minInclusive, long maxInclusive) {
    long current = sequences.getOrDefault(path, minInclusive);
    long next = current + 1;
    if (next > maxInclusive) {
      next = minInclusive;
    }
    sequences.put(path, next);
    return current;
  }

  public void putCurrentValue(String key, String value) {
    currentValues.put(key, value);
  }

  public String getCurrentValue(String key) {
    return currentValues.get(key);
  }

  public GeneratorRegistry generatorRegistry() {
    return generatorRegistry;
  }

  public long bytesWritten() {
    return output.getBytesWritten();
  }

  public boolean hasGlobalMaxBytes() {
    return maxBytes != null;
  }

  public boolean reachedGlobalMaxBytes() {
    return maxBytes != null && bytesWritten() >= maxBytes;
  }
}
