// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ExportPackages.Parameter;
import com.yahoo.container.plugin.osgi.ImportPackages.Import;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImportPackageTest {
    private static final String PACKAGE_NAME = "com.yahoo.exported";
    private final Set<String> referencedPackages = Collections.singleton(PACKAGE_NAME);
    private final Map<String, Export> exports = exportByPackageName(new Export(List.of(PACKAGE_NAME), Collections.emptyList()));
    private final Map<String, Export> exportsWithVersion = exportByPackageName(
            new Export(List.of(PACKAGE_NAME), List.of(new Parameter("version", "1.3"))));

    private static Map<String, Export> exportByPackageName(Export export) {
        return ExportPackages.exportsByPackageName(List.of(export));
    }

    @Test
    void require_that_non_implemented_import_with_matching_export_is_included() {
        Set<Import> imports = calculateImports(referencedPackages, Set.of(), exports);
        assertEquals(1, imports.stream().filter(imp -> imp.packageName().equals(PACKAGE_NAME) && imp.version().isEmpty()).count());
    }

    @Test
    void require_that_non_implemented_import_without_matching_export_is_excluded() {
        Set<Import> imports = calculateImports(referencedPackages, Set.of(), Map.of());
        assertTrue(imports.isEmpty());
    }

    @Test
    void require_that_implemented_import_with_matching_export_is_excluded() {
        Set<Import> imports = calculateImports(referencedPackages, referencedPackages, exports);
        assertTrue(imports.isEmpty());
    }

    @Test
    void require_that_version_is_included() {
        Set<Import> imports = calculateImports(referencedPackages, Set.of(), exportsWithVersion);
        assertEquals(1, imports.stream().filter(imp -> imp.packageName().equals(PACKAGE_NAME) && imp.version().equals("1.3")).count());
    }

    @Test
    void require_that_all_versions_up_to_the_next_major_version_is_in_range() {
        assertEquals("[1.2,2)", new Import("foo", Optional.of("1.2")).importVersionRange().get());
    }

    // TODO: Detecting guava packages should be based on bundle-symbolicName, not package name.
    @Test
    void require_that_for_guava_all_future_major_versions_are_in_range() {
        Optional<String> rangeWithInfiniteUpperLimit = Optional.of("[18.1," + ImportPackages.INFINITE_VERSION + ")");
        assertEquals(rangeWithInfiniteUpperLimit, new Import("com.google.common", Optional.of("18.1")).importVersionRange());
        assertEquals(rangeWithInfiniteUpperLimit, new Import("com.google.common.foo", Optional.of("18.1")).importVersionRange());
        assertEquals(Optional.of("[18.1,19)"), new Import("com.google.commonality", Optional.of("18.1")).importVersionRange());
    }

    @Test
    void require_that_none_version_gives_non_version_range() {
        assertTrue(new Import("foo", Optional.empty()).importVersionRange().isEmpty());
    }

    @Test
    void require_that_exception_is_thrown_when_major_component_is_non_numeric() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Import("foo", Optional.of("1notValid.2"));
        });
    }

    @Test
    void require_that_osgi_import_supports_missing_version() {
        assertEquals("com.yahoo.exported", new Import("com.yahoo.exported", Optional.empty()).asOsgiImport());
    }

    @Test
    void require_that_osgi_import_version_range_includes_all_versions_from_the_current_up_to_the_next_major_version() {
        assertEquals("com.yahoo.exported;version=\"[1.2,2)\"", new Import("com.yahoo.exported", Optional.of("1.2")).asOsgiImport());
    }

    @Test
    void require_that_osgi_import_version_range_ignores_qualifier() {
        assertEquals("com.yahoo.exported;version=\"[1.2.3,2)\"", new Import("com.yahoo.exported", Optional.of("1.2.3.qualifier")).asOsgiImport());
    }

    private static Set<Import> calculateImports(Set<String> referencedPackages,
                                                Set<String> implementedPackages,
                                                Map<String, Export> exportedPackages) {
        return new HashSet<>(ImportPackages.calculateImports(referencedPackages, implementedPackages, exportedPackages).values());
    }
}
