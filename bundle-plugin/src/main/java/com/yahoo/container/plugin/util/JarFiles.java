// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.io.File;
import java.io.InputStream;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JarFiles {
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

    public static Optional<Manifest> getManifest(File jarFile) {
        return withJarFile(jarFile, jar -> Optional.ofNullable(jar.getManifest()));
    }
}
