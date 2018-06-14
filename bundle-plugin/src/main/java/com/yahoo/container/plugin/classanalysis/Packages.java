// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import java.util.HashSet;
import java.util.Set;

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
