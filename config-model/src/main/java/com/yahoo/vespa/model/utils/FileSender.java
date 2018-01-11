// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.utils;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.*;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.config.ConfigDefinition.DefaultValued;
import com.yahoo.vespa.model.AbstractService;

import java.io.Serializable;
import java.util.*;

/**
 * Utility methods for sending files to a collection of nodes.
 *
 * @author gjoranv
 */
public class FileSender implements Serializable {

    /**
     * Send the given file to all given services.
     *
     * @param relativePath  The path to the file, relative to the app pkg.
     * @param services  The services to send the file to.
     * @return The file reference that the file was given, never null.
     * @throws IllegalStateException if services is empty.
     */
    public static FileReference sendFileToServices(String relativePath,
                                                   Collection<? extends AbstractService> services) {
        if (services.isEmpty()) {
            throw new IllegalStateException("No service instances. Probably a standalone cluster setting up <nodes> " +
                                            "using 'count' instead of <node> tags.");
        }
        FileReference fileref = null;
        for (AbstractService service : services) {
            // The same reference will be returned from each call.
            fileref = service.sendFile(relativePath);
        }
        return fileref;
    }

    /**
     * Sends all user configured files for a producer to all given services.
     */
    public static <PRODUCER extends AbstractConfigProducer<?>>
    void sendUserConfiguredFiles(PRODUCER producer, Collection<? extends AbstractService> services, DeployLogger logger) {
        if (services.isEmpty())
            return;

        UserConfigRepo userConfigs = producer.getUserConfigs();
        Map<String, FileReference> sentFiles = new HashMap<>();
        for (ConfigDefinitionKey key : userConfigs.configsProduced()) {
            ConfigPayloadBuilder builder = userConfigs.get(key);
            try {
                sendUserConfiguredFiles(builder, sentFiles, services, key, logger);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to send files for " + key, e);
            }
        }
    }

    private static void sendUserConfiguredFiles(ConfigPayloadBuilder builder,
                                                Map<String, FileReference> sentFiles,
                                                Collection<? extends AbstractService> services,
                                                ConfigDefinitionKey key, DeployLogger logger) {
        ConfigDefinition configDefinition = builder.getConfigDefinition();
        if (configDefinition == null) {
            // TODO: throw new IllegalArgumentException("Not able to find config definition for " + builder);
            logger.log(LogLevel.WARNING, "Not able to find config definition for " + key + ". Will not send files for this config");
            return;
        }
        // Inspect fields at this level
        sendEntries(builder, sentFiles, services, configDefinition.getFileDefs());
        sendEntries(builder, sentFiles, services, configDefinition.getPathDefs());

        // Inspect arrays
        for (Map.Entry<String, ConfigDefinition.ArrayDef> entry : configDefinition.getArrayDefs().entrySet()) {
            if (isFileOrPathArray(entry)) {
                ConfigPayloadBuilder.Array array = builder.getArray(entry.getKey());
                sendFileEntries(array.getElements(), sentFiles, services);
            }
        }
        // Maps
        for (Map.Entry<String, ConfigDefinition.LeafMapDef> entry : configDefinition.getLeafMapDefs().entrySet()) {
            if (isFileOrPathMap(entry)) {
                ConfigPayloadBuilder.MapBuilder map = builder.getMap(entry.getKey());
                sendFileEntries(map.getElements(), sentFiles, services);
            }
        }

        // Inspect inner fields
        for (String name : configDefinition.getStructDefs().keySet()) {
            sendUserConfiguredFiles(builder.getObject(name), sentFiles, services, key, logger);
        }
        for (String name : configDefinition.getInnerArrayDefs().keySet()) {
            ConfigPayloadBuilder.Array array = builder.getArray(name);
            for (ConfigPayloadBuilder element : array.getElements()) {
                sendUserConfiguredFiles(element, sentFiles, services, key, logger);
            }
        }
        for (String name : configDefinition.getStructMapDefs().keySet()) {
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(name);
            for (ConfigPayloadBuilder element : map.getElements()) {
                sendUserConfiguredFiles(element, sentFiles, services, key, logger);
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

    private static void sendEntries(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, Collection<? extends AbstractService> services, Map<String, ? extends DefaultValued<String>> entries) {
        for (String name : entries.keySet()) {
            ConfigPayloadBuilder fileEntry = builder.getObject(name);
            if (fileEntry.getValue() == null) {
                throw new IllegalArgumentException("Unable to send file for field '" + name + "'. Invalid config value " + fileEntry.getValue());
            }
            sendFileEntry(fileEntry, sentFiles, services);
        }
    }

    private static void sendFileEntries(Collection<ConfigPayloadBuilder> builders, Map<String, FileReference> sentFiles, Collection<? extends AbstractService> services) {
        for (ConfigPayloadBuilder builder : builders) {
            sendFileEntry(builder, sentFiles, services);
        }
    }

    private static void sendFileEntry(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, Collection<? extends AbstractService> services) {
        String path = builder.getValue();
        FileReference reference = sentFiles.get(path);
        if (reference == null) {
            reference = sendFileToServices(path, services);
            sentFiles.put(path, reference);
        }
        builder.setValue(reference.value());
    }

}
