// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.text.JSON;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.DimensionHelper;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Represents a hierarchy of flag data files. See {@link FlagsTarget} for file naming convention.
 *
 * The flag files must reside in a 'flags/' root directory containing a directory for each flag name:
 * {@code ./flags/<flag-id>/*.json}
 *
 * Optionally, there can be an arbitrary number of directories "between" 'flags/' root directory and
 * the flag name directory:
 * {@code ./flags/onelevel/<flag-id>/*.json}
 * {@code ./flags/onelevel/anotherlevel/<flag-id>/*.json}
 *
 * @author bjorncs
 */
public class SystemFlagsDataArchive {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<FlagId, Map<String, FlagData>> files;

    private SystemFlagsDataArchive(Map<FlagId, Map<String, FlagData>> files) {
        this.files = files;
    }

    public static SystemFlagsDataArchive fromZip(InputStream rawIn) {
        Builder builder = new Builder();
        try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(rawIn))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("flags/")) {
                    Path filePath = Paths.get(name);
                    String rawData = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    addFile(builder, rawData, filePath, Set.of(), null);
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SystemFlagsDataArchive fromDirectoryAndSystem(Path directory, ZoneRegistry systemDefinition) {
        return fromDirectory(directory, systemDefinition);
    }

    public static SystemFlagsDataArchive fromDirectory(Path directory) { return fromDirectory(directory, null); }

    private static SystemFlagsDataArchive fromDirectory(Path directory, ZoneRegistry systemDefinition) {
        Set<String> filenamesForSystem = getFilenamesForSystem(systemDefinition);
        Path root = directory.toAbsolutePath();
        Path flagsDirectory = directory.resolve("flags");
        if (!Files.isDirectory(flagsDirectory)) {
            throw new IllegalArgumentException("Sub-directory 'flags' does not exist: " + flagsDirectory);
        }
        try (Stream<Path> directoryStream = Files.walk(root)) {
            Builder builder = new Builder();
            directoryStream.forEach(absolutePath -> {
                Path relativePath = root.relativize(absolutePath);
                if (!Files.isDirectory(absolutePath) &&
                        relativePath.startsWith("flags")) {
                    String rawData = uncheck(() -> Files.readString(absolutePath, StandardCharsets.UTF_8));
                    addFile(builder, rawData, relativePath, filenamesForSystem, systemDefinition);
                }
            });
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    public void toZip(OutputStream out) {
        ZipOutputStream zipOut = new ZipOutputStream(out);
        files.forEach((flagId, fileMap) -> {
            fileMap.forEach((filename, flagData) -> {
                uncheck(() -> {
                    zipOut.putNextEntry(new ZipEntry(toFilePath(flagId, filename)));
                    zipOut.write(flagData.serializeToUtf8Json());
                    zipOut.closeEntry();
                });
            });
        });
        uncheck(zipOut::flush);
    }

    public List<FlagData> flagData(FlagsTarget target) {
        List<String> filenames = target.flagDataFilesPrioritized();
        List<FlagData> targetData = new ArrayList<>();
        files.forEach((flagId, fileMap) -> {
            for (String filename : filenames) {
                FlagData data = fileMap.get(filename);
                if (data != null) {
                    if (!data.isEmpty()) {
                        targetData.add(data);
                    }
                    return;
                }
            }
        });
        return targetData;
    }

    public void validateAllFilesAreForTargets(SystemName currentSystem, Set<FlagsTarget> targets) throws IllegalArgumentException {
        Set<String> validFiles = targets.stream()
                .flatMap(target -> target.flagDataFilesPrioritized().stream())
                .collect(Collectors.toSet());
        Set<SystemName> otherSystems = Arrays.stream(SystemName.values())
                .filter(systemName -> systemName != currentSystem)
                .collect(Collectors.toSet());
        files.forEach((flagId, fileMap) -> {
            for (String filename : fileMap.keySet()) {
                boolean isFileForOtherSystem = otherSystems.stream()
                        .anyMatch(system -> filename.startsWith(system.value() + "."));
                boolean isFileForCurrentSystem = validFiles.contains(filename);
                if (!isFileForOtherSystem && !isFileForCurrentSystem) {
                    throw new IllegalArgumentException("Unknown flag file: " + toFilePath(flagId, filename));
                }
            }
        });
    }

    private static Set<String> getFilenamesForSystem(ZoneRegistry systemDefinition) {
        if (systemDefinition == null) return Set.of();
        return FlagsTarget.getAllTargetsInSystem(systemDefinition, false).stream()
                .flatMap(target -> target.flagDataFilesPrioritized().stream())
                .collect(Collectors.toSet());
    }

    private static void addFile(Builder builder, String rawData, Path filePath, Set<String> filenamesForSystem,
                                ZoneRegistry systemDefinition) {
        String filename = filePath.getFileName().toString();
        if (filename.startsWith(".")) {
            return; // Ignore files starting with '.'
        }
        if (!filenamesForSystem.isEmpty() && !filenamesForSystem.contains(filename)) {
            if (systemDefinition != null && filename.startsWith(systemDefinition.system().value() + '.')) {
                throw new IllegalArgumentException(String.format(
                        "Environment or zone in filename '%s' does not exist", filename));
            }
            return; // Ignore files irrelevant for system
        }
        if (!filename.endsWith(".json")) {
            throw new IllegalArgumentException(String.format("Only JSON files are allowed in 'flags/' directory (found '%s')", filePath.toString()));
        }
        FlagId directoryDeducedFlagId = new FlagId(filePath.getName(filePath.getNameCount()-2).toString());
        FlagData flagData;
        if (rawData.isBlank()) {
            flagData = new FlagData(directoryDeducedFlagId);
        } else {
            String normalizedRawData = normalizeJson(rawData);
            flagData = FlagData.deserialize(normalizedRawData);
            if (!directoryDeducedFlagId.equals(flagData.id())) {
                throw new IllegalArgumentException(
                        String.format("Flag data file with flag id '%s' in directory for '%s'",
                                flagData.id(), directoryDeducedFlagId.toString()));
            }

            String serializedData = flagData.serializeToJson();
            if (!JSON.equals(serializedData, normalizedRawData)) {
                throw new IllegalArgumentException(filePath + " contains unknown non-comment fields: " +
                        "after removing any comment fields the JSON is:\n  " +
                        normalizedRawData +
                        "\nbut deserializing this ended up with a JSON that are missing some of the fields:\n  " +
                        serializedData +
                        "\nSee https://git.ouroath.com/vespa/hosted-feature-flags for more info on the JSON syntax");
            }
        }

        if (builder.hasFile(filename, flagData)) {
            throw new IllegalArgumentException(
                String.format("Flag data file in '%s' contains redundant flag data for id '%s' already set in another directory!",
                              filePath, flagData.id()));
        }

        builder.addFile(filename, flagData);
    }

    static String normalizeJson(String json) {
        JsonNode root = uncheck(() -> mapper.readTree(json));
        removeCommentsRecursively(root);
        verifyValues(root);
        return root.toString();
    }

    private static void verifyValues(JsonNode root) {
        var cursor = new JsonAccessor(root);
        cursor.get("rules").forEachArrayElement(rule -> rule.get("conditions").forEachArrayElement(condition -> {
            var dimension = condition.get("dimension");
            if (dimension.isEqualTo(DimensionHelper.toWire(FetchVector.Dimension.APPLICATION_ID))) {
                condition.get("values").forEachArrayElement(conditionValue -> {
                    String applicationIdString = conditionValue.asString()
                            .orElseThrow(() -> new IllegalArgumentException("Non-string application ID: " + conditionValue));
                    // Throws exception if not recognized
                    ApplicationId.fromSerializedForm(applicationIdString);
                });
            } else if (dimension.isEqualTo(DimensionHelper.toWire(FetchVector.Dimension.NODE_TYPE))) {
                condition.get("values").forEachArrayElement(conditionValue -> {
                    String nodeTypeString = conditionValue.asString()
                            .orElseThrow(() -> new IllegalArgumentException("Non-string node type: " + conditionValue));
                    // Throws exception if not recognized
                    NodeType.valueOf(nodeTypeString);
                });
            } else if (dimension.isEqualTo(DimensionHelper.toWire(FetchVector.Dimension.CONSOLE_USER_EMAIL))) {
                condition.get("values").forEachArrayElement(conditionValue -> conditionValue.asString()
                        .orElseThrow(() -> new IllegalArgumentException("Non-string email address: " + conditionValue)));
            } else if (dimension.isEqualTo(DimensionHelper.toWire(FetchVector.Dimension.TENANT_ID))) {
                condition.get("values").forEachArrayElement(conditionValue -> conditionValue.asString()
                        .orElseThrow(() -> new IllegalArgumentException("Non-string tenant ID: " + conditionValue)));
            }
        }));
    }

    private static void removeCommentsRecursively(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.remove("comment");
        }

        node.forEach(SystemFlagsDataArchive::removeCommentsRecursively);
    }

    private static String toFilePath(FlagId flagId, String filename) {
        return "flags/" + flagId.toString() + "/" + filename;
    }

    public static class Builder {
        private final Map<FlagId, Map<String, FlagData>> files = new TreeMap<>();

        public Builder() {}

        public Builder addFile(String filename, FlagData data) {
            files.computeIfAbsent(data.id(), k -> new TreeMap<>()).put(filename, data);
            return this;
        }

        public boolean hasFile(String filename, FlagData data) {
            return files.containsKey(data.id()) && files.get(data.id()).containsKey(filename);
        }

        public SystemFlagsDataArchive build() {
            Map<FlagId, Map<String, FlagData>> copy = new TreeMap<>();
            files.forEach((flagId, map) -> copy.put(flagId, new TreeMap<>(map)));
            return new SystemFlagsDataArchive(copy);
        }

    }

    private static class JsonAccessor {
        private final JsonNode jsonNode;

        public JsonAccessor(JsonNode jsonNode) {
            this.jsonNode = jsonNode;
        }

        public JsonAccessor get(String fieldName) {
            if (jsonNode == null) {
                return this;
            } else {
                return new JsonAccessor(jsonNode.get(fieldName));
            }
        }

        public Optional<String> asString() {
            return jsonNode != null && jsonNode.isTextual() ? Optional.of(jsonNode.textValue()) : Optional.empty();
        }

        public void forEachArrayElement(Consumer<JsonAccessor> consumer) {
            if (jsonNode != null && jsonNode.isArray()) {
                jsonNode.forEach(jsonNodeElement -> consumer.accept(new JsonAccessor(jsonNodeElement)));
            }
        }

        /** Returns true if this (JsonNode) is a string and equal to value. */
        public boolean isEqualTo(String value) {
            return jsonNode != null && jsonNode.isTextual() && Objects.equals(jsonNode.textValue(), value);
        }

        @Override
        public String toString() {
            return jsonNode == null ? "undefined" : jsonNode.toString();
        }
    }
}
