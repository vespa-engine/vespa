// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final ObjectMapper mapper = new ObjectMapper();

    @JsonProperty("mainBundle") public final BundleClasspathMapping mainBundle;
    @JsonProperty("providedDependencies") public final List<BundleClasspathMapping> providedDependencies;

    @JsonCreator
    public ProjectBundleClassPaths(@JsonProperty("mainBundle") BundleClasspathMapping mainBundle,
                                   @JsonProperty("providedDependencies") List<BundleClasspathMapping> providedDependencies) {
        this.mainBundle = mainBundle;
        this.providedDependencies = providedDependencies;
    }

    public static void save(Path path, ProjectBundleClassPaths mappings) throws IOException {
        Files.createDirectories(path.getParent());
        mapper.writeValue(path.toFile(), mappings);
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
        @JsonProperty("bundleSymbolicName") public final String bundleSymbolicName;
        @JsonProperty("classPathElements") public final List<String> classPathElements;

        @JsonCreator
        public BundleClasspathMapping(@JsonProperty("bundleSymbolicName") String bundleSymbolicName,
                                      @JsonProperty("classPathElements") List<String> classPathElements) {
            this.bundleSymbolicName = bundleSymbolicName;
            this.classPathElements = classPathElements;
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
