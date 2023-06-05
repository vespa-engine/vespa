// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin;

import com.yahoo.vespa.config.VespaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the bundle jar file built and its manifest.
 *
 * @author Tony Vaagenes
 */
public class BundleTest {
    static final String TEST_BUNDLE_PATH = System.getProperty("test.bundle.path", ".") + "/";

    // If bundle-plugin-test is compiled in a mvn command that also built dependencies, e.g. 'defaults',
    // the artifact is defaults.jar, otherwise the installed and versioned artifact
    // is used: defaults-7-SNAPSHOT.jar or e.g. defaults-7.123.45.jar.
    private static final String snapshotOrVersionOrNone = "(-\\d+((-SNAPSHOT)|((\\.\\d+(\\.\\d+)?)?))?)?\\.jar";

    private JarFile jarFile;
    private Attributes mainAttributes;

    @BeforeEach
    public void setup() {
        try {
            File componentJar = findBundleJar("main");
            jarFile = new JarFile(componentJar);
            Manifest manifest = jarFile.getManifest();
            mainAttributes = manifest.getMainAttributes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static File findBundleJar(String bundleName) {
        Path bundlePath = Paths.get(TEST_BUNDLE_PATH, bundleName + "-bundle.jar");
        if (! Files.exists(bundlePath)) {
            throw new RuntimeException("Failed finding component jar file: " + bundlePath);
        }

        return bundlePath.toFile();
    }

    @Test
    void require_that_bundle_version_is_added_to_manifest() {
        String bundleVersion = mainAttributes.getValue("Bundle-Version");

        // Because of snapshot builds, we can only verify the major version.
        int majorBundleVersion = Integer.valueOf(bundleVersion.substring(0, bundleVersion.indexOf('.')));
        assertEquals(VespaVersion.major, majorBundleVersion);
    }

    @Test
    void require_that_bundle_symbolic_name_matches_pom_artifactId() {
        assertEquals("main", mainAttributes.getValue("Bundle-SymbolicName"));
    }

    @Test
    void require_that_manifest_contains_inferred_imports() {
        String importPackage = mainAttributes.getValue("Import-Package");

        // From SimpleSearcher
        assertTrue(importPackage.contains("com.yahoo.prelude.hitfield"));

        // From SimpleSearcher2
        assertTrue(importPackage.contains("com.yahoo.processing"));
        assertTrue(importPackage.contains("com.yahoo.metrics.simple"));
        assertTrue(importPackage.contains("com.google.inject"));
    }

    @Test
    void require_that_manifest_contains_manual_imports() {
        String importPackage = mainAttributes.getValue("Import-Package");

        assertTrue(importPackage.contains("manualImport.withoutVersion"));
        assertTrue(importPackage.contains("manualImport.withVersion;version=\"12.3.4\""));

        for (int i = 1; i <= 2; ++i)
            assertTrue(importPackage.contains("multiple.packages.with.the.same.version" + i + ";version=\"[1,2)\""));
    }

    @Test
    void require_that_manifest_contains_exports() {
        String exportPackage = mainAttributes.getValue("Export-Package");
        assertTrue(exportPackage.contains("com.yahoo.test;version=1.2.3.RELEASE"));
    }

    @Test
    void require_that_manifest_contains_public_api_for_this_bundle_and_embedded_bundles() {
        assertEquals("com.yahoo.test,com.yahoo.vespa.defaults", mainAttributes.getValue("X-JDisc-PublicApi-Package"));
    }

    @Test
    void require_that_manifest_contains_bundle_class_path() {
        String bundleClassPath = mainAttributes.getValue("Bundle-ClassPath");
        assertTrue(bundleClassPath.contains(".,"));

        Pattern jrtPattern = Pattern.compile("dependencies/defaults" + snapshotOrVersionOrNone);
        assertTrue(jrtPattern.matcher(bundleClassPath).find(), "Bundle class path did not contain 'defaults''.");
    }

    @Test
    void require_that_component_jar_file_contains_compile_artifacts() {
        String requiredDep = "dependencies/defaults";
        Pattern depPattern = Pattern.compile(requiredDep + snapshotOrVersionOrNone);
        ZipEntry depEntry = null;

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            var e = entries.nextElement();
            if (e.getName().startsWith(requiredDep)) {
                if (depPattern.matcher(e.getName()).matches()) {
                    depEntry = e;
                    break;
                }
            }
        }
        assertNotNull(depEntry, "Component jar file did not contain 'defaults' dependency.");
    }


    @Test
    void require_that_web_inf_url_is_propagated_to_the_manifest() {
        String webInfUrl = mainAttributes.getValue("WebInfUrl");
        assertTrue(webInfUrl.contains("/WEB-INF/web.xml"));
    }

}
