// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class PackageInfoTest {

    private static final ExportPackageAnnotation EXPORT = new ExportPackageAnnotation(0, 0, 0, "");

    private static PackageInfo exportOnly(String name) {
        return new PackageInfo(name, Optional.of(EXPORT), false);
    }

    private static PackageInfo exportAndPublic(String name) {
        return new PackageInfo(name, Optional.of(EXPORT), true);
    }

    private static PackageInfo noExport(String name) {
        return new PackageInfo(name, Optional.empty(), false);
    }

    @Test
    void public_api_is_preserved_when_other_has_it() {
        var result = exportOnly("p").hasExportPackageOrElse(exportAndPublic("p"));
        assertTrue(result.isPublicApi());
        assertTrue(result.exportPackage().isPresent());
    }

    @Test
    void public_api_is_preserved_when_this_has_it() {
        var result = exportAndPublic("p").hasExportPackageOrElse(exportOnly("p"));
        assertTrue(result.isPublicApi());
        assertTrue(result.exportPackage().isPresent());
    }

    @Test
    void public_api_is_preserved_when_both_have_it() {
        var result = exportAndPublic("p").hasExportPackageOrElse(exportAndPublic("p"));
        assertTrue(result.isPublicApi());
    }

    @Test
    void not_public_when_neither_has_it() {
        var result = exportOnly("p").hasExportPackageOrElse(exportOnly("p"));
        assertFalse(result.isPublicApi());
    }

    @Test
    void this_export_annotation_takes_precedence() {
        var thisExport = new ExportPackageAnnotation(1, 2, 3, "qualifier");
        var thisInfo = new PackageInfo("p", Optional.of(thisExport), false);
        var result = thisInfo.hasExportPackageOrElse(exportAndPublic("p"));
        assertEquals(thisExport, result.exportPackage().get());
        assertTrue(result.isPublicApi());
    }

    @Test
    void returns_other_when_this_has_no_export() {
        var result = noExport("p").hasExportPackageOrElse(exportAndPublic("p"));
        assertTrue(result.isPublicApi());
        assertTrue(result.exportPackage().isPresent());
    }

    @Test
    void returns_other_when_this_has_no_export_and_other_is_not_public() {
        var result = noExport("p").hasExportPackageOrElse(exportOnly("p"));
        assertFalse(result.isPublicApi());
        assertTrue(result.exportPackage().isPresent());
    }

}
