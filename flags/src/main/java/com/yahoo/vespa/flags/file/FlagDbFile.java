// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.file;

import java.util.logging.Level;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Java API for a flag database stored in a single file
 *
 * @author hakonhall
 */
public class FlagDbFile {
    private static final Logger logger = Logger.getLogger(FlagDbFile.class.getName());

    private final Path path;

    public FlagDbFile() {
        this(FileSystems.getDefault());
    }

    public FlagDbFile(FileSystem fileSystem) {
        this(fileSystem.getPath(Defaults.getDefaults().underVespaHome("var/vespa/flag.db")));
    }

    public FlagDbFile(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }

    public Map<FlagId, FlagData> read() {
        Optional<byte[]> bytes = readFile();
        if (!bytes.isPresent()) return Collections.emptyMap();
        return FlagData.deserializeList(bytes.get()).stream().collect(Collectors.toMap(FlagData::id, Function.identity()));
    }

    public boolean sync(Map<FlagId, FlagData> flagData) {
        boolean modified = false;
        Map<FlagId, FlagData> currentFlagData = read();
        Set<FlagId> flagIdsToBeRemoved = new HashSet<>(currentFlagData.keySet());
        List<FlagData> flagDataList = new ArrayList<>(flagData.values());

        for (FlagData data : flagDataList) {
            flagIdsToBeRemoved.remove(data.id());

            FlagData existingFlagData = currentFlagData.get(data.id());
            if (existingFlagData == null) {
                logger.log(Level.INFO, "New flag " + data.id() + ": " + data.serializeToJson());
                modified = true;

                // Could also consider testing with FlagData::equals, but that would be too fragile?
            } else if (!Objects.equals(data.serializeToJson(), existingFlagData.serializeToJson())){
                logger.log(Level.INFO, "Updating flag " + data.id() + " from " +
                        existingFlagData.serializeToJson() + " to " + data.serializeToJson());
                modified = true;
            }
        }

        if (!flagIdsToBeRemoved.isEmpty()) {
            String flagIdsString = flagIdsToBeRemoved.stream().map(FlagId::toString).collect(Collectors.joining(", "));
            logger.log(Level.INFO, "Removing flags " + flagIdsString);
            modified = true;
        }

        if (!modified) return false;

        writeFile(FlagData.serializeListToUtf8Json(flagDataList));

        return modified;
    }

    private Optional<byte[]> readFile() {
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeFile(byte[] bytes) {
        uncheck(() -> Files.createDirectories(path.getParent()));
        uncheck(() -> Files.write(path, bytes));
    }
}
