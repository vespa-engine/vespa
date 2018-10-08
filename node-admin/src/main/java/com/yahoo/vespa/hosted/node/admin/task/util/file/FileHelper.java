// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.uncheck;

/**
 * @author freva
 */
public class FileHelper {

    private final Path basePath;
    private Predicate<FileAttributes> fileFilter;
    private Predicate<FileAttributes> directoryFilter;
    private boolean includeBase = false;
    private boolean recursive = false;

    public FileHelper(Path basePath, boolean includeFiles, boolean includeDirectories) {
        this.basePath = basePath;
        this.fileFilter = path -> includeFiles;
        this.directoryFilter = path -> includeDirectories;
    }

    /**
     * Creates a {@link FileHelper} that will by default match all files and all directories
     * under the given basePath.
     */
    public static FileHelper streamContents(Path basePath) {
        return new FileHelper(basePath, true, true);
    }

    /**
     * Creates a {@link FileHelper} that will by default match all files and no directories
     * under the given basePath.
     */
    public static FileHelper streamFiles(Path basePath) {
        return new FileHelper(basePath, true, false);
    }

    /**
     * Creates a {@link FileHelper} that will by default match all directories and no files
     * under the given basePath.
     */
    public static FileHelper streamDirectories(Path basePath) {
        return new FileHelper(basePath, false, true);
    }


    /**
     * Filter that will be used to match files under the base path. Files include everything that
     * is not a directory (such as symbolic links)
     */
    public FileHelper filterFile(Predicate<FileAttributes> fileFilter) {
        this.fileFilter = fileFilter;
        return this;
    }

    /**
     * Filter that will be used to match directories under the base path.
     *
     * NOTE: When a directory is matched, all of its sub-directories and files are also matched
     */
    public FileHelper filterDirectory(Predicate<FileAttributes> directoryFilter) {
        this.directoryFilter = directoryFilter;
        return this;
    }

    /**
     * Whether the search should be recursive.
     *
     * WARNING: When using {@link #delete()} and matching directories, make sure that the directories
     * either are already empty or that recursive is set
     */
    public FileHelper recursive(boolean recursive) {
        this.recursive = recursive;
        return this;
    }

    /**
     * Whether the base path should also be considered (i.e. checked against the correspoding filter).
     * When using {@link #delete()} with directories, this is the difference between
     * `rm -rf basePath` (true) and `rm -rf basePath/*` (false)
     */
    public FileHelper includeBase(boolean includeBase) {
        this.includeBase = includeBase;
        return this;
    }

    public int delete() {
        int[] numDeletions = { 0 }; // :(
        forEach(attributes -> {
            if (deleteIfExists(attributes.path()))
                numDeletions[0]++;
        });

        return numDeletions[0];
    }

    public List<FileAttributes> list() {
        LinkedList<FileAttributes> list = new LinkedList<>();
        forEach(list::add);
        return list;
    }

    public Stream<FileAttributes> stream() {
        return list().stream();
    }

    public void forEachPath(Consumer<Path> action) {
        forEach(attributes -> action.accept(attributes.path()));
    }

    /** Applies a given consumer to all the matching {@link FileHelper.FileAttributes} */
    public void forEach(Consumer<FileAttributes> action) {
        applyForEachToMatching(basePath, fileFilter, directoryFilter, recursive, includeBase, action);
    }
    

    /**
     * <p> This method walks a file tree rooted at a given starting file. The file tree traversal is
     * <em>depth-first</em>: The filter function is applied in pre-order (NLR), but the given
     * {@link Consumer} will be called in post-order (LRN).
     */
    private void applyForEachToMatching(Path basePath, Predicate<FileAttributes> fileFilter, Predicate<FileAttributes> directoryFilter,
                                        boolean recursive, boolean includeBase, Consumer<FileAttributes> action) {
        try {
            Files.walkFileTree(basePath, Collections.emptySet(), recursive ? Integer.MAX_VALUE : 1, new SimpleFileVisitor<Path>() {
                private Stack<FileAttributes> matchingDirectoryStack = new Stack<>();
                private int currentLevel = -1;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    currentLevel++;

                    FileAttributes attributes = new FileAttributes(dir, attrs);
                    if (!matchingDirectoryStack.empty() || directoryFilter.test(attributes))
                        matchingDirectoryStack.push(attributes);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // When we find a directory at the max depth given to Files.walkFileTree, the directory
                    // will be passed to visitFile() rather than (pre|post)VisitDirectory
                    if (attrs.isDirectory()) {
                        preVisitDirectory(file, attrs);
                        return postVisitDirectory(file, null);
                    }

                    FileAttributes attributes = new FileAttributes(file, attrs);
                    if (!matchingDirectoryStack.isEmpty() || fileFilter.test(attributes))
                        action.accept(attributes);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (!matchingDirectoryStack.isEmpty()) {
                        FileAttributes attributes = matchingDirectoryStack.pop();
                        if (currentLevel != 0 || includeBase)
                            action.accept(attributes);
                    }

                    currentLevel--;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (NoSuchFileException ignored) {

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    // Ideally, we would reuse the FileAttributes in this package, but unfortunately we only get
    // BasicFileAttributes and not PosixFileAttributes from FileVisitor
    public static class FileAttributes {
        private final Path path;
        private final BasicFileAttributes attributes;

        FileAttributes(Path path, BasicFileAttributes attributes) {
            this.path = path;
            this.attributes = attributes;
        }

        public Path path() { return path; }
        public String filename() { return path.getFileName().toString(); }
        public Instant lastModifiedTime() { return attributes.lastModifiedTime().toInstant(); }
        public boolean isRegularFile() { return attributes.isRegularFile(); }
        public boolean isDirectory() { return attributes.isDirectory(); }
        public long size() { return attributes.size(); }
    }


    // Filters
    public static Predicate<FileAttributes> olderThan(Duration duration) {
        return attrs -> Duration.between(attrs.lastModifiedTime(), Instant.now()).compareTo(duration) > 0;
    }

    public static Predicate<FileAttributes> youngerThan(Duration duration) {
        return olderThan(duration).negate();
    }

    public static Predicate<FileAttributes> largerThan(long sizeInBytes) {
        return attrs -> attrs.size() > sizeInBytes;
    }

    public static Predicate<FileAttributes> smallerThan(long sizeInBytes) {
        return largerThan(sizeInBytes).negate();
    }

    public static Predicate<FileAttributes> nameMatches(Pattern pattern) {
        return attrs -> pattern.matcher(attrs.filename()).matches();
    }

    public static Predicate<FileAttributes> nameStartsWith(String string) {
        return attrs -> attrs.filename().startsWith(string);
    }

    public static Predicate<FileAttributes> nameEndsWith(String string) {
        return attrs -> attrs.filename().endsWith(string);
    }


    // Other helpful methods that no not throw checked exceptions
    public static boolean moveIfExists(Path from, Path to) {
        try {
            Files.move(from, to);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean deleteIfExists(Path path) {
        return uncheck(() -> Files.deleteIfExists(path));
    }

    public static Path createDirectories(Path path) {
        return uncheck(() -> Files.createDirectories(path));
    }
}
