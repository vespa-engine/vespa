// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import com.google.inject.Inject;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.Rule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

/**
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
    public Optional<RawFlag> fetch(FlagId flagId, FetchVector vector) {
        return getResolver(flagId).resolve(vector);
    }

    private FlagData getResolver(FlagId flagId) {
        Optional<String> v2String = getString(flagId, ".2");
        if (v2String.isPresent()) {
            return FlagData.deserialize(v2String.get());
        }

        Optional<String> v1String = getString(flagId, "");
        if (v1String.isPresent()) {
            // version 1: File contains value as a JSON
            // version 2: File contains FileResolver as a JSON (which may contain many values, one for each rule)
            // version 1 files should probably be discontinued
            Rule rule = new Rule(Optional.of(JsonNodeRawFlag.fromJson(v1String.get())), Collections.emptyList());
            return new FlagData(flagId, new FetchVector(), Collections.singletonList(rule));
        }

        // Will eventually resolve to empty RawFlag
        return new FlagData(flagId);
    }

    private Optional<String> getString(FlagId id, String suffix) {
        return getBytes(id, suffix).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private Optional<byte[]> getBytes(FlagId id, String suffix) {
        try {
            return Optional.of(Files.readAllBytes(getPath(id, suffix)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path getPath(FlagId id, String suffix) {
        return flagsDirectory.resolve(id.toString() + suffix);
    }

    @Override
    public String toString() {
        return "FileFlagSource{" + flagsDirectory + '}';
    }
}
