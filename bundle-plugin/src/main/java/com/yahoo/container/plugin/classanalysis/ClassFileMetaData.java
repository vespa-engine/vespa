// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final PackageInfo packageInfo;

    public ClassFileMetaData(String name, Set<String> referencedClasses, PackageInfo packageInfo) {
        this.name = name;
        this.referencedClasses = referencedClasses;
        this.packageInfo = packageInfo;
    }

    public String getName() {
        return name;
    }

    public Set<String> getReferencedClasses() {
        return referencedClasses;
    }

    public PackageInfo packageInfo() {
        return packageInfo;
    }

    public Optional<ExportPackageAnnotation> getExportPackage() {
        return packageInfo.exportPackage();
    }

    public boolean isPublicApi() {
        return packageInfo.isPublicApi();
    }

}
