// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author freva
 */
public class FileHelper {
    private static final Logger logger = Logger.getLogger(FileHelper.class.getSimpleName());

    /**
     * (Recursively) deletes files if they match all the criteria, also deletes empty directories.
     *
     * @param basePath      Base path from where to start the search
     * @param maxAge        Delete files older (last modified date) than maxAge
     * @param fileNameRegex Delete files where filename matches fileNameRegex
     * @param recursive     Delete files in sub-directories (with the same criteria)
     */
    public static void deleteFiles(Path basePath, Duration maxAge, Optional<String> fileNameRegex, boolean recursive) throws IOException {
        Pattern fileNamePattern = fileNameRegex.map(Pattern::compile).orElse(null);

        for (Path path : listContentsOfDirectory(basePath)) {
            if (Files.isDirectory(path)) {
                if (recursive) {
                    deleteFiles(path, maxAge, fileNameRegex, true);
                    if (listContentsOfDirectory(path).isEmpty() && !Files.deleteIfExists(path)) {
                        logger.warning("Could not delete directory: " + path.toAbsolutePath());
                    }
                }
            } else if (isPatternMatchingFilename(fileNamePattern, path) &&
                    isTimeSinceLastModifiedMoreThan(path, maxAge)) {
                if (! Files.deleteIfExists(path)) {
                    logger.warning("Could not delete file: " + path.toAbsolutePath());
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
    static void deleteFilesExceptNMostRecent(Path basePath, int nMostRecentToKeep) throws IOException {
        if (nMostRecentToKeep < 1) {
            throw new IllegalArgumentException("Number of files to keep must be a positive number");
        }

        List<Path> pathsInDeleteDir = listContentsOfDirectory(basePath).stream()
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(FileHelper::getLastModifiedTime))
                .skip(nMostRecentToKeep)
                .collect(Collectors.toList());

        for (Path path : pathsInDeleteDir) {
            if (!Files.deleteIfExists(path)) {
                logger.warning("Could not delete file: " + path.toAbsolutePath());
            }
        }
    }

    static void deleteFilesLargerThan(Path basePath, long sizeInBytes) throws IOException {
        for (Path path : listContentsOfDirectory(basePath)) {
            if (Files.isDirectory(path)) {
                deleteFilesLargerThan(path, sizeInBytes);
            } else {
                if (Files.size(path) > sizeInBytes && !Files.deleteIfExists(path)) {
                    logger.warning("Could not delete file: " + path.toAbsolutePath());
                }
            }
        }
    }

    /**
     * Deletes directories and their contents if they match all the criteria
     *
     * @param basePath      Base path to delete the directories from
     * @param maxAge        Delete directories older (last modified date) than maxAge
     * @param dirNameRegex  Delete directories where directory name matches dirNameRegex
     */
    public static void deleteDirectories(Path basePath, Duration maxAge, Optional<String> dirNameRegex) throws IOException {
        Pattern dirNamePattern = dirNameRegex.map(Pattern::compile).orElse(null);

        for (Path path : listContentsOfDirectory(basePath)) {
            if (Files.isDirectory(path) && isPatternMatchingFilename(dirNamePattern, path)) {
                boolean mostRecentFileModifiedBeforeMaxAge = getMostRecentlyModifiedFileIn(path)
                        .map(mostRecentlyModified -> isTimeSinceLastModifiedMoreThan(mostRecentlyModified, maxAge))
                        .orElse(true);

                if (mostRecentFileModifiedBeforeMaxAge) {
                    deleteFiles(path, Duration.ZERO, Optional.empty(), true);
                    if (listContentsOfDirectory(path).isEmpty() && !Files.deleteIfExists(path)) {
                        logger.warning("Could not delete directory: " + path.toAbsolutePath());
                    }
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
    public static void recursiveDelete(Path basePath) throws IOException {
        if (Files.isDirectory(basePath)) {
            for (Path path : listContentsOfDirectory(basePath)) {
                recursiveDelete(path);
            }
        }

        Files.deleteIfExists(basePath);
    }

    public static void moveIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.move(from, to);
        }
    }

    private static Optional<Path> getMostRecentlyModifiedFileIn(Path basePath) throws IOException {
        return Files.walk(basePath).max(Comparator.comparing(FileHelper::getLastModifiedTime));
    }

    private static boolean isTimeSinceLastModifiedMoreThan(Path path, Duration duration) {
        Instant nowMinusDuration = Instant.now().minus(duration);
        Instant lastModified = getLastModifiedTime(path).toInstant();

        // Return true also if they are equal for test stability
        // (lastModified <= nowMinusDuration) is the same as !(lastModified > nowMinusDuration)
        return !lastModified.isAfter(nowMinusDuration);
    }

    private static boolean isPatternMatchingFilename(Pattern pattern, Path path) {
        return pattern == null || pattern.matcher(path.getFileName().toString()).find();
    }

    /**
     * @return list all files in a directory, returns empty list if directory does not exist
     */
    public static List<Path> listContentsOfDirectory(Path basePath) {
        try (Stream<Path> directoryStream = Files.list(basePath)) {
            return directoryStream.collect(Collectors.toList());
        } catch (NoSuchFileException ignored) {
            return Collections.emptyList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list contents of directory " + basePath.toAbsolutePath(), e);
        }
    }

    static FileTime getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to get last modified time of " + path.toAbsolutePath(), e);
        }
    }
}
