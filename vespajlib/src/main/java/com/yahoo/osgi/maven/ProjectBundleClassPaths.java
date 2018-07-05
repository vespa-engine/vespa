// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi.maven;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the bundles in a maven project and the classpath elements
 * corresponding to code that would end up in the bundle.
 *
 * @author Tony Vaagenes
 * @author bjorncs
 */

public class ProjectBundleClassPaths {
    public static final String CLASSPATH_MAPPINGS_FILENAME = "bundle-plugin.bundle-classpath-mappings.json";

    public final BundleClasspathMapping mainBundle;
    public final List<BundleClasspathMapping> providedDependencies;

    public ProjectBundleClassPaths(BundleClasspathMapping mainBundle,
                                   List<BundleClasspathMapping> providedDependencies) {
        this.mainBundle = mainBundle;
        this.providedDependencies = providedDependencies;
    }

    public static void save(Path path, ProjectBundleClassPaths mappings) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
            save(out, mappings);
        }
    }

    static void save(OutputStream out, ProjectBundleClassPaths mappings) throws IOException {
        Slime slime = new Slime();
        Cursor rootCursor = slime.setObject();
        Cursor mainBundleCursor = rootCursor.setObject("mainBundle");
        BundleClasspathMapping.save(mainBundleCursor, mappings.mainBundle);
        Cursor dependenciesCursor = rootCursor.setArray("providedDependencies");
        mappings.providedDependencies
                .forEach(d -> BundleClasspathMapping.save(dependenciesCursor.addObject(), d));
        new JsonFormat(false).encode(out, slime);
    }

    public static ProjectBundleClassPaths load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return load(bytes);
    }

    static ProjectBundleClassPaths load(byte[] bytes) {
        Slime slime = new Slime();
        new JsonDecoder().decode(slime, bytes);
        Inspector inspector = slime.get();
        BundleClasspathMapping mainBundle = BundleClasspathMapping.load(inspector.field("mainBundle"));
        Inspector dependenciesInspector = inspector.field("providedDependencies");
        List<BundleClasspathMapping> providedDependencies = new ArrayList<>();
        for (int i = 0; i < dependenciesInspector.entries(); i++) {
            providedDependencies.add(BundleClasspathMapping.load(dependenciesInspector.entry(i)));
        }
        return new ProjectBundleClassPaths(mainBundle, providedDependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectBundleClassPaths that = (ProjectBundleClassPaths) o;
        return Objects.equals(mainBundle, that.mainBundle) &&
                Objects.equals(providedDependencies, that.providedDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mainBundle, providedDependencies);
    }

    public static class BundleClasspathMapping {
        public final String bundleSymbolicName;
        public final List<String> classPathElements;

        public BundleClasspathMapping(String bundleSymbolicName,
                                      List<String> classPathElements) {
            this.bundleSymbolicName = bundleSymbolicName;
            this.classPathElements = classPathElements;
        }

        static void save(Cursor rootCursor, BundleClasspathMapping mapping) {
            rootCursor.setString("bundleSymbolicName", mapping.bundleSymbolicName);
            Cursor arrayCursor = rootCursor.setArray("classPathElements");
            mapping.classPathElements.forEach(arrayCursor::addString);
        }

        static BundleClasspathMapping load(Inspector inspector) {
            String bundleSymoblicName = inspector.field("bundleSymbolicName").asString();
            Inspector elementsInspector = inspector.field("classPathElements");
            List<String> classPathElements = new ArrayList<>();
            for (int i = 0; i < elementsInspector.entries(); i++) {
                classPathElements.add(elementsInspector.entry(i).asString());
            }
            return new BundleClasspathMapping(bundleSymoblicName, classPathElements);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BundleClasspathMapping that = (BundleClasspathMapping) o;
            return Objects.equals(bundleSymbolicName, that.bundleSymbolicName) &&
                    Objects.equals(classPathElements, that.classPathElements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bundleSymbolicName, classPathElements);
        }
    }

}
