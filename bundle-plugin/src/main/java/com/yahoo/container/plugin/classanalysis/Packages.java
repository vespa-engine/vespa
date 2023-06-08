// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import com.yahoo.container.plugin.osgi.ImportPackages;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility methods related to packages.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Packages {

    public static class PackageMetaData {
        public final Set<String> definedPackages;
        public final Set<String> referencedExternalPackages;

        public PackageMetaData(Set<String> definedPackages, Set<String> referencedExternalPackages) {
            this.definedPackages = definedPackages;
            this.referencedExternalPackages = referencedExternalPackages;
        }
    }

    public static String packageName(String fullClassName) {
        int index = fullClassName.lastIndexOf('.');
        if (index == -1) {
            return "";
        } else {
            return fullClassName.substring(0, index);
        }
    }

    /**
     * Returns the imported Vespa packages that don't exist in the given set of allowed packages.
     */
    public static List<String> disallowedVespaImports(Map<String, ImportPackages.Import> imports, List<String> allowed) {
        if (imports == null || imports.isEmpty()) return List.of();

        var publicApi = allowed == null ? Set.of() : new HashSet<>(allowed);

        Set<String> yahooImports = imports.keySet().stream()
                .filter(pkg -> pkg.startsWith("com.yahoo") || pkg.startsWith("ai.vespa."))
                .collect(Collectors.toSet());

        List<String> disallowedImports = yahooImports.stream().collect(Collectors.groupingBy(publicApi::contains)).get(false);
        return disallowedImports == null ? List.of() : disallowedImports;
    }

    public static PackageMetaData analyzePackages(Set<ClassFileMetaData> allClasses) {
        Set<String> definedPackages = new HashSet<>();
        Set<String> referencedPackages = new HashSet<>();
        for (ClassFileMetaData metaData : allClasses) {
            definedPackages.add(packageName(metaData.getName()));
            metaData.getReferencedClasses().forEach(className -> referencedPackages.add(packageName(className)));
        }
        referencedPackages.removeAll(definedPackages);
        return new PackageMetaData(definedPackages, referencedPackages);
    }

}
