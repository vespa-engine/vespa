// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle;

import com.yahoo.container.plugin.osgi.ExportPackages;
import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class AnalyzeBundleTest {
    private final List<Export> exports;
    private final Map<String, Export> exportsByPackageName;

    File jarDir = new File("src/test/resources/jar");

    public AnalyzeBundleTest() {
        File notOsgi = new File(jarDir, "notAOsgiBundle.jar");
        File simple = new File(jarDir, "simple1.jar");
        exports = AnalyzeBundle.exportedPackagesAggregated(List.of(notOsgi, simple));
        exportsByPackageName = ExportPackages.exportsByPackageName(this.exports);
    }

    private File jarFile(String name) {
        return new File(jarDir, name);
    }

    @Test
    void require_that_non_osgi_bundles_are_ignored() {
        assertFalse(exportsByPackageName.containsKey("com.yahoo.sample.exported.package.ignored"));
    }

    @Test
    void require_that_exports_are_retrieved_from_manifest_in_jars() {
        assertEquals(1, exportsByPackageName.keySet().size());
        assertTrue(exportsByPackageName.containsKey("com.yahoo.sample.exported.package"));
    }

    @Test
    void exported_class_names_can_be_retrieved() {
        assertEquals(ExportPackages.packageNames(exports), exports.get(0).getPackageNames().stream().collect(Collectors.toSet()));
    }

    @Test
    void require_that_invalid_exports_throws_exception() {
        try {
            AnalyzeBundle.exportedPackages(jarFile("errorExport.jar"));
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid manifest in bundle 'src/test/resources/jar/errorExport.jar'"));
            assertTrue(e.getCause().getMessage().startsWith("Failed parsing Export-Package"), e.getCause().getMessage());
        }
    }
}
