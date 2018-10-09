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
    private Predicate<FileAttributes> fileFilter = attr -> false;
    private Predicate<FileAttributes> directoryFilter = attr -> false;
    private boolean includeBase = false;
    private int maxDepth = 1;

    public FileHelper(Path basePath) {
        this.basePath = basePath;
    }

    /** Creates a FileHelper at the given basePath  */
    public static FileHelper from(Path basePath) {
        return new FileHelper(basePath);
    }

    /** Creates a FileHelper at give basePath that will match all files */
    public static FileHelper streamFiles(Path basePath) {
        return from(basePath).filterFile(attr -> true);
    }


    /** Creates a FileHelper at give basePath that will match all directories */
    public static FileHelper streamDirectories(Path basePath) {
        return from(basePath).filterDirectory(attr -> true);
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
     * Maximum depth (relative to basePath) where contents should be matched with the given filters.
     *
     * Note: When using {@link #delete()}, elements beyond this depth will be deleted if they are inside
     * a directory that matched before max depth. This behaves similarly to
     * `find basePath -maxdepth maxDepth -exec rm -r "{}" \;`
     */
    public FileHelper maxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    /** Whether the base path should also be considered (i.e. checked against the corresponding filter) */
    public FileHelper includeBase(boolean includeBase) {
        this.includeBase = includeBase;
        return this;
    }

    /**
     * Deletes all matching elements, ignores basePath
     *
     * @see #delete(boolean)
     */
    public int delete() {
        return delete(false);
    }

    /**
     * Deletes all matching elements
     *
     * @param deleteBase if true, will delete basePath aswell
     * @return Number of deleted items
     */
    public int delete(boolean deleteBase) {
        int[] numDeletions = { 0 }; // :(
        applyForEachToMatching(basePath,
                deleteBase ? all() : fileFilter,
                deleteBase ? all() : directoryFilter,
                deleteBase ? 0 : maxDepth,
                true,
                deleteBase || includeBase,
                attributes -> {
                    if (deleteIfExists(attributes.path()))
                        numDeletions[0]++;
                }
        );

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
        applyForEachToMatching(basePath, fileFilter, directoryFilter, maxDepth, false, includeBase, action);
    }
    

    /**
     * <p> This method walks a file tree rooted at a given starting file. The file tree traversal is
     * <em>depth-first</em>: The filter function is applied in pre-order (NLR), but the given
     * {@link Consumer} will be called in post-order (LRN).
     */
    private void applyForEachToMatching(Path basePath, Predicate<FileAttributes> fileFilter, Predicate<FileAttributes> directoryFilter,
                                        int maxMatchDepth, boolean matchAllInMatchingDirectories, boolean includeBase,
                                        Consumer<FileAttributes> action) {
        try {
            // Only need to traverse as deep as we want to match, unless we want to match everything in directories
            // already matched
            final int maxTraverseDepth = matchAllInMatchingDirectories ? Integer.MAX_VALUE : maxMatchDepth;
            Files.walkFileTree(basePath, Collections.emptySet(), maxTraverseDepth, new SimpleFileVisitor<Path>() {
                private final Stack<FileAttributes> matchingDirectoryStack = new Stack<>();
                private int currentLevel = -1;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    currentLevel++;

                    FileAttributes attributes = new FileAttributes(dir, attrs);
                    // If we are inside a directory that previously matched and we want to match anything
                    // inside matching directories, add it to the matching stack and continue
                    if (!matchingDirectoryStack.empty() && matchAllInMatchingDirectories) {
                        matchingDirectoryStack.push(attributes);
                        return FileVisitResult.CONTINUE;
                    }

                    boolean directoryMatches = directoryFilter.test(attributes);
                    // If the directory does not match the filter and we are or beyond max matching depth, we can
                    // skip the the entire subtree
                    if (!directoryMatches && currentLevel >= maxMatchDepth)
                        return FileVisitResult.SKIP_SUBTREE;

                    if (directoryMatches)
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
                    if ((!matchingDirectoryStack.empty() && matchAllInMatchingDirectories) || fileFilter.test(attributes))
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

    public static Predicate<FileAttributes> all() {
        return attrs -> true;
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
