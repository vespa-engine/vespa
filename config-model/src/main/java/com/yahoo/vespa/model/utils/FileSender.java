// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import com.yahoo.config.FileReference;
import com.yahoo.config.ModelReference;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.AbstractService;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Utility methods for sending files to a collection of nodes.
 *
 * @author gjoranv
 */
public class FileSender implements Serializable {

    private final Collection<? extends AbstractService> services;
    private final FileRegistry fileRegistry;
    private final DeployLogger logger;

    public FileSender(Collection<? extends AbstractService> services, FileRegistry fileRegistry, DeployLogger logger) {
        this.services = services;
        this.fileRegistry = fileRegistry;
        this.logger = logger;
    }

    /**
     * Sends all user configured files for a producer to all given services.
     */
    public <PRODUCER extends AnyConfigProducer> void sendUserConfiguredFiles(PRODUCER producer) {
        if (services.isEmpty()) return;

        UserConfigRepo userConfigs = producer.getUserConfigs();
        Map<Path, FileReference> sentFiles = new HashMap<>();
        for (ConfigDefinitionKey key : userConfigs.configsProduced()) {
            ConfigPayloadBuilder builder = userConfigs.get(key);
            try {
                sendUserConfiguredFiles(builder, sentFiles, key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to send file specified in " + key, e);
            }
        }
    }

    private void sendUserConfiguredFiles(ConfigPayloadBuilder builder, Map<Path, FileReference> sentFiles, ConfigDefinitionKey key) {
        ConfigDefinition configDefinition = builder.getConfigDefinition();
        if (configDefinition == null) {
            // TODO: throw new IllegalArgumentException("Not able to find config definition for " + builder);
            logger.logApplicationPackage(Level.FINE, "Not able to find config definition for " + key +
                                                     ". Will not send files for this config");
            return;
        }
        // Inspect fields at this level
        sendEntries(builder, sentFiles, configDefinition.getFileDefs(), false);
        sendEntries(builder, sentFiles, configDefinition.getPathDefs(), false);
        sendEntries(builder, sentFiles, configDefinition.getModelDefs(), true);

        // Inspect arrays
        for (Map.Entry<String, ConfigDefinition.ArrayDef> entry : configDefinition.getArrayDefs().entrySet()) {
            if ( ! isAnyFileType(entry.getValue().getTypeSpec().getType())) continue;
            ConfigPayloadBuilder.Array array = builder.getArray(entry.getKey());
            sendFileEntries(array.getElements(), sentFiles, "model".equals(entry.getValue().getTypeSpec().getType()));
        }

        // Inspect maps
        for (Map.Entry<String, ConfigDefinition.LeafMapDef> entry : configDefinition.getLeafMapDefs().entrySet()) {
            if ( ! isAnyFileType(entry.getValue().getTypeSpec().getType())) continue;
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(entry.getKey());
            sendFileEntries(map.getElements(), sentFiles, "model".equals(entry.getValue().getTypeSpec().getType()));
        }

        // Inspect inner fields
        for (String name : configDefinition.getStructDefs().keySet()) {
            sendUserConfiguredFiles(builder.getObject(name), sentFiles, key);
        }
        for (String name : configDefinition.getInnerArrayDefs().keySet()) {
            ConfigPayloadBuilder.Array array = builder.getArray(name);
            for (ConfigPayloadBuilder element : array.getElements()) {
                sendUserConfiguredFiles(element, sentFiles, key);
            }
        }
        for (String name : configDefinition.getStructMapDefs().keySet()) {
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(name);
            for (ConfigPayloadBuilder element : map.getElements()) {
                sendUserConfiguredFiles(element, sentFiles, key);
            }
        }
    }

    private static boolean isAnyFileType(String type) {
        return "file".equals(type) || "path".equals(type) || "model".equals(type);
    }

    private void sendEntries(ConfigPayloadBuilder builder,
                             Map<Path, FileReference> sentFiles,
                             Map<String, ?> entries,
                             boolean isModelType) {
        for (String name : entries.keySet()) {
            ConfigPayloadBuilder fileEntry = builder.getObject(name);
            if (fileEntry.getValue() == null)
                throw new IllegalArgumentException("Unable to send file for field '" + name +
                                                   "': Invalid config value " + fileEntry.getValue());
            sendFileEntry(fileEntry, sentFiles, isModelType);
        }
    }

    private void sendFileEntries(Collection<ConfigPayloadBuilder> builders, Map<Path, FileReference> sentFiles, boolean isModelType) {
        for (ConfigPayloadBuilder builder : builders) {
            sendFileEntry(builder, sentFiles, isModelType);
        }
    }

    private void sendFileEntry(ConfigPayloadBuilder builder, Map<Path, FileReference> sentFiles, boolean isModelType) {
        Path path;
        if (isModelType) {
            var modelReference = ModelReference.valueOf(builder.getValue());
            if (modelReference.path().isEmpty()) return;
            path = Path.fromString(modelReference.path().get().value());
        }
        else {
            path = Path.fromString(builder.getValue());
        }

        FileReference reference = sentFiles.get(path);
        if (reference == null) {
            reference = fileRegistry.addFile(path.getRelative());
            sentFiles.put(path, reference);
        }

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
