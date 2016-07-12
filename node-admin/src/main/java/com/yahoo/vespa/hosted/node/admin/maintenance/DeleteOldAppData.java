package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.io.File;
import java.time.Duration;

/**
 * @author valerijf
 */

public class DeleteOldAppData {
    public static final long DEFAULT_MAX_AGE_IN_SECONDS = Duration.ofDays(7).getSeconds();

    public static void deleteOldAppData(String path, long maxAgeSeconds, String prefix, String suffix, boolean recursive) {
        File deleteFolder = new File(path);
        File[] filesInDeleteFolder = deleteFolder.listFiles();

        if (filesInDeleteFolder == null) {
            throw new IllegalArgumentException("The specified path is not a folder");
        }

        for (File file : filesInDeleteFolder) {
            if (file.isDirectory()) {
                if (recursive) {
                    deleteOldAppData(file.getAbsolutePath(), maxAgeSeconds, prefix, suffix, true);
                    if (file.list().length == 0 && !file.delete()) {
                        System.err.println("Could not delete folder: " + file.getAbsolutePath());
                    }
                }
            } else if ((prefix == null || file.getName().startsWith(prefix)) && (suffix == null || file.getName().endsWith(suffix))) {
                if (file.lastModified() + maxAgeSeconds*1000 < System.currentTimeMillis()) {
                    if (!file.delete()) {
                        System.err.println("Could not delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }
}
