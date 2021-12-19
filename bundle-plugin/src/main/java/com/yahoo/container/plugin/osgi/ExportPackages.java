// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ExportPackages {
    public static class Export {
        private final List<String> packageNames;
        private final List<Parameter> parameters;

        public Export(List<String> packageNames, List<Parameter> parameters) {
            this.packageNames = packageNames;
            this.parameters = parameters;
        }

        public Optional<String> version() {
            for (Parameter par : parameters) {
                if ("version".equals(par.getName())) {
                    return Optional.of(par.getValue());
                }
            }
            return Optional.empty();
        }

        public List<String> getPackageNames() {
            return packageNames;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

    }

    public static class Parameter {
        private final String name;
        private final String value;

        public Parameter(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Parameter parameter = (Parameter) o;
            return Objects.equals(name, parameter.name) && Objects.equals(value, parameter.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }
    }

    public static Set<String> packageNames(Collection<Export> exports) {
        return exports.stream()
                .map(Export::getPackageNames)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    public static Map<String, Export> exportsByPackageName(Collection<Export> exports) {
        Map<String, Export> ret = new HashMap<>();
        for (Export export : exports) {
            for (String packageName : export.getPackageNames()) {
                //ensure that earlier exports of a package overrides later exports.
                ret.computeIfAbsent(packageName, ign -> export);
            }
        }
        return ret;
    }
}
