// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.mojo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Tony Vaagenes
 */
public class Compression {
    static public void zipDirectory(File dir) throws Exception {
        FileOutputStream zipFile = new FileOutputStream(new File(dir.getParent(), dir.getName() + ".zip"));
        ZipOutputStream zipOutputStream = new ZipOutputStream(zipFile);
        try {
            addDirectory(zipOutputStream, dir.getName(), dir, "");
        } finally {
            zipOutputStream.close();
        }
    }

    private static void addDirectory(ZipOutputStream zipOutputStream, String zipTopLevelDir, File baseDir, String relativePath) throws IOException {
        File currentDir = new File(baseDir, relativePath);

        for (File child : currentDir.listFiles()) {
            if (child.isDirectory()) {
                addDirectory(zipOutputStream, zipTopLevelDir, baseDir, composePath(relativePath, child.getName()));
            } else {
                addFile(zipOutputStream, zipTopLevelDir, relativePath, child);
            }
        }
    }

    private static void addFile(ZipOutputStream zipOutputStream, String zipTopLevelDir, String relativePath, File child) throws IOException {
        ZipEntry entry = new ZipEntry(composePath(zipTopLevelDir, composePath(relativePath, child.getName())));
        zipOutputStream.putNextEntry(entry);
        try {
            FileInputStream fileInput = new FileInputStream(child);
            try {
                copyBytes(fileInput, zipOutputStream);
            } finally {
                fileInput.close();
            }
        } finally {
            zipOutputStream.closeEntry();
        }
    }

    public static void copyBytes(InputStream input, OutputStream output) throws IOException {
        byte[] b = new byte[1024];
        int numRead = 0;

        while((numRead = input.read(b)) != -1) {
            output.write(b, 0, numRead);
        }
    }

    private static String composePath(String relativePath, String subDir) {
        return  relativePath.isEmpty() ?
                subDir :
                relativePath + File.separator + subDir;
    }
}
