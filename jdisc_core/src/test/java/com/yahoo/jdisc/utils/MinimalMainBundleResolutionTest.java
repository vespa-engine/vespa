// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.utils;

import com.yahoo.jdisc.core.FelixFramework;
import com.yahoo.jdisc.core.FelixParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests automatic bundle resolution functionality in MinimalMain.
 *
 * @author bjorncs
 */
class MinimalMainBundleResolutionTest {

    @Test
    void testBundleIndexingAndResolution(@TempDir Path tempDir) throws Exception {
        var bundlesDir = tempDir.resolve("bundles");
        Files.createDirectories(bundlesDir);

        createBundle(bundlesDir.resolve("bundle-dependency.jar"),
                    "test.bundle.dependency",
                    "1.0.0",
                    "com.test.dependency",
                    null);

        createBundle(bundlesDir.resolve("bundle-main.jar"),
                    "test.bundle.main",
                    "1.0.0",
                    "com.test.main",
                    "com.test.dependency");

        createBundle(bundlesDir.resolve("vespajlib.jar"),
                    "test.bundle.blacklisted",
                    "1.0.0",
                    "com.test.blacklisted",
                    null);

        var indexPath = tempDir.resolve("bundle-index.xml");
        var blacklistPattern = Pattern.compile("vespajlib\\.jar");
        var bundleIndexer = new BundleIndexer(bundlesDir, blacklistPattern);
        var createdIndexPath = bundleIndexer.createIndex(List.of(), indexPath);

        assertTrue(Files.exists(createdIndexPath), "Index file should be created");

        var felixCacheDir = tempDir.resolve("felix-cache");
        Files.createDirectories(felixCacheDir);
        var framework = new FelixFramework(
                new FelixParams()
                        .setLoggerEnabled(false)
                        .setCachePath(felixCacheDir.toString()));
        framework.start();

        try {
            var resolver = new BundleResolver(framework.bundleContext(), indexPath);
            var resolved = resolver.resolve("test.bundle.main");

            assertNotNull(resolved, "Resolved bundles should not be null");
            assertEquals(2, resolved.size(), "Should resolve exactly 2 bundles (main + dependency)");

            var hasDependency = resolved.stream().anyMatch(path -> path.contains("bundle-dependency.jar"));
            var hasMain = resolved.stream().anyMatch(path -> path.contains("bundle-main.jar"));
            assertTrue(hasDependency, "Dependency bundle should be in resolved list");
            assertTrue(hasMain, "Main bundle should be in resolved list");
        } finally {
            framework.stop();
        }
    }

    private void createBundle(Path jarPath, String symbolicName, String version,
                             String exportPackage, String importPackage) throws Exception {
        var manifest = new Manifest();
        var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.putValue("Bundle-ManifestVersion", "2");
        attributes.putValue("Bundle-SymbolicName", symbolicName);
        attributes.putValue("Bundle-Version", version);
        if (exportPackage != null) {
            attributes.putValue("Export-Package", exportPackage);
        }
        if (importPackage != null) {
            attributes.putValue("Import-Package", importPackage);
        }

        try (var ignored = new JarOutputStream(new FileOutputStream(jarPath.toFile()), manifest)) {}
    }
}
