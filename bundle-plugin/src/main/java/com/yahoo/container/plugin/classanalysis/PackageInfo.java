// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import java.util.Optional;

/**
 * Info about a Java package deduced from class analysis.
 *
 * @author gjoranv
 */
record PackageInfo(String name, Optional<ExportPackageAnnotation> exportPackage, boolean isPublicApi) {

    /**
     * Returns a PackageInfo with an ExportPackage annotation if either this or other has one.
     * If both have ExportPackage, this takes precedence, but isPublicApi is OR-ed from both.
     */
    PackageInfo hasExportPackageOrElse(PackageInfo other) {
        if (exportPackage().isPresent()) {
            boolean mergedPublicApi = isPublicApi || other.isPublicApi;
            return mergedPublicApi == isPublicApi ? this : new PackageInfo(name, exportPackage, true);
        }
        return other;
    }

}
