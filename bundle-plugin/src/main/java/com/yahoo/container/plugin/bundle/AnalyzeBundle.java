// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.bundle;

import com.yahoo.container.plugin.osgi.ExportPackageParser;
import com.yahoo.container.plugin.osgi.ExportPackages.Export;
import com.yahoo.container.plugin.util.JarFiles;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class AnalyzeBundle {
    public static class PublicPackages {
        public final List<Export> exports;
        public final List<String> globals;

        public PublicPackages(List<Export> exports, List<String> globals) {
            this.exports = exports;
            this.globals = globals;
        }
    }

    public static PublicPackages publicPackagesAggregated(Collection<File> jarFiles) {
        List<Export> exports = new ArrayList<>();
        List<String> globals = new ArrayList<>();

        for (File jarFile : jarFiles) {
            PublicPackages pp = publicPackages(jarFile);
            exports.addAll(pp.exports);
            globals.addAll(pp.globals);
        }
        return new PublicPackages(exports, globals);
    }

    public static PublicPackages publicPackages(File jarFile) {
        try {
            Optional<Manifest> jarManifest = JarFiles.getManifest(jarFile);
            if (jarManifest.isPresent()) {
                Manifest manifest = jarManifest.get();
                if (isOsgiManifest(manifest)) {
                    return new PublicPackages(parseExports(manifest), parseGlobals(manifest));
                }
            }
            return new PublicPackages(Collections.emptyList(), Collections.emptyList());
        } catch (Exception e) {
            throw new RuntimeException(String.format("Invalid manifest in bundle '%s'", jarFile.getPath()), e);
        }
    }

    public static Optional<String> bundleSymbolicName(File jarFile) {
        return JarFiles.getManifest(jarFile).flatMap(AnalyzeBundle::getBundleSymbolicName);
    }

    private static List<Export> parseExportsFromAttribute(Manifest manifest, String attributeName) {
        return getMainAttributeValue(manifest, attributeName).map(ExportPackageParser::parseExports).orElseGet(() -> new ArrayList<>());
    }

    private static List<Export> parseExports(Manifest jarManifest) {
        return parseExportsFromAttribute(jarManifest, "Export-Package");
    }

    private static List<String> parseGlobals(Manifest manifest) {
        List<Export> globals = parseExportsFromAttribute(manifest, "Global-Package");

        for (Export export : globals) {
            if (export.getParameters().isEmpty() == false) {
                throw new RuntimeException("Parameters not valid for Global-Package.");
            }
        }

        return globals.stream().flatMap(g -> g.getPackageNames().stream()).collect(Collectors.toList());
    }

    private static Optional<String> getMainAttributeValue(Manifest manifest, String attributeName) {
        return Optional.ofNullable(manifest.getMainAttributes().getValue(attributeName));
    }

    private static boolean isOsgiManifest(Manifest mf) {
        return getBundleSymbolicName(mf).isPresent();
    }

    private static Optional<String> getBundleSymbolicName(Manifest mf) {
        return getMainAttributeValue(mf, "Bundle-SymbolicName");
    }
}
