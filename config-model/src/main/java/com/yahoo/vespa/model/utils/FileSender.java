// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinition.DefaultValued;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.ConfigPayloadBuilder;
import com.yahoo.vespa.model.AbstractService;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
     * Send the given file to all given services.
     *
     * @param fileReference  The file reference to send.
     * @param services  The services to send the file to.
     * @throws IllegalStateException if services is empty.
     */
    public static void send(FileReference fileReference, Collection<? extends AbstractService> services) {
        if (services.isEmpty()) {
            throw new IllegalStateException("No service instances. Probably a standalone cluster setting up <nodes> " +
                    "using 'count' instead of <node> tags.");
        }

        for (AbstractService service : services) {
            // The same reference will be returned from each call.
            service.send(fileReference);
        }
    }

    /**
     * Sends all user configured files for a producer to all given services.
     */
    public <PRODUCER extends AbstractConfigProducer<?>>
    void sendUserConfiguredFiles(PRODUCER producer) {
        if (services.isEmpty())
            return;

        UserConfigRepo userConfigs = producer.getUserConfigs();
        Map<String, FileReference> sentFiles = new HashMap<>();
        for (ConfigDefinitionKey key : userConfigs.configsProduced()) {
            ConfigPayloadBuilder builder = userConfigs.get(key);
            try {
                sendUserConfiguredFiles(builder, sentFiles, key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to send files for " + key, e);
            }
        }
    }

    private void sendUserConfiguredFiles(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, ConfigDefinitionKey key) {
        ConfigDefinition configDefinition = builder.getConfigDefinition();
        if (configDefinition == null) {
            // TODO: throw new IllegalArgumentException("Not able to find config definition for " + builder);
            logger.logApplicationPackage(Level.FINE, "Not able to find config definition for " + key + ". Will not send files for this config");
            return;
        }
        // Inspect fields at this level
        sendEntries(builder, sentFiles, configDefinition.getFileDefs());
        sendEntries(builder, sentFiles, configDefinition.getPathDefs());

        // Inspect arrays
        for (Map.Entry<String, ConfigDefinition.ArrayDef> entry : configDefinition.getArrayDefs().entrySet()) {
            if (isFileOrPathArray(entry)) {
                ConfigPayloadBuilder.Array array = builder.getArray(entry.getKey());
                sendFileEntries(array.getElements(), sentFiles);
            }
        }
        // Maps
        for (Map.Entry<String, ConfigDefinition.LeafMapDef> entry : configDefinition.getLeafMapDefs().entrySet()) {
            if (isFileOrPathMap(entry)) {
                ConfigPayloadBuilder.MapBuilder map = builder.getMap(entry.getKey());
                sendFileEntries(map.getElements(), sentFiles);
            }
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

    private static boolean isFileOrPathMap(Map.Entry<String, ConfigDefinition.LeafMapDef> entry) {
        String mapType = entry.getValue().getTypeSpec().getType();
        return ("file".equals(mapType) || "path".equals(mapType));
    }

    private static boolean isFileOrPathArray(Map.Entry<String, ConfigDefinition.ArrayDef> entry) {
        String arrayType = entry.getValue().getTypeSpec().getType();
        return ("file".equals(arrayType) || "path".equals(arrayType));
    }

    private void sendEntries(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, Map<String, ? extends DefaultValued<String>> entries) {
        for (String name : entries.keySet()) {
            ConfigPayloadBuilder fileEntry = builder.getObject(name);
            if (fileEntry.getValue() == null) {
                throw new IllegalArgumentException("Unable to send file for field '" + name + "'. Invalid config value " + fileEntry.getValue());
            }
            sendFileEntry(fileEntry, sentFiles);
        }
    }

    private void sendFileEntries(Collection<ConfigPayloadBuilder> builders, Map<String, FileReference> sentFiles) {
        for (ConfigPayloadBuilder builder : builders) {
            sendFileEntry(builder, sentFiles);
        }
    }

    private void sendFileEntry(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles) {
        String path = builder.getValue();
        FileReference reference = sentFiles.get(path);
        if (reference == null) {
            reference = fileRegistry.addFile(path);
            send(reference, services);
            sentFiles.put(path, reference);
        }
        builder.setValue(reference.value());
    }

}
