// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.JSON;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.DimensionHelper;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

import static com.yahoo.config.provision.CloudName.AWS;
import static com.yahoo.config.provision.CloudName.GCP;
import static com.yahoo.config.provision.CloudName.YAHOO;
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

    public static SystemFlagsDataArchive fromZip(InputStream rawIn, ZoneRegistry zoneRegistry) {
        Builder builder = new Builder();
        try (ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(rawIn))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith("flags/")) {
                    Path filePath = Paths.get(name);
                    String rawData = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    addFile(builder, rawData, filePath, zoneRegistry, true);
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SystemFlagsDataArchive fromDirectory(Path directory, ZoneRegistry zoneRegistry, boolean forceAddFiles) {
        Path root = directory.toAbsolutePath();
        Path flagsDirectory = directory.resolve("flags");
        if (!Files.isDirectory(flagsDirectory)) {
            throw new FlagValidationException("Sub-directory 'flags' does not exist: " + flagsDirectory);
        }
        try (Stream<Path> directoryStream = Files.walk(flagsDirectory)) {
            Builder builder = new Builder();
            directoryStream.forEach(path -> {
                Path relativePath = root.relativize(path.toAbsolutePath());
                if (Files.isRegularFile(path)) {
                    String rawData = uncheck(() -> Files.readString(path, StandardCharsets.UTF_8));
                    addFile(builder, rawData, relativePath, zoneRegistry, forceAddFiles);
                }
            });
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] toZipBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            toZip(out);
            return out.toByteArray();
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

    public void validateAllFilesAreForTargets(Set<FlagsTarget> targets) throws FlagValidationException {
        Set<String> validFiles = targets.stream()
                                        .flatMap(target -> target.flagDataFilesPrioritized().stream())
                                        .collect(Collectors.toSet());
        files.forEach((flagId, fileMap) -> fileMap.keySet().forEach(filename -> {
            if (!validFiles.contains(filename)) {
                throw new FlagValidationException("Unknown flag file: " + toFilePath(flagId, filename));
            }
        }));
    }

    boolean hasFlagData(FlagId flagId, String filename) {
        return files.getOrDefault(flagId, Map.of()).containsKey(filename);
    }

    private static void addFile(Builder builder, String rawData, Path filePath, ZoneRegistry zoneRegistry, boolean force) {
        String filename = filePath.getFileName().toString();

        if (!force) {
            if (filename.startsWith("."))
                return; // Ignore files starting with '.'

            if (!FlagsTarget.filenameForSystem(filename, zoneRegistry.system()))
                return; // Ignore files for other systems
        }

        FlagId directoryDeducedFlagId = new FlagId(filePath.getName(filePath.getNameCount()-2).toString());
        FlagData flagData;
        if (rawData.isBlank()) {
            flagData = new FlagData(directoryDeducedFlagId);
        } else {
            Set<ZoneId> zones = force ? zoneRegistry.zones().all().zones().stream().map(ZoneApi::getVirtualId).collect(Collectors.toSet())
                                      : Set.of();
            String normalizedRawData = normalizeJson(rawData, zones);
            flagData = FlagData.deserialize(normalizedRawData);
            if (!directoryDeducedFlagId.equals(flagData.id())) {
                throw new FlagValidationException("Flag data file with flag id '%s' in directory for '%s'"
                                                          .formatted(flagData.id(), directoryDeducedFlagId.toString()));
            }

            String serializedData = flagData.serializeToJson();
            if (!JSON.equals(serializedData, normalizedRawData)) {
                throw new FlagValidationException("""
                                                   %s contains unknown non-comment fields or rules with null values: after removing any comment fields the JSON is:
                                                     %s
                                                   but deserializing this ended up with:
                                                     %s
                                                   These fields may be spelled wrong, or remove them?
                                                   See https://git.ouroath.com/vespa/hosted-feature-flags for more info on the JSON syntax
                                                   """.formatted(filePath, normalizedRawData, serializedData));
            }
        }

        if (builder.hasFile(filename, flagData)) {
            throw new FlagValidationException("Flag data file in '%s' contains redundant flag data for id '%s' already set in another directory!"
                                                      .formatted(filePath, flagData.id()));
        }

        builder.addFile(filename, flagData);
    }

    static String normalizeJson(String json, Set<ZoneId> zones) {
        JsonNode root = uncheck(() -> mapper.readTree(json));
        removeCommentsRecursively(root);
        removeNullRuleValues(root);
        verifyValues(root, zones);
        return root.toString();
    }

    private static void verifyValues(JsonNode root, Set<ZoneId> zones) {
        var cursor = new JsonAccessor(root);
        cursor.get("rules").forEachArrayElement(rule -> rule.get("conditions").forEachArrayElement(condition -> {
            FetchVector.Dimension dimension = DimensionHelper
                    .fromWire(condition.get("dimension")
                                       .asString()
                                       .orElseThrow(() -> new FlagValidationException("Invalid dimension in condition: " + condition)));
            switch (dimension) {
                case APPLICATION_ID -> validateStringValues(condition, ApplicationId::fromSerializedForm);
                case CONSOLE_USER_EMAIL -> validateStringValues(condition, email -> {});
                case CLOUD -> validateStringValues(condition, cloud -> {
                    if (!Set.of(YAHOO, AWS, GCP).contains(CloudName.from(cloud)))
                        throw new FlagValidationException("Unknown cloud: " + cloud);
                });
                case CLUSTER_ID -> validateStringValues(condition, ClusterSpec.Id::from);
                case CLUSTER_TYPE -> validateStringValues(condition, ClusterSpec.Type::from);
                case ENVIRONMENT -> validateStringValues(condition, Environment::from);
                case HOSTNAME -> validateStringValues(condition, HostName::of);
                case NODE_TYPE -> validateStringValues(condition, NodeType::valueOf);
                case SYSTEM -> validateStringValues(condition, system -> {
                    if (!SystemName.hostedVespa().contains(SystemName.from(system)))
                        throw new FlagValidationException("Unknown system: " + system);
                });
                case TENANT_ID -> validateStringValues(condition, TenantName::from);
                case VESPA_VERSION -> validateStringValues(condition, versionString -> {
                    if (Version.fromString(versionString).getMajor() < 8)
                        throw new FlagValidationException("Major Vespa version must be at least 8: " + versionString);
                });
                case ZONE_ID -> validateStringValues(condition, zoneIdString -> {
                    ZoneId zoneId = ZoneId.from(zoneIdString);
                    if (!zones.isEmpty() && !zones.contains(zoneId))
                        throw new FlagValidationException("Unknown zone: " + zoneIdString);
                });
            }
        }));
    }

    private static void validateStringValues(JsonAccessor condition, Consumer<String> valueValidator) {
        condition.get("values").forEachArrayElement(conditionValue -> {
            String value = conditionValue.asString()
                                         .orElseThrow(() -> {
                                             String dimension = condition.get("dimension").asString().orElseThrow();
                                             String type = condition.get("type").asString().orElseThrow();
                                             return new FlagValidationException("Non-string %s in %s condition: %s".formatted(
                                                     dimension, type, conditionValue));
                                         });
            try {
                valueValidator.accept(value);
            } catch (IllegalArgumentException e) {
                String dimension = condition.get("dimension").asString().orElseThrow();
                String type = condition.get("type").asString().orElseThrow();
                throw new FlagValidationException("Invalid %s '%s' in %s condition: %s".formatted(dimension, value, type, e.getMessage()));
            }
        });
    }

    private static void removeCommentsRecursively(JsonNode node) {
        if (node instanceof ObjectNode) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.remove("comment");
        }

        node.forEach(SystemFlagsDataArchive::removeCommentsRecursively);
    }

    private static void removeNullRuleValues(JsonNode root) {
        if (root instanceof ObjectNode objectNode) {
            JsonNode rules = objectNode.get("rules");
            if (rules != null) {
                rules.forEach(ruleNode -> {
                    if (ruleNode instanceof ObjectNode rule) {
                        JsonNode value = rule.get("value");
                        if (value != null && value.isNull()) {
                            rule.remove("value");
                        }
                    }
                });
            }
        }
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
