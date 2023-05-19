package com.yahoo.container.plugin.classanalysis;

import java.util.Optional;

/**
 * The package
 *
 * @author gjoranv
 */
record PackageInfo(String name, Optional<ExportPackageAnnotation> exportPackage, boolean isPublicApi) {

    PackageInfo {
        if (exportPackage.isEmpty() && isPublicApi) {
            throw new IllegalArgumentException("Package %s is declared PublicApi, but is not exported.".formatted(name));
        }
    }

    PackageInfo hasExportPackageOrElse(PackageInfo other) {
        return exportPackage().isPresent() ? this : other;
    }

}
