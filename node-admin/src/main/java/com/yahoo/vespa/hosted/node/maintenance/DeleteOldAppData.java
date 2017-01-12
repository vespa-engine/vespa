// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author freva
 */

public class DeleteOldAppData {
    private static final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(DeleteOldAppData.class);

    /**
     * (Recursively) deletes files if they match all the criteria, also deletes empty directories.
     *
     * @param basePath      Base path from where to start the search
     * @param maxAgeSeconds Delete files older (last modified date) than maxAgeSeconds
     * @param fileNameRegex Delete files where filename matches fileNameRegex
     * @param recursive     Delete files in sub-directories (with the same criteria)
     */
    public static void deleteFiles(String basePath, long maxAgeSeconds, String fileNameRegex, boolean recursive) {
        Pattern fileNamePattern = fileNameRegex != null ? Pattern.compile(fileNameRegex) : null;
        File[] filesInDeleteDirectory = getContentsOfDirectory(basePath);

        for (File file : filesInDeleteDirectory) {
            if (file.isDirectory()) {
                if (recursive) {
                    deleteFiles(file.getAbsolutePath(), maxAgeSeconds, fileNameRegex, true);
                    if (file.list().length == 0 && !file.delete()) {
                        logger.warning("Could not delete directory: " + file.getAbsolutePath());
                    }
                }
            } else if (isPatternMatchingFilename(fileNamePattern, file) &&
                    isTimeSinceLastModifiedMoreThan(file, Duration.ofSeconds(maxAgeSeconds))) {
                if (!file.delete()) {
                    logger.warning("Could not delete file: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Deletes all files in target directory except the n most recent (by modified date)
     *
     * @param basePath          Base path to delete from
     * @param nMostRecentToKeep Number of most recent files to keep
     */
    public static void deleteFilesExceptNMostRecent(String basePath, int nMostRecentToKeep) {
        File[] deleteDirContents = getContentsOfDirectory(basePath);

        if (nMostRecentToKeep < 1) {
            throw new IllegalArgumentException("Number of files to keep must be a positive number");
        }

        List<File> filesInDeleteDir = Arrays.stream(deleteDirContents).filter(File::isFile).collect(Collectors.toList());
        if (filesInDeleteDir.size() <= nMostRecentToKeep) return;

        Collections.sort(filesInDeleteDir, (f1, f2) -> Long.signum(f1.lastModified() - f2.lastModified()));

        for (int i = nMostRecentToKeep; i < filesInDeleteDir.size(); i++) {
            if (!filesInDeleteDir.get(i).delete()) {
                logger.warning("Could not delete file: " + filesInDeleteDir.get(i).getAbsolutePath());
            }
        }
    }

    public static void deleteFilesLargerThan(File baseDirectory, long sizeInBytes) {
        File[] filesInBaseDirectory = getContentsOfDirectory(baseDirectory.getAbsolutePath());

        for (File file : filesInBaseDirectory) {
            if (file.isDirectory()) {
                deleteFilesLargerThan(file, sizeInBytes);
            } else {
                if (file.length() > sizeInBytes && !file.delete()) {
                    logger.warning("Could not delete file: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Deletes directories and their contents if they match all the criteria
     *
     * @param basePath      Base path to delete the directories from
     * @param maxAgeSeconds Delete directories older (last modified date) than maxAgeSeconds
     * @param dirNameRegex  Delete directories where directory name matches dirNameRegex
     */
    public static void deleteDirectories(String basePath, long maxAgeSeconds, String dirNameRegex) {
        Pattern dirNamePattern = dirNameRegex != null ? Pattern.compile(dirNameRegex) : null;
        File[] filesInDeleteDirectory = getContentsOfDirectory(basePath);

        for (File file : filesInDeleteDirectory) {
            if (file.isDirectory() &&
                    isPatternMatchingFilename(dirNamePattern, file) &&
                    isTimeSinceLastModifiedMoreThan(getMostRecentlyModifiedFileIn(file), Duration.ofSeconds(maxAgeSeconds))) {
                deleteFiles(file.getPath(), 0, null, true);
                if (file.list().length == 0 && !file.delete()) {
                    logger.warning("Could not delete directory: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Similar to rm -rf file:
     *   - It's not an error if file doesn't exist
     *   - If file is a directory, it and all content is removed
     *   - For symlinks: Only the symlink is removed, not what the symlink points to
     */
    public static void recursiveDelete(File file) throws IOException {
        if (file.isDirectory()) {
            for (File childFile : file.listFiles()) {
                recursiveDelete(childFile);
            }
        }

        Files.deleteIfExists(file.toPath());
    }

    static File[] getContentsOfDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        File[] directoryContents = directory.listFiles();

        return directoryContents == null ? new File[0] : directoryContents;
    }

    private static File getMostRecentlyModifiedFileIn(File baseFile) {
        File mostRecent = baseFile;
        File[] filesInDirectory = getContentsOfDirectory(baseFile.getAbsolutePath());

        for (File file : filesInDirectory) {
            if (file.isDirectory()) {
                file = getMostRecentlyModifiedFileIn(file);
            }

            if (file.lastModified() > mostRecent.lastModified()) {
                mostRecent = file;
            }
        }
        return mostRecent;
    }

    private static boolean isTimeSinceLastModifiedMoreThan(File file, Duration duration) {
        return System.currentTimeMillis() - file.lastModified() > duration.toMillis();
    }

    private static boolean isPatternMatchingFilename(Pattern pattern, File file) {
        return pattern == null || pattern.matcher(file.getName()).find();
    }
}
