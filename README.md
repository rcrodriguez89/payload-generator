# payload-generator

CLI en Java 21 para generar JSON masivo a partir de un template YAML, con bajo uso de memoria usando Jackson Streaming API (`JsonGenerator`).

## Version recomendada de Gradle

Usa **Gradle 8.10.2** (compatible con Java 21).

## Build

```bash
./gradlew clean shadowJar
```

## Cobertura (JaCoCo)

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification
```

El proyecto falla si la cobertura de lineas es menor a `85%`.

## Calidad de codigo (Checkstyle, PMD, SpotBugs)

Plugins incorporados:

- `checkstyle`
- `pmd`
- `com.github.spotbugs`

Reglas utilizadas desde:

- `checks/checkstyle.xml`
- `checks/checkstyle-suppressions.xml`
- `checks/pmd-ruleset.xml`
- `checks/spotbugs-exclude.xml`

Comandos para ejecutar cada plugin:

```bash

# Checkstyle
./gradlew checkstyleMain checkstyleTest

# PMD
./gradlew pmdMain pmdTest

# SpotBugs
./gradlew spotbugsMain spotbugsTest
```

Comando unico para ejecutar todos los tasks de calidad:

```bash
./gradlew checkstyleMain checkstyleTest pmdMain pmdTest spotbugsMain spotbugsTest
```

Tambien puedes ejecutar todo el pipeline con:

```bash
./gradlew check
```

Reportes generados:

- `build/reports/checkstyle/main.html`
- `build/reports/checkstyle/test.html`
- `build/reports/pmd/main.html`
- `build/reports/pmd/test.html`
- `build/reports/spotbugs/main.html`
- `build/reports/spotbugs/test.html`

Fat jar generado en:

```text
build/libs/payload-generator.jar
```

> En Maven se usaria `maven-shade-plugin`; en Gradle se usa el equivalente `shadowJar`.

## Uso CLI

```bash
java -jar build/libs/payload-generator.jar \
  --template template.yaml \
  --out payload.json \
  --seed 123 \
  --max-bytes 10485760
  # o --max-kbs 10240
  # o --max-mbs 10
```

Parametros:

- `--template` (obligatorio)
- `--out` (obligatorio). Si no incluye carpeta (ej: `payload.json`), se crea en `generated-payload/` junto al template.
- `--seed` (opcional, reproducible)
- `--pretty` (opcional, default `false`)
- `--max-bytes` (opcional, aproximado)
- `--max-kbs` (opcional, aproximado, kilobytes)
- `--max-mbs` (opcional, aproximado, megabytes)

Si se indican dos o tres (`--max-bytes`, `--max-kbs`, `--max-mbs`), se usa solo el ultimo parametro especificado.

## Tipos soportados

Escalares:

- `string` (`length`, `charset` opcional)
- `int` (`min`/`max`)
- `long` (`min`/`max`, `seq=true`)
- `decimal` (`min`/`max`, `scale`)
- `boolean`
- `uuid`
- `instant` (ISO-8601)
- `date` (YYYY-MM-DD)
- `nullable` + `nullProbability`
- `default` (si existe, se usa este valor y no se calcula aleatoriamente)

Fechas y tiempo:

- `pastOrPresent: true`: genera valores pasados o presentes (maximo = fecha/hora actual UTC)
- `future: true`: genera solo valores futuros
- `pastOrPresent` y `future` no se pueden usar juntos en el mismo campo
- Aplica para `date` e `instant`

Complejos:

- `object` (`properties`)
- `array` (`count` o `untilBytes`, `item`)
- `stringTemplate` (similar a `object`, pero serializa el objeto generado como string JSON escapado)

## Generadores realistas

Soportados en template con `generator:`:

- `firstName`
- `lastName`
- `fullName`
- `email` (soporta override con `domain`; si no se especifica usa lista interna de dominios)
- `country`
- `city` (dependiente de pais)
- `chileanRut` (RUT chileno valido aleatorio)
  - `omitGroupSeparator`: `true` para omitir los puntos de agrupacion (default `false`)
- `accountNumber` (numero de cuenta aleatorio configurable con checksum opcional)

Datasets incluidos:

- 60 nombres
- 60 apellidos
- 47 paises
- 10 ciudades por pais
- 20 dominios de email reconocidos

## Ejemplo minimo de template

```yaml
type: array
count: 1000
item:
  type: object
  properties:
    fullName:
      generator: fullName
    email:
      generator: email
      domain: acme.com
    rut:
      generator: chileanRut
      omitGroupSeparator: true
    accountNumber:
      generator: accountNumber
      length: 16
      prefix: "52"
      checkDigit: luhn
      grouping: 4
      separator: "-"
    auditPayload:
      type: stringTemplate
      properties:
        source:
          type: string
          default: backend
        status:
          type: string
          default: OK
        amount:
          type: decimal
          min: 10
          max: 10
          scale: 2
    createdAt:
      type: instant
      pastOrPresent: true
    nextDueDate:
      type: date
      future: true
    plan:
      type: string
      default: basic
```

### accountNumber options

- `length`: longitud total (default `12`)
- `prefix`: prefijo fijo opcional
- `format`: `numeric` (default) o `alphanumeric`
- `checkDigit`: `none`, `luhn`, `mod11`
- `grouping`: cantidad de caracteres por bloque en la salida (opcional)
- `separator`: separador para bloques cuando se usa `grouping` (default `-`)

Notas:

- `checkDigit` solo aplica para `format: numeric`.
- Si no especificas `checkDigit`, se usa `none`.

### stringTemplate behavior

- Requiere `properties` igual que `object`.
- Genera internamente un objeto JSON y luego lo escribe como `string`.
- En la salida final, veras un texto JSON escapado.

## Arquitectura

- `ValueGenerator`: contrato para generadores personalizados.
- `GeneratorRegistry`: registro/resolucion de generadores.
- `GenerationContext`: estado de generacion (seed, random, valores actuales, secuencias, bytes escritos).
- `StreamingPayloadWriter`: recorre el template y escribe directamente al `JsonGenerator`.

## Extender generadores

1. Crear clase que implemente `ValueGenerator`.
2. Implementar `generate(JsonNode node, GenerationContext context, String path)`.
3. Registrar en `GeneratorRegistry.defaultRegistry()`:

```java
registry.register("miGenerator", new MiGenerator());
```

4. Usar en YAML:

```yaml
miCampo:
  generator: miGenerator
```
