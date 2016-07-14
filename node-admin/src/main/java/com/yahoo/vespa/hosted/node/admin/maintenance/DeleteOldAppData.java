package com.yahoo.vespa.hosted.node.admin.maintenance;

import java.io.File;
import java.time.Duration;
import java.util.regex.Pattern;

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
     * @param fileName       Delete files where regex matches against filename
     * @param recursive      Delete files in sub-directories (with the same criteria)
     */
    public static void deleteFiles(String basePath, long maxAgeSeconds, String fileName, boolean recursive) {
        File deleteDirectory = new File(basePath);
        File[] filesInDeleteDirectory = deleteDirectory.listFiles();
        Pattern fileNamePattern = fileName != null ? Pattern.compile(fileName) : null;

        if (filesInDeleteDirectory == null) {
            throw new IllegalArgumentException("The specified path is not a directory");
        }

        for (File file : filesInDeleteDirectory) {
            if (file.isDirectory() && recursive) {
                deleteFiles(file.getAbsolutePath(), maxAgeSeconds, fileName, true);
                if (file.list().length == 0 && !file.delete()) {
                    System.err.println("Could not delete directory: " + file.getAbsolutePath());
                }
            } else if (fileNamePattern == null || fileNamePattern.matcher(file.getName()).find()) {
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
                deleteFiles(file.getPath(), 0, null, true);
                if (file.list().length == 0 && !file.delete()) {
                    System.err.println("Could not delete directory: " + file.getAbsolutePath());
                }
            }
        }
    }
}
