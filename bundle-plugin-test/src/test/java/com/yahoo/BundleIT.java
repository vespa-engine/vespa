// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo;

import com.yahoo.osgi.maven.ProjectBundleClassPaths;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * Verifies the bundle jar file built and its manifest.
 * @author Tony Vaagenes
 */
public class BundleIT {
    private JarFile jarFile;
    private Attributes mainAttributes;

    @Before
    public void setup() {
        try {
            File componentJar = findBundleJar();
            jarFile = new JarFile(componentJar);
            Manifest manifest = jarFile.getManifest();
            mainAttributes = manifest.getMainAttributes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File findBundleJar() {
        File[] componentFile = new File("target").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String fileName) {
                return fileName.endsWith("-deploy.jar") || fileName.endsWith("-jar-with-dependencies.jar");
            }
        });

        if (componentFile.length != 1) {
            throw new RuntimeException("Failed finding component jar file");
        }

        return componentFile[0];
    }

    @Test
    @Ignore
    public void require_that_manifest_version_matches_pom_version() {
        assertThat(mainAttributes.getValue("Bundle-Version"), is("5.1.0"));
    }

    @Test
    public void require_that_bundle_symbolic_name_matches_pom_artifactId() {
        assertThat(mainAttributes.getValue("Bundle-SymbolicName"), is("bundle-plugin-test"));
    }

    @Test
    public void require_that_manifest_contains_inferred_imports() {
        String importPackage = mainAttributes.getValue("Import-Package");
        assertThat(importPackage, containsString("com.yahoo.prelude.hitfield"));
        //Not available from jdisc at the moment, scope temporarily changed to compile.
        //assertThat(importPackage, containsString("org.json"));
    }

    @Test
    public void require_that_manifest_contains_manual_imports() {
        String importPackage = mainAttributes.getValue("Import-Package");

        assertThat(importPackage, containsString("manualImport.withoutVersion"));
        assertThat(importPackage, containsString("manualImport.withVersion;version=\"12.3.4\""));

        for (int i=1; i<=2; ++i)
            assertThat(importPackage, containsString("multiple.packages.with.the.same.version" + i + ";version=\"[1,2)\""));
    }

    @Test
    public void require_that_manifest_contains_exports() {
        String exportPackage = mainAttributes.getValue("Export-Package");
        assertThat(exportPackage, containsString("com.yahoo.test;version=1.2.3.RELEASE"));
    }

    @Test
    public void require_that_manifest_contains_bundle_class_path() {
        String bundleClassPath = mainAttributes.getValue("Bundle-ClassPath");
        assertThat(bundleClassPath, containsString(".,"));
        // If bundle-plugin-test is compiled in a mvn command that also built jrt,
        // the jrt artifact is jrt.jar, otherwise the installed and versioned artifact
        // is used: jrt-6-SNAPSHOT.jar.
        assertThat(bundleClassPath, anyOf(
                containsString("dependencies/jrt-6-SNAPSHOT.jar"),
                containsString("dependencies/jrt.jar")));
    }

    @Test
    public void require_that_component_jar_file_contains_compile_artifacts() {
        ZipEntry versionedEntry = jarFile.getEntry("dependencies/jrt-6-SNAPSHOT.jar");
        ZipEntry unversionedEntry = jarFile.getEntry("dependencies/jrt.jar");
        if (versionedEntry == null) {
            assertNotNull(unversionedEntry);
        } else {
            assertNull(unversionedEntry);
        }
    }


    @Test
    public void require_that_web_inf_url_is_propagated_to_the_manifest() {
        String webInfUrl = mainAttributes.getValue("WebInfUrl");
        assertThat(webInfUrl, containsString("/WEB-INF/web.xml"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void bundle_class_path_mappings_are_generated() throws URISyntaxException, IOException {
        URL mappingsUrl = getClass().getResource("/" + com.yahoo.osgi.maven.ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME);
        assertNotNull(
                "Could not find " + ProjectBundleClassPaths.CLASSPATH_MAPPINGS_FILENAME + " in the test output directory",
                mappingsUrl);

        ProjectBundleClassPaths bundleClassPaths = ProjectBundleClassPaths.load(Paths.get(mappingsUrl.toURI()));

        assertThat(bundleClassPaths.mainBundle.bundleSymbolicName, is("bundle-plugin-test"));

        Collection<String> mainBundleClassPaths = bundleClassPaths.mainBundle.classPathElements;

        assertThat(mainBundleClassPaths,
                hasItems(
                        endsWith("target/classes"),
                        anyOf(
                                allOf(containsString("jrt"), containsString(".jar"), containsString("m2/repository")),
                                containsString("jrt/target/jrt.jar"))));
    }
}
