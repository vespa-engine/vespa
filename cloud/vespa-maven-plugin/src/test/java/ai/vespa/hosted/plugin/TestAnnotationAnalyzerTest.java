// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link TestAnnotationAnalyzer} against compiled fixtures under
 * {@code ai.vespa.hosted.plugin.fixtures}. The fixtures are real, compiled Java
 * classes. Pointing the analyzer at their {@code .class} files mirrors how
 * {@code GenerateTestDescriptorMojo} drives the analyzer in a real build.
 *
 * @author olaa
 */
class TestAnnotationAnalyzerTest {

    private static final String PKG = "ai.vespa.hosted.plugin.fixtures.";

    private static TestAnnotationAnalyzer analyzer;

    @BeforeAll
    static void analyze() throws URISyntaxException, IOException {
        Path testClasses = Path.of(TestAnnotationAnalyzerTest.class
                                           .getProtectionDomain().getCodeSource().getLocation().toURI());
        Path fixtures = testClasses.resolve("ai/vespa/hosted/plugin/fixtures");
        analyzer = new TestAnnotationAnalyzer();
        try (Stream<Path> files = Files.walk(fixtures)) {
            files.filter(f -> f.toString().endsWith(".class")).forEach(analyzer::visitClass);
        }
        analyzer.resolve();
    }

    @Test
    void direct_annotation() {
        assertTrue(analyzer.systemTests().contains(PKG + "Direct"));
    }

    @Test
    void subclass_of_annotated_base() {
        assertTrue(analyzer.systemTests().contains(PKG + "Base"));
        assertTrue(analyzer.systemTests().contains(PKG + "Child"),
                   "Child extends Base, must be detected via superclass walk");
    }

    @Test
    void meta_annotation() {
        assertTrue(analyzer.systemTests().contains(PKG + "Bearer"),
                   "Bearer is annotated with @MetaSystem, which is meta-annotated with @SystemTest");
    }

    @Test
    void subclass_via_meta_annotation_on_base() {
        assertTrue(analyzer.systemTests().contains(PKG + "MetaBase"));
        assertTrue(analyzer.systemTests().contains(PKG + "MetaChild"),
                   "MetaChild extends MetaBase whose annotation chain ends at @SystemTest");
    }

    @Test
    void annotation_declaration_is_not_bucketed() {
        // MetaSystem is itself meta-annotated with @SystemTest, but @interface declarations
        // must never be classified as tests.
        assertFalse(analyzer.systemTests().contains(PKG + "MetaSystem"));
    }

    @Test
    void multi_category_class() {
        assertTrue(analyzer.systemTests().contains(PKG + "BothKinds"));
        assertTrue(analyzer.stagingTests().contains(PKG + "BothKinds"));
    }

    @Test
    void unknown_super_does_not_break_resolution() {
        // UnknownSuper extends java.util.HashMap (outside test-classes); the superclass walk
        // should terminate cleanly and the class still be bucketed via its direct annotation.
        assertTrue(analyzer.systemTests().contains(PKG + "UnknownSuper"));
    }
}
