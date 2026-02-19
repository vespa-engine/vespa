// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.abicheck.mojo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;

public class LeakageCheckerTest {

    @Test
    public void filters_vespa_non_public_api_types_as_leakages() {
        Map<String, Set<String>> referencedTypes = Map.of(
                "com.yahoo.publicapi.Foo", Set.of(
                        "java.lang.String",
                        "com.yahoo.publicapi.Bar",
                        "com.yahoo.internal.Baz",
                        "org.apache.commons.Util"));
        Set<String> publicApiPackages = Set.of("com.yahoo.publicapi");

        Map<String, Set<String>> leakages = LeakageChecker.filterLeakages(referencedTypes, publicApiPackages);

        assertThat(leakages.size(), equalTo(1));
        assertThat(leakages.get("com.yahoo.publicapi.Foo"), equalTo(Set.of("com.yahoo.internal.Baz")));
    }

    @Test
    public void excludes_classes_with_no_leaked_types() {
        Map<String, Set<String>> referencedTypes = Map.of(
                "com.yahoo.publicapi.Clean", Set.of(
                        "java.lang.Object",
                        "com.yahoo.publicapi.Bar"));
        Set<String> publicApiPackages = Set.of("com.yahoo.publicapi");

        Map<String, Set<String>> leakages = LeakageChecker.filterLeakages(referencedTypes, publicApiPackages);

        assertThat(leakages.isEmpty(), equalTo(true));
    }

    @Test
    public void detects_ai_vespa_leakages() {
        Map<String, Set<String>> referencedTypes = Map.of(
                "ai.vespa.publicapi.Foo", Set.of("ai.vespa.internal.Bar"));
        Set<String> publicApiPackages = Set.of("ai.vespa.publicapi");

        Map<String, Set<String>> leakages = LeakageChecker.filterLeakages(referencedTypes, publicApiPackages);

        assertThat(leakages.size(), equalTo(1));
        assertThat(leakages.get("ai.vespa.publicapi.Foo"), equalTo(Set.of("ai.vespa.internal.Bar")));
    }

    @Test
    public void compare_matching_leakages_passes() {
        Log log = mock(Log.class);
        Map<String, Set<String>> spec = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));
        Map<String, Set<String>> actual = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));

        var result = LeakageChecker.compareLeakages(spec, actual, log);
        assertThat(result.matches(), equalTo(true));
        assertThat(result.hasNewLeakages(), equalTo(false));
        assertThat(result.hasStaleEntries(), equalTo(false));
        verifyNoInteractions(log);
    }

    @Test
    public void compare_detects_new_leaking_class() {
        Log log = mock(Log.class);
        Map<String, Set<String>> spec = Map.of();
        Map<String, Set<String>> actual = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));

        var result = LeakageChecker.compareLeakages(spec, actual, log);
        assertThat(result.matches(), equalTo(false));
        assertThat(result.hasNewLeakages(), equalTo(true));
        assertThat(result.hasStaleEntries(), equalTo(false));
        verify(log).error("New leakage in class com.yahoo.Foo: [com.yahoo.internal.Bar]");
    }

    @Test
    public void compare_detects_stale_class_in_spec() {
        Log log = mock(Log.class);
        Map<String, Set<String>> spec = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));
        Map<String, Set<String>> actual = Map.of();

        var result = LeakageChecker.compareLeakages(spec, actual, log);
        assertThat(result.matches(), equalTo(false));
        assertThat(result.hasNewLeakages(), equalTo(false));
        assertThat(result.hasStaleEntries(), equalTo(true));
        verify(log).error("Stale class in leakage spec (no longer has leakages): com.yahoo.Foo");
    }

    @Test
    public void compare_detects_new_leaked_type_in_existing_class() {
        Log log = mock(Log.class);
        Map<String, Set<String>> spec = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));
        Map<String, Set<String>> actual = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar", "com.yahoo.internal.Baz")));

        var result = LeakageChecker.compareLeakages(spec, actual, log);
        assertThat(result.matches(), equalTo(false));
        assertThat(result.hasNewLeakages(), equalTo(true));
        assertThat(result.hasStaleEntries(), equalTo(false));
        verify(log).error("Class com.yahoo.Foo: New leaked type: com.yahoo.internal.Baz");
    }

    @Test
    public void compare_detects_stale_leaked_type_in_existing_class() {
        Log log = mock(Log.class);
        Map<String, Set<String>> spec = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar", "com.yahoo.internal.Baz")));
        Map<String, Set<String>> actual = Map.of(
                "com.yahoo.Foo", new TreeSet<>(Set.of("com.yahoo.internal.Bar")));

        var result = LeakageChecker.compareLeakages(spec, actual, log);
        assertThat(result.matches(), equalTo(false));
        assertThat(result.hasNewLeakages(), equalTo(false));
        assertThat(result.hasStaleEntries(), equalTo(true));
        verify(log).error("Class com.yahoo.Foo: Stale leaked type (remove from spec): com.yahoo.internal.Baz");
    }

    @Test
    public void packageOf_extracts_package() {
        assertThat(LeakageChecker.packageOf("com.yahoo.internal.Foo"), equalTo("com.yahoo.internal"));
        assertThat(LeakageChecker.packageOf("Foo"), equalTo(""));
    }

    @Test
    public void isVespaPackage_recognizes_vespa_prefixes() {
        assertThat(LeakageChecker.isVespaPackage("com.yahoo.foo"), equalTo(true));
        assertThat(LeakageChecker.isVespaPackage("ai.vespa.foo"), equalTo(true));
        assertThat(LeakageChecker.isVespaPackage("java.lang"), equalTo(false));
        assertThat(LeakageChecker.isVespaPackage("org.apache.commons"), equalTo(false));
    }
}
