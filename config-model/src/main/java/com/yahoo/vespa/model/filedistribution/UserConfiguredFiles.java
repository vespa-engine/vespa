// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.yolean.Exceptions;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.model.container.ApplicationContainerCluster.UserConfiguredUrls;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Utility methods for registering file distribution of files/paths/urls/models defined by the user.
 *
 * @author gjoranv
 * @author hmusum
 */
public class UserConfiguredFiles implements Serializable {

    private final FileRegistry fileRegistry;
    private final DeployLogger logger;
    private final UserConfiguredUrls userConfiguredUrls;
    private final String unknownConfigDefinition;
    private final ApplicationPackage applicationPackage;

    public UserConfiguredFiles(FileRegistry fileRegistry, DeployLogger logger,
                               ModelContext.FeatureFlags featureFlags,
                               UserConfiguredUrls userConfiguredUrls,
                               ApplicationPackage applicationPackage) {
        this.fileRegistry = fileRegistry;
        this.logger = logger;
        this.userConfiguredUrls = userConfiguredUrls;
        this.unknownConfigDefinition = featureFlags.unknownConfigDefinition();
        this.applicationPackage = applicationPackage;
    }

    /**
     * Registers user configured files for a producer for file distribution.
     */
    public <PRODUCER extends AnyConfigProducer> void register(PRODUCER producer) {
        UserConfigRepo userConfigs = producer.getUserConfigs();
        Map<Path, FileReference> registeredFiles = new HashMap<>();
        for (ConfigDefinitionKey key : userConfigs.configsProduced()) {
            ConfigPayloadBuilder builder = userConfigs.get(key);
            try {
                register(builder, registeredFiles, key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid config in services.xml for '" + key + "': " +
                                                   Exceptions.toMessageString(e));
            }
        }
    }

    private void register(ConfigPayloadBuilder builder, Map<Path, FileReference> registeredFiles, ConfigDefinitionKey key) {
        ConfigDefinition configDefinition = builder.getConfigDefinition();
        if (configDefinition == null) {
            String message = "Unable to find config definition " + key + ". Will not register files for file distribution for this config";
            switch (unknownConfigDefinition) {
                case "warning" -> logger.logApplicationPackage(WARNING, message);
                case "fail" -> throw new IllegalArgumentException("Unable to find config definition for " + key);
            }
            return;
        }

        // Inspect fields at this level
        registerEntries(builder, registeredFiles, configDefinition.getFileDefs(), false);
        registerEntries(builder, registeredFiles, configDefinition.getPathDefs(), false);
        registerEntries(builder, registeredFiles, configDefinition.getOptionalPathDefs(), false);
        registerEntries(builder, registeredFiles, configDefinition.getModelDefs(), true);

        // Inspect arrays
        for (Map.Entry<String, ConfigDefinition.ArrayDef> entry : configDefinition.getArrayDefs().entrySet()) {
            if (isNotAnyFileType(entry.getValue().getTypeSpec().getType())) continue;
            ConfigPayloadBuilder.Array array = builder.getArray(entry.getKey());
            registerFileEntries(array.getElements(), registeredFiles, "model".equals(entry.getValue().getTypeSpec().getType()));
        }

        // Inspect maps
        for (Map.Entry<String, ConfigDefinition.LeafMapDef> entry : configDefinition.getLeafMapDefs().entrySet()) {
            if (isNotAnyFileType(entry.getValue().getTypeSpec().getType())) continue;
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(entry.getKey());
            registerFileEntries(map.getElements(), registeredFiles, "model".equals(entry.getValue().getTypeSpec().getType()));
        }

        // Inspect inner fields
        for (String name : configDefinition.getStructDefs().keySet()) {
            register(builder.getObject(name), registeredFiles, key);
        }
        for (String name : configDefinition.getInnerArrayDefs().keySet()) {
            ConfigPayloadBuilder.Array array = builder.getArray(name);
            for (ConfigPayloadBuilder element : array.getElements()) {
                register(element, registeredFiles, key);
            }
        }
        for (String name : configDefinition.getStructMapDefs().keySet()) {
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(name);
            for (ConfigPayloadBuilder element : map.getElements()) {
                register(element, registeredFiles, key);
            }
        }
    }

    private static boolean isNotAnyFileType(String type) {
        return ! "file".equals(type) && ! "path".equals(type) && ! "model".equals(type);
    }

    private void registerEntries(ConfigPayloadBuilder builder,
                                 Map<Path, FileReference> registeredFiles,
                                 Map<String, ?> entries,
                                 boolean isModelType) {
        for (Map.Entry<String, ?> entry : entries.entrySet()) {
            String name = entry.getKey();
            ConfigPayloadBuilder fileEntry = builder.getObject(name);
            if (isEmptyOptionalPath(entry, fileEntry)) continue;
            if (fileEntry.getValue() == null || fileEntry.getValue().equals("."))
                throw new IllegalArgumentException("Invalid config value '" + fileEntry.getValue() + "' for field '" + name);
            registerFileEntry(fileEntry, registeredFiles, isModelType);
        }
    }

    private static boolean isEmptyOptionalPath(Map.Entry<String, ?> entry, ConfigPayloadBuilder fileEntry) {
        return entry.getValue() instanceof ConfigDefinition.OptionalPathDef && fileEntry.getValue() == null;
    }

    private void registerFileEntries(Collection<ConfigPayloadBuilder> builders, Map<Path, FileReference> registeredFiles, boolean isModelType) {
        for (ConfigPayloadBuilder builder : builders) {
            registerFileEntry(builder, registeredFiles, isModelType);
        }
    }

    private void registerFileEntry(ConfigPayloadBuilder builder, Map<Path, FileReference> registeredFiles, boolean isModelType) {
        Path path;
        if (isModelType) {
            var modelReference = ModelReference.valueOf(builder.getValue());
            if (modelReference.path().isEmpty()) {
                modelReference.url().ifPresent(url -> userConfiguredUrls.add(url.value()));
                return;
            }
            path = Path.fromString(modelReference.path().get().value());
        }
        else {
            path = Path.fromString(builder.getValue());
        }

        ApplicationFile file = applicationPackage.getFile(path);
        if (file.isDirectory() && (file.listFiles() == null || file.listFiles().isEmpty()))
            logger.logApplicationPackage(INFO, "Directory '" + path.getRelative() + "' is empty");

        FileReference reference = registeredFiles.get(path);
        if (reference == null) {
            reference = fileRegistry.addFile(path.getRelative());
            registeredFiles.put(path, reference);
        }
        if (reference == null)
            throw new IllegalArgumentException("No such file or directory '" + path.getRelative() + "'");

        if (isModelType) {
            var model = ModelReference.valueOf(builder.getValue());
            var modelWithReference = ModelReference.unresolved(model.modelId(), model.url(), Optional.of(reference));
            builder.setValue(modelWithReference.toString());
        }
        else {
            builder.setValue(reference.value());
        }
    }

}
