// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.systemflags.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
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
import com.yahoo.vespa.flags.json.Condition;
import com.yahoo.vespa.flags.json.DimensionHelper;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.RelationalCondition;
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
import static com.yahoo.vespa.flags.FetchVector.Dimension.SYSTEM;
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
                    String fileContent = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    builder.maybeAddFile(filePath, fileContent, zoneRegistry, true);
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SystemFlagsDataArchive fromDirectory(Path directory, ZoneRegistry zoneRegistry, boolean simulateInController) {
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
                    String fileContent = uncheck(() -> Files.readString(path, StandardCharsets.UTF_8));
                    builder.maybeAddFile(relativePath, fileContent, zoneRegistry, simulateInController);
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

    private static void validateSystems(FlagData flagData) throws FlagValidationException {
        flagData.rules().forEach(rule -> rule.conditions().forEach(condition -> {
            if (condition.dimension() == SYSTEM) {
                validateConditionValues(condition, system -> {
                    if (!SystemName.hostedVespa().contains(SystemName.from(system)))
                        throw new FlagValidationException("Unknown system: " + system);
                });
            }
        }));
    }

    private static void validateForSystem(FlagData flagData, ZoneRegistry zoneRegistry, boolean inController) throws FlagValidationException {
        Set<ZoneId> zones = inController ?
                            zoneRegistry.zonesIncludingSystem().all().zones().stream().map(ZoneApi::getVirtualId).collect(Collectors.toSet()) :
                            null;

        flagData.rules().forEach(rule -> rule.conditions().forEach(condition -> {
            int force_switch_expression_dummy = switch (condition.type()) {
                case RELATIONAL -> switch (condition.dimension()) {
                    case APPLICATION_ID, CLOUD, CLOUD_ACCOUNT, CLUSTER_ID, CLUSTER_TYPE, CONSOLE_USER_EMAIL, 
                            ENVIRONMENT, HOSTNAME, NODE_TYPE, SYSTEM, TENANT_ID, ZONE_ID ->
                            throw new FlagValidationException(condition.type().toWire() + " " +
                                                              DimensionHelper.toWire(condition.dimension()) +
                                                              " condition is not supported");
                    case VESPA_VERSION -> {
                        RelationalCondition rCond = RelationalCondition.create(condition.toCreateParams());
                        Version version = Version.fromString(rCond.relationalPredicate().rightOperand());
                        if (version.getMajor() < 8)
                            throw new FlagValidationException("Major Vespa version must be at least 8: " + version);
                        yield 0;
                    }
                };

                case WHITELIST, BLACKLIST -> switch (condition.dimension()) {
                    case APPLICATION_ID -> validateConditionValues(condition, ApplicationId::fromSerializedForm);
                    case CONSOLE_USER_EMAIL -> validateConditionValues(condition, email -> {
                        if (!email.contains("@"))
                            throw new FlagValidationException("Invalid email address: " + email);
                    });
                    case CLOUD -> validateConditionValues(condition, cloud -> {
                        if (!Set.of(YAHOO, AWS, GCP).contains(CloudName.from(cloud)))
                            throw new FlagValidationException("Unknown cloud: " + cloud);
                    });
                    case CLOUD_ACCOUNT -> validateConditionValues(condition, CloudAccount::from);
                    case CLUSTER_ID -> validateConditionValues(condition, ClusterSpec.Id::from);
                    case CLUSTER_TYPE -> validateConditionValues(condition, ClusterSpec.Type::from);
                    case ENVIRONMENT -> validateConditionValues(condition, Environment::from);
                    case HOSTNAME -> validateConditionValues(condition, HostName::of);
                    case NODE_TYPE -> validateConditionValues(condition, NodeType::valueOf);
                    case SYSTEM -> throw new IllegalStateException("Flag data contains system dimension");
                    case TENANT_ID -> validateConditionValues(condition, TenantName::from);
                    case VESPA_VERSION -> throw new FlagValidationException(condition.type().toWire() + " " +
                                                                            DimensionHelper.toWire(condition.dimension()) +
                                                                            " condition is not supported");
                    case ZONE_ID -> validateConditionValues(condition, zoneIdString -> {
                        ZoneId zoneId = ZoneId.from(zoneIdString);
                        if (inController && !zones.contains(zoneId))
                            throw new FlagValidationException("Unknown zone: " + zoneIdString);
                    });
                };
            };
        }));
    }

    private static int validateConditionValues(Condition condition, Consumer<String> valueValidator) {
        condition.toCreateParams().values().forEach(value -> {
            try {
                valueValidator.accept(value);
            } catch (IllegalArgumentException e) {
                String dimension = DimensionHelper.toWire(condition.dimension());
                String type = condition.type().toWire();
                throw new FlagValidationException("Invalid %s '%s' in %s condition: %s".formatted(dimension, value, type, e.getMessage()));
            }
        });

        return 0;  // dummy to force switch expression
    }

    private static FlagData parseFlagData(FlagId flagId, String fileContent, ZoneRegistry zoneRegistry, boolean inController) {
        if (fileContent.isBlank()) return new FlagData(flagId);

        final JsonNode root;
        try {
            root = mapper.readTree(fileContent);
        } catch (JsonProcessingException e) {
            throw new FlagValidationException("Invalid JSON: " + e.getMessage());
        }

        removeCommentsRecursively(root);
        removeNullRuleValues(root);
        String normalizedRawData = root.toString();
        FlagData flagData = FlagData.deserialize(normalizedRawData);

        if (!flagId.equals(flagData.id()))
            throw new FlagValidationException("Flag ID specified in file (%s) doesn't match the directory name (%s)"
                                                      .formatted(flagData.id(), flagId.toString()));

        String serializedData = flagData.serializeToJson();
        if (!JSON.equals(serializedData, normalizedRawData))
            throw new FlagValidationException("""
                                               Unknown non-comment fields or rules with null values: after removing any comment fields the JSON is:
                                                 %s
                                               but deserializing this ended up with:
                                                 %s
                                               These fields may be spelled wrong, or remove them?
                                               See https://git.ouroath.com/vespa/hosted-feature-flags for more info on the JSON syntax
                                               """.formatted(normalizedRawData, serializedData));

        validateSystems(flagData);
        flagData = flagData.partialResolve(new FetchVector().with(SYSTEM, zoneRegistry.system().value()));

        validateForSystem(flagData, zoneRegistry, inController);

        return flagData;
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

        boolean maybeAddFile(Path filePath, String fileContent, ZoneRegistry zoneRegistry, boolean inController) {
            String filename = filePath.getFileName().toString();

            if (filename.startsWith("."))
                return false; // Ignore files starting with '.'

            if (!inController && !FlagsTarget.filenameForSystem(filename, zoneRegistry.system()))
                return false; // Ignore files for other systems

            FlagId directoryDeducedFlagId = new FlagId(filePath.getName(filePath.getNameCount()-2).toString());

            if (hasFile(filename, directoryDeducedFlagId))
                throw new FlagValidationException("Flag data file in '%s' contains redundant flag data for id '%s' already set in another directory!"
                                                          .formatted(filePath, directoryDeducedFlagId));

            final FlagData flagData;
            try {
                flagData = parseFlagData(directoryDeducedFlagId, fileContent, zoneRegistry, inController);
            } catch (FlagValidationException e) {
                throw new FlagValidationException("In file " + filePath + ": " + e.getMessage());
            }

            addFile(filename, flagData);
            return true;
        }

        public Builder addFile(String filename, FlagData data) {
            files.computeIfAbsent(data.id(), k -> new TreeMap<>()).put(filename, data);
            return this;
        }

        public boolean hasFile(String filename, FlagId id) {
            return files.containsKey(id) && files.get(id).containsKey(filename);
        }

        public SystemFlagsDataArchive build() {
            Map<FlagId, Map<String, FlagData>> copy = new TreeMap<>();
            files.forEach((flagId, map) -> copy.put(flagId, new TreeMap<>(map)));
            return new SystemFlagsDataArchive(copy);
        }

    }
}
