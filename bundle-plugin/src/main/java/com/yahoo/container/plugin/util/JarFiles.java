// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.yahoo.container.plugin.mojo.GenerateProvidedArtifactManifestMojo.PROVIDED_ARTIFACTS_MANIFEST_ENTRY;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JarFiles {

    public static List<ArtifactId> providedArtifactsFromManifest(File jarFile) {
        return getManifest(jarFile).map(mf -> getMainAttributeValue(mf, PROVIDED_ARTIFACTS_MANIFEST_ENTRY)
                        .map(s -> Arrays.stream(s.split(","))
                                .map(ArtifactId::fromStringValue)
                                .toList())
                        .orElse(Collections.emptyList()))
                .orElse(Collections.emptyList());

    }

    public static <T> T withJarFile(File file, ThrowingFunction<JarFile, T> action) {
        try (JarFile jar = new JarFile(file)) {
            return action.apply(jar);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T withInputStream(ZipFile zipFile, ZipEntry zipEntry, ThrowingFunction<InputStream, T> action) {
        try (InputStream is = zipFile.getInputStream(zipEntry)) {
            return action.apply(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Optional<String> getMainAttributeValue(Manifest manifest, String attributeName) {
        return Optional.ofNullable(manifest.getMainAttributes().getValue(attributeName));
    }

    public static Optional<Manifest> getManifest(File jarFile) {
        return withJarFile(jarFile, jar -> Optional.ofNullable(jar.getManifest()));
    }
}
