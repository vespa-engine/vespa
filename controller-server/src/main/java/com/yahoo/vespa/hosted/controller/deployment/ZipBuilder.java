// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility class to build zipped content by adding already zipped byte content or
 * adding new unzipped entries.
 *
 * @author freva
 */
public class ZipBuilder implements AutoCloseable {

    private final ByteArrayOutputStream byteArrayOutputStream;
    private final ZipOutputStream zipOutputStream;

    public ZipBuilder(int initialSize) {
        byteArrayOutputStream = new ByteArrayOutputStream(initialSize);
        zipOutputStream = new ZipOutputStream(byteArrayOutputStream);
    }

    public void add(byte[] zippedContent) {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zippedContent))) {
            for (ZipEntry entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                zin.transferTo(zipOutputStream);
                zipOutputStream.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to add zipped content", e);
        }
    }

    public void add(String entryName, byte[] content) {
        try {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content);
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to add entry " + entryName, e);
        }
    }

    /** @return zipped byte array */
    public byte[] toByteArray() {
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public void close() {
        try {
            zipOutputStream.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close zip output stream", e);
        }
    }
}
