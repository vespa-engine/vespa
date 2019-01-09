// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.file;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Java API for a local file-based flag repository.
 *
 * @author hakonhall
 */
public class FlagDirectory {
    private static final Logger logger = Logger.getLogger(FlagDirectory.class.getName());

    private final Path flagDirectory;

    public FlagDirectory() {
        this(FileSystems.getDefault());
    }

    FlagDirectory(FileSystem fileSystem) {
        this(fileSystem.getPath(Defaults.getDefaults().vespaHome() + "/var/vespa/flags"));
    }

    public FlagDirectory(Path flagDirectory) {
        this.flagDirectory = flagDirectory;
    }

    public Path getPath() {
        return flagDirectory;
    }

    public Map<FlagId, FlagData> read() {
        return getAllRegularFilesStream()
                .map(path -> {
                    FlagId flagId = new FlagId(getFilenameOf(path));
                    Optional<FlagData> flagData = readFlagData(flagId);
                    if (!flagData.isPresent()) return null;
                    if (!Objects.equals(flagData.get().id(), flagId)) {
                        logger.log(LogLevel.WARNING, "Flag file " + path + " contains conflicting id " +
                                flagData.get().id() + ", ignoring flag");
                        return null;
                    }
                    return flagData.get();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(FlagData::id, Function.identity()));
    }

    public Optional<FlagData> readFlagData(FlagId flagId) {
        return readUtf8File(getPathFor(flagId)).map(FlagData::deserialize);
    }

    /**
     * Modify the flag directory as necessary, such that a later {@link #read()} will return {@code flagData}.
     *
     * @return true if any modifications were done.
     */
    public boolean sync(Map<FlagId, FlagData> flagData) {
        boolean modified = false;

        Set<Path> pathsToDelete = getAllRegularFilesStream().collect(Collectors.toCollection(HashSet::new));

        uncheck(() -> Files.createDirectories(flagDirectory));
        for (Map.Entry<FlagId, FlagData> entry : flagData.entrySet()) {
            FlagId flagId = entry.getKey();
            FlagData data = entry.getValue();
            Path path = getPathFor(flagId);

            pathsToDelete.remove(path);

            String serializedData = data.serializeToJson();
            Optional<String> fileContent = readUtf8File(path);
            if (fileContent.isPresent()) {
                if (!Objects.equals(fileContent.get(), serializedData)) {
                    logger.log(LogLevel.INFO, "Updating flag " + flagId + " from " + fileContent.get() +
                            " to " + serializedData);
                    writeUtf8File(path, serializedData);
                    modified = true;
                }
            } else {
                logger.log(LogLevel.INFO, "New flag " + flagId + ": " + serializedData);
                writeUtf8File(path, serializedData);
                modified = true;
            }
        }

        for (Path path : pathsToDelete) {
            logger.log(LogLevel.INFO, "Removing flag file " + path);
            uncheck(() -> Files.deleteIfExists(path));
            modified = true;
        }

        return modified;
    }

    private Stream<Path> getAllRegularFilesStream() {
        try {
            return Files.list(flagDirectory).filter(Files::isRegularFile);
        } catch (NotDirectoryException | NoSuchFileException e) {
            return Stream.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String getFilenameOf(Path path) {
        return path.getName(path.getNameCount() - 1).toString();
    }

    private Path getPathFor(FlagId flagId) {
        return flagDirectory.resolve(flagId.toString());
    }

    private Optional<String> readUtf8File(Path path) {
        try {
            return Optional.of(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeUtf8File(Path path, String content) {
        uncheck(() -> Files.write(path, content.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return "FlagDirectory{" + flagDirectory + '}';
    }
}
