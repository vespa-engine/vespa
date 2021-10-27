<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# abi-check-plugin

Maven plugin for ensuring project ABI stability.

## Theory of operation

The project artifact JAR is scanned for class files. The resulting package tree is scanned for
packages annotated with configured annotation denoting a package that is considered public ABI.
Classes in those packages are scanned for their visible ABI and the result is compared against
expected ABI (stored in a JSON file) and possible discrepancies are reported.

## What is considered visible ABI

Visible ABI is considered to be classes, methods and fields that are accessible from other JAR
files without use of reflection or other tricks.

## Setup

### Add plugin to build

Add the plugin to `<plugins>` in the project `pom.xml`, with an execution of `abicheck` goal. This
goal has to be executed in a phase where the project artifact JAR is available, the recommended
phase is `package`.

Example:
```
<plugin>
  <groupId>com.yahoo.vespa</groupId>
  <artifactId>abi-check-plugin</artifactId>
  <configuration>
    <publicApiAnnotation>com.yahoo.api.annotations.PublicApi</publicApiAnnotation>
  </configuration>
  <executions>
    <execution>
      <phase>package</phase>
        <goals>
          <goal>abicheck</goal>
        </goals>
    </execution>
  </executions>
</plugin>
```

### Configuration parameters

 * **publicApiAnnotation** (required)  
   Fully qualified class name of the annotation that denotes a public API package.
 * **specFileName** (optional, default: `abi-spec.json`)  
   File name relative to project root from which to read the expected ABI spec from.
   
## Updating the expected ABI spec

To automatically generate the expected ABI spec from the current ABI of the project, define
property `abicheck.writeSpec` when running the relevant phase.

Example: `mvn package -Dabicheck.writeSpec`
