package com.yahoo.container.plugin.classanalysis;

import java.util.Optional;

/**
 * The package
 *
 * @author gjoranv
 */
record PackageInfo(String name, Optional<ExportPackageAnnotation> exportPackage, boolean isPublicApi) {

    PackageInfo hasExportPackageOrElse(PackageInfo other) {
        return exportPackage().isPresent() ? this : other;
    }

}
