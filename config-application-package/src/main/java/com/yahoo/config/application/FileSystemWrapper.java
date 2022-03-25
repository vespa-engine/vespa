// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.yolean.function.ThrowingFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Wraps a real or virtual file system — essentially a mapping from paths to bytes.
 *
 * @author jonmv
 */
public class FileSystemWrapper {

    final Path root;
    final Predicate<Path> existence;
    final ThrowingFunction<Path, byte[], IOException> reader;

    private FileSystemWrapper(Path root, Predicate<Path> existence, ThrowingFunction<Path, byte[], IOException> reader) {
        this.root = root;
        this.existence = existence;
        this.reader = reader;
    }

    public static FileSystemWrapper ofFiles(Path root, Predicate<Path> existence, ThrowingFunction<Path, byte[], IOException> reader) {
        return new FileSystemWrapper(root, existence, reader);
    }

    public static FileSystemWrapper getDefault(Path root) {
        return ofFiles(root, Files::exists, Files::readAllBytes);
    }

    public FileWrapper wrap(Path path) {
        return new FileWrapper(path);
    }


    public class FileWrapper {
        private final Path path;
        private FileWrapper(Path path) {
            Path relative = root.relativize(path).normalize();
            if (relative.isAbsolute() || relative.startsWith(".."))
                throw new IllegalArgumentException(path + " is not a descendant of " + root);

            this.path = path;
        }

        public Path path() { return path; }
        public boolean exists() { return existence.test(path); }
        public byte[] content() throws IOException { return reader.apply(path); }
        public Optional<FileWrapper> parent() { return Optional.ofNullable(path.getParent()).map(path -> wrap(path)); }
        public FileWrapper child(String name) { return wrap(path.resolve(name)); }
    }

}
