package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.io.File;
import java.time.Duration;

/**
 * @author valerijf
 */

public class DeleteOldAppData {
    public static final long DEFAULT_MAX_AGE_IN_SECONDS = Duration.ofDays(7).getSeconds();

    /**
     * (Recursively) deletes files if they match all the criteria, also deletes empty directories.
     *
     * @param basePath       Base path from where to start the search
     * @param maxAgeSeconds  Delete files older (last modified date) than maxAgeSeconds
     * @param fileNamePrefix Delete files with filename starting with fileNamePrefix
     * @param fileNameSuffix Delete files with filename (including extension) ending with fileNameSuffix
     * @param recursive      Delete files in sub-directories (with the same criteria)
     */
    public static void deleteFiles(String basePath, long maxAgeSeconds, String fileNamePrefix, String fileNameSuffix, boolean recursive) {
        File deleteDirectory = new File(basePath);
        File[] filesInDeleteDirectory = deleteDirectory.listFiles();

        if (filesInDeleteDirectory == null) {
            throw new IllegalArgumentException("The specified path is not a directory");
        }

        for (File file : filesInDeleteDirectory) {
            if (file.isDirectory() && recursive) {
                deleteFiles(file.getAbsolutePath(), maxAgeSeconds, fileNamePrefix, fileNameSuffix, true);
                if (file.list().length == 0 && !file.delete()) {
                    System.err.println("Could not delete directory: " + file.getAbsolutePath());
                }
            } else if ((fileNamePrefix == null || file.getName().startsWith(fileNamePrefix))
                    && (fileNameSuffix == null || file.getName().endsWith(fileNameSuffix))) {
                if (file.lastModified() + maxAgeSeconds * 1000 < System.currentTimeMillis()) {
                    if (!file.delete()) {
                        System.err.println("Could not delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * Deletes directories and their contents if they match all the criteria
     *
     * @param basePath      Base path to delete the directories from
     * @param maxAgeSeconds Delete directories older (last modified date) than maxAgeSeconds
     * @param dirNamePrefix Delete directories with name starting with dirNamePrefix
     * @param dirNameSuffix Delete directories with name ending with dirNameSuffix
     */
    public static void deleteDirectories(String basePath, long maxAgeSeconds, String dirNamePrefix, String dirNameSuffix) {
        File deleteDirectory = new File(basePath);
        File[] filesInDeleteDirectory = deleteDirectory.listFiles();

        if (filesInDeleteDirectory == null) {
            throw new IllegalArgumentException("The specified path is not a directory");
        }

        for (File file : filesInDeleteDirectory) {
            if (file.isDirectory() &&
                    (dirNamePrefix == null || file.getName().startsWith(dirNamePrefix)) &&
                    (dirNameSuffix == null || file.getName().endsWith(dirNameSuffix)) &&
                    file.lastModified() + maxAgeSeconds * 1000 < System.currentTimeMillis()) {
                deleteFiles(file.getPath(), 0, null, null, true);
                if (file.list().length == 0 && !file.delete()) {
                    System.err.println("Could not delete directory: " + file.getAbsolutePath());
                }
            }
        }
    }
}
