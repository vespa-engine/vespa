package com.yahoo.container.plugin.classanalysis;

import java.util.Optional;

/**
 * Info about a Java package deduced from class analysis.
 *
 * @author gjoranv
 */
record PackageInfo(String name, Optional<ExportPackageAnnotation> exportPackage, boolean isPublicApi) {

    /**
     * Returns this if it has an ExportPackage annotation, otherwise returns the other.
     * Used to combine objects, where this should take precedence over the other.
     */
    PackageInfo hasExportPackageOrElse(PackageInfo other) {
        return exportPackage().isPresent() ? this : other;
    }

}
