// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import java.util.Optional;
import java.util.Set;

/**
 * The result of analyzing a .class file.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ClassFileMetaData {
    private final String name;
    private final Set<String> referencedClasses;
    private final Optional<ExportPackageAnnotation> exportPackage;

    public ClassFileMetaData(String name, Set<String> referencedClasses, Optional<ExportPackageAnnotation> exportPackage) {
        this.name = name;
        this.referencedClasses = referencedClasses;
        this.exportPackage = exportPackage;
    }

    public String getName() {
        return name;
    }

    public Set<String> getReferencedClasses() {
        return referencedClasses;
    }

    public Optional<ExportPackageAnnotation> getExportPackage() {
        return exportPackage;
    }
}
