// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.maven.plugin.enforcer;

import com.yahoo.vespa.maven.plugin.enforcer.EnforceDependenciesAllProjects.Dependency;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.yahoo.vespa.maven.plugin.enforcer.EnforceDependenciesAllProjects.validateDependencies;
import static com.yahoo.vespa.maven.plugin.enforcer.EnforceDependenciesAllProjects.writeDependencySpec;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class EnforceDependenciesAllProjectsTest {

    @Test
    void succeeds_dependencies_matches_spec() {
        SortedSet<Dependency> dependencies = new TreeSet<>(Set.of(
                Dependency.fromString("com.example:foo:1.2.3"),
                Dependency.fromString("com.example:bar:2.3.4")));
        Path specFile = Paths.get("src/test/resources/allowed-dependencies.txt");
        assertDoesNotThrow(() -> validateDependencies(dependencies, specFile));
    }

    @Test
    void fails_on_forbidden_dependency() {
        SortedSet<Dependency> dependencies = new TreeSet<>(Set.of(
                Dependency.fromString("com.example:foo:1.2.3"),
                Dependency.fromString("com.example:bar:2.3.4"),
                Dependency.fromString("com.example:foobar:3.4.5")));
        Path specFile = Paths.get("src/test/resources/allowed-dependencies.txt");
        var exception = assertThrows(EnforcerRuleException.class,
                                     () -> validateDependencies(dependencies, specFile));
        String expectedErrorMessage =
                """
                The dependency enforcer failed:
                Forbidden dependencies:
                 - com.example:foobar:3.4.5
                Maven dependency validation failed. To update dependency spec run 'mvn enforcer:enforce -DdependencyEnforcer.writeSpec'""";
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void fails_on_missing_dependency() {
        SortedSet<Dependency> dependencies = new TreeSet<>(Set.of(
                Dependency.fromString("com.example:foo:1.2.3")));
        Path specFile = Paths.get("src/test/resources/allowed-dependencies.txt");
        var exception = assertThrows(EnforcerRuleException.class,
                                     () -> validateDependencies(dependencies, specFile));
        String expectedErrorMessage =
                """
                The dependency enforcer failed:
                Removed dependencies:
                 - com.example:bar:2.3.4
                Maven dependency validation failed. To update dependency spec run 'mvn enforcer:enforce -DdependencyEnforcer.writeSpec'""";
        assertEquals(expectedErrorMessage, exception.getMessage());
    }

    @Test
    void writes_valid_spec_file(@TempDir Path tempDir) throws EnforcerRuleException, IOException {
        SortedSet<Dependency> dependencies = new TreeSet<>(Set.of(
                Dependency.fromString("com.example:foo:1.2.3"),
                Dependency.fromString("com.example:bar:2.3.4")));
        Path outputFile = tempDir.resolve("allowed-dependencies.txt");
        writeDependencySpec(outputFile, dependencies);
        assertEquals(
                Files.readString(Paths.get("src/test/resources/allowed-dependencies.txt")),
                Files.readString(outputFile));

    }

}