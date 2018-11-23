// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.google.common.util.concurrent.UncheckedTimeoutException;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A {@link FlagSource} backed by local files.
 *
 * @author hakonhall
 */
public class FileFlagSource implements FlagSource {
    static final String FLAGS_DIRECTORY = "/etc/vespa/flags";

    private final Path flagsDirectory;

    @Inject
    public FileFlagSource() {
        this(FileSystems.getDefault());
    }

    public FileFlagSource(FileSystem fileSystem) {
        this(fileSystem.getPath(FLAGS_DIRECTORY));
    }

    public FileFlagSource(Path flagsDirectory) {
        this.flagsDirectory = flagsDirectory;
    }

    @Override
    public boolean hasFeature(FlagId id) {
        return Files.exists(getPath(id));
    }

    @Override
    public Optional<String> getString(FlagId id) {
        return getBytes(id).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    public Optional<byte[]> getBytes(FlagId id) {
        try {
            return Optional.of(Files.readAllBytes(getPath(id)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedTimeoutException(e);
        }
    }

    private Path getPath(FlagId id) {
        return flagsDirectory.resolve(id.toString());
    }

    @Override
    public String toString() {
        return "FileFlagSource{" + flagsDirectory + '}';
    }
}
