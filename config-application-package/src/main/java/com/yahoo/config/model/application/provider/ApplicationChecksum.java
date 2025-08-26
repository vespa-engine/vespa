// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.HexDump;
import com.yahoo.text.Utf8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * A md5 checksum of an application
 */
class ApplicationChecksum {

    private final String checksum;

    ApplicationChecksum(File appDir) {
        checksum = computeChecksum(appDir);
    }

    public String asString() { return checksum; }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof ApplicationChecksum other)) return false;
        return other.checksum.equals(this.checksum);
    }

    @Override
    public int hashCode() { return checksum.hashCode(); }

    @Override
    public String toString() { return "Application checksum: " + checksum; }

    private static String computeChecksum(File appDir) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            for (File file : appDir.listFiles((dir, name) -> !name.equals(ApplicationPackage.EXT_DIR) && !name.startsWith("."))) {
                addPathToDigest(file, "", md5, true, false);
            }
            return toLowerCase(HexDump.toHexString(md5.digest()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Adds the given path to the digest, or does nothing if path is neither file nor dir
     *
     * @param path path to add to message digest
     * @param suffix only files with this suffix are considered
     * @param digest the {link @MessageDigest} to add the file paths to
     * @param recursive whether to recursively find children in the paths
     * @param fullPathNames whether to include the full paths in checksum or only the names
     * @throws java.io.IOException if adding path to digest fails when reading files from path
     */
    private static void addPathToDigest(File path, String suffix, MessageDigest digest, boolean recursive, boolean fullPathNames) throws IOException {
        if (!path.exists()) return;
        if (fullPathNames) {
            digest.update(path.getPath().getBytes(Utf8.getCharset()));
        } else {
            digest.update(path.getName().getBytes(Utf8.getCharset()));
        }
        if (path.isFile()) {
            FileInputStream is = new FileInputStream(path);
            addToDigest(is, digest);
            is.close();
        } else if (path.isDirectory()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (File elem : files) {
                    if ((elem.isDirectory() && recursive) || elem.getName().endsWith(suffix)) {
                        addPathToDigest(elem, suffix, digest, recursive, fullPathNames);
                    }
                }
            }
        }
    }

    private static void addToDigest(InputStream is, MessageDigest digest) throws IOException {
        if (is == null) return;
        byte[] buffer = new byte[65536];
        int i;
        do {
            i = is.read(buffer);
            if (i > 0) {
                digest.update(buffer, 0, i);
            }
        } while(i != -1);
    }

}
