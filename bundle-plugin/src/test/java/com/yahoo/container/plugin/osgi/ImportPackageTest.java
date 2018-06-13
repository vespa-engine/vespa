// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.osgi.ExportPackages.Parameter;
import com.yahoo.container.plugin.osgi.ImportPackages.Import;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImportPackageTest {
    private static final String PACKAGE_NAME = "com.yahoo.exported";
    private Set<String> referencedPackages = Collections.singleton(PACKAGE_NAME);
    private Map<String, Export> exports = exportByPackageName(new Export(singletonList(PACKAGE_NAME), Collections.emptyList()));
    private Map<String, Export> exportsWithVersion = exportByPackageName(
            new Export(singletonList(PACKAGE_NAME), singletonList(new Parameter("version", "1.3"))));

    private static Map<String, Export> exportByPackageName(Export export) {
        return ExportPackages.exportsByPackageName(singletonList(export));
    }

    @Test
    public void require_that_non_implemented_import_with_matching_export_is_included() {
        Set<Import> imports = calculateImports(referencedPackages, emptySet(), exports);
        assertThat(imports, contains(importMatching(PACKAGE_NAME, "")));
    }

    @Test
    public void require_that_non_implemented_import_without_matching_export_is_excluded() {
        Set<Import> imports = calculateImports(referencedPackages, emptySet(), emptyMap());
        assertThat(imports, empty());
    }

    @Test
    public void require_that_implemented_import_with_matching_export_is_excluded() {
        Set<Import> imports = calculateImports(referencedPackages, referencedPackages, exports);

        assertThat(imports, empty());
    }

    @Test
    public void require_that_version_is_included() {
        Set<Import> imports = calculateImports(referencedPackages, emptySet(), exportsWithVersion);

        assertThat(imports, contains(importMatching(PACKAGE_NAME, "1.3")));
    }

    @Test
    public void require_that_all_versions_up_to_the_next_major_version_is_in_range() {
        assertThat(new Import("foo", Optional.of("1.2")).importVersionRange().get(), is("[1.2,2)"));
    }

    // TODO: Detecting guava packages should be based on bundle-symbolicName, not package name.
    @Test
    public void require_that_for_guava_all_future_major_versions_are_in_range() {
        Optional<String> rangeWithInfiniteUpperLimit = Optional.of("[18.1," + ImportPackages.INFINITE_VERSION + ")");
        assertThat(new Import("com.google.common", Optional.of("18.1")).importVersionRange(), is(rangeWithInfiniteUpperLimit));
        assertThat(new Import("com.google.common.foo", Optional.of("18.1")).importVersionRange(), is(rangeWithInfiniteUpperLimit));
        assertThat(new Import("com.google.commonality", Optional.of("18.1")).importVersionRange(), is(Optional.of("[18.1,19)")));
    }

    @Test
    public void require_that_none_version_gives_non_version_range() {
        assertThat(new Import("foo", Optional.empty()).importVersionRange(), is(Optional.empty()));
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void require_that_exception_is_thrown_when_major_component_is_non_numeric() {
        expectedException.expect(IllegalArgumentException.class);
        new Import("foo", Optional.of("1notValid.2"));
    }

    @Test
    public void require_that_osgi_import_supports_missing_version() {
        assertThat(new Import("com.yahoo.exported", Optional.empty()).asOsgiImport(), is("com.yahoo.exported"));
    }

    @Test
    public void require_that_osgi_import_version_range_includes_all_versions_from_the_current_up_to_the_next_major_version() {
        assertThat(new Import("com.yahoo.exported", Optional.of("1.2")).asOsgiImport(), is("com.yahoo.exported;version=\"[1.2,2)\""));
    }

    @Test
    public void require_that_osgi_import_version_range_ignores_qualifier() {
        assertThat(new Import("com.yahoo.exported", Optional.of("1.2.3.qualifier")).asOsgiImport(),
                is("com.yahoo.exported;version=\"[1.2.3,2)\""));
    }

    private static Set<Import> calculateImports(Set<String> referencedPackages, Set<String> implementedPackages,
            Map<String, Export> exportedPackages) {
        return new HashSet<>(ImportPackages.calculateImports(referencedPackages, implementedPackages, exportedPackages).values());
    }

    private static TypeSafeMatcher<Import> importMatching(String packageName, String version) {
        return new TypeSafeMatcher<Import>() {
            @Override
            protected boolean matchesSafely(Import anImport) {
                return anImport.packageName().equals(packageName) && anImport.version().equals(version);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("an Import of package ").appendValue(packageName).appendText(" with version ").appendValue(version);
            }
        };
    }
}
