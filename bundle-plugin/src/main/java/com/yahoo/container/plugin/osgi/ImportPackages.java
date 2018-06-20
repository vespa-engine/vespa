// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ImportPackages {
    public static final int INFINITE_VERSION = 99999;
    private static final String GUAVA_BASE_PACKAGE = "com.google.common";

    public static class Import {
        private final String packageName;
        private final List<Integer> versionNumber;

        public Import(String packageName, Optional<String> version) {
            this.packageName = packageName;
            this.versionNumber = new ArrayList<>();

            if (version.isPresent()) {
                try {
                    Arrays.stream(version.get().split("\\.")).map(Integer::parseInt).limit(3).forEach(this.versionNumber::add);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            String.format("Invalid version number '%s' for package '%s'.", version.get(), packageName), e);
                }
            }
        }

        public Optional<Integer> majorVersion() {
            if (versionNumber.size() >= 1) {
                return Optional.of(versionNumber.get(0));
            } else {
                return Optional.empty();
            }
        }

        public String packageName() {
            return packageName;
        }

        public String version() {
            return versionNumber.stream().map(Object::toString).collect(Collectors.joining("."));
        }

        // TODO: Detecting guava packages should be based on Bundle-SymbolicName, not package name.
        public Optional<String> importVersionRange() {
            if (versionNumber.isEmpty()) {
                return Optional.empty();
            } else {
                int upperLimit = isGuavaPackage() ? INFINITE_VERSION // guava increases major version for each release
                        : versionNumber.get(0) + 1;
                return Optional.of(String.format("[%s,%d)", version(), upperLimit));
            }
        }

        public boolean isGuavaPackage() {
            return packageName.equals(GUAVA_BASE_PACKAGE) || packageName.startsWith(GUAVA_BASE_PACKAGE + ".");
        }

        public String asOsgiImport() {
            return packageName + importVersionRange().map(version -> ";version=\"" + version + '"').orElse("");
        }
    }

    public static Map<String, Import> calculateImports(Set<String> referencedPackages, Set<String> implementedPackages,
            Map<String, ExportPackages.Export> exportedPackages) {
        Map<String, Import> ret = new HashMap<>();
        for (String undefinedPackage : Sets.difference(referencedPackages, implementedPackages)) {
            ExportPackages.Export export = exportedPackages.get(undefinedPackage);
            if (export != null) {
                ret.put(undefinedPackage, new Import(undefinedPackage, version(export)));
            }
        }
        return ret;
    }

    private static Optional<String> version(ExportPackages.Export export) {
        for (ExportPackages.Parameter param : export.getParameters()) {
            if ("version".equals(param.getName())) {
                return Optional.of(param.getValue());
            }
        }
        return Optional.empty();
    }
}
