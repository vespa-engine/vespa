// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * Utility methods for registering files to a service.
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
     * Registers the given file reference to the given services.
     *
     * @param fileReference  The file reference to send.
     * @param services  The services to send the file to.
     * @throws IllegalStateException if services is empty.
     */
    public static void registerFileReference(FileReference fileReference, Collection<? extends AbstractService> services) {
        if (services.isEmpty()) {
            throw new IllegalStateException("No service instances. Probably a standalone cluster setting up <nodes> " +
                                            "using 'count' instead of <node> tags.");
        }

        services.forEach(s -> s.registerFileReference(fileReference));
    }

    /**
     * Register all user configured files for a producer to all given services.
     */
    public <PRODUCER extends AbstractConfigProducer<?>> void registerUserConfiguredFiles(PRODUCER producer) {
        if (services.isEmpty())
            return;

        UserConfigRepo userConfigs = producer.getUserConfigs();
        Map<String, FileReference> sentFiles = new HashMap<>();
        for (ConfigDefinitionKey key : userConfigs.configsProduced()) {
            ConfigPayloadBuilder builder = userConfigs.get(key);
            try {
                registerUserConfiguredFiles(builder, sentFiles, key);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to send file specified in " + key, e);
            }
        }
    }

    private void registerUserConfiguredFiles(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, ConfigDefinitionKey key) {
        ConfigDefinition configDefinition = builder.getConfigDefinition();
        if (configDefinition == null) {
            // TODO: throw new IllegalArgumentException("Not able to find config definition for " + builder);
            logger.logApplicationPackage(Level.FINE, "Not able to find config definition for " + key +
                                                     ". Will not send files for this config");
            return;
        }
        // Inspect fields at this level
        registerEntries(builder, sentFiles, configDefinition.getFileDefs());
        registerEntries(builder, sentFiles, configDefinition.getPathDefs());

        // Inspect arrays
        for (Map.Entry<String, ConfigDefinition.ArrayDef> entry : configDefinition.getArrayDefs().entrySet()) {
            if (isFileOrPathArray(entry)) {
                ConfigPayloadBuilder.Array array = builder.getArray(entry.getKey());
                registerFileEntries(array.getElements(), sentFiles);
            }
        }
        // Maps
        for (Map.Entry<String, ConfigDefinition.LeafMapDef> entry : configDefinition.getLeafMapDefs().entrySet()) {
            if (isFileOrPathMap(entry)) {
                ConfigPayloadBuilder.MapBuilder map = builder.getMap(entry.getKey());
                registerFileEntries(map.getElements(), sentFiles);
            }
        }

        // Inspect inner fields
        for (String name : configDefinition.getStructDefs().keySet()) {
            registerUserConfiguredFiles(builder.getObject(name), sentFiles, key);
        }
        for (String name : configDefinition.getInnerArrayDefs().keySet()) {
            ConfigPayloadBuilder.Array array = builder.getArray(name);
            for (ConfigPayloadBuilder element : array.getElements()) {
                registerUserConfiguredFiles(element, sentFiles, key);
            }
        }
        for (String name : configDefinition.getStructMapDefs().keySet()) {
            ConfigPayloadBuilder.MapBuilder map = builder.getMap(name);
            for (ConfigPayloadBuilder element : map.getElements()) {
                registerUserConfiguredFiles(element, sentFiles, key);
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

    private void registerEntries(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles, Map<String, ? extends DefaultValued<String>> entries) {
        for (String name : entries.keySet()) {
            ConfigPayloadBuilder fileEntry = builder.getObject(name);
            if (fileEntry.getValue() == null) {
                throw new IllegalArgumentException("Unable to send file for field '" + name + "': Invalid config value " + fileEntry.getValue());
            }
            registerFileEntry(fileEntry, sentFiles);
        }
    }

    private void registerFileEntries(Collection<ConfigPayloadBuilder> builders, Map<String, FileReference> sentFiles) {
        for (ConfigPayloadBuilder builder : builders) {
            registerFileEntry(builder, sentFiles);
        }
    }

    private void registerFileEntry(ConfigPayloadBuilder builder, Map<String, FileReference> sentFiles) {
        String path = builder.getValue();
        FileReference reference = sentFiles.get(path);
        if (reference == null) {
            reference = fileRegistry.addFile(path);
            registerFileReference(reference, services);
            sentFiles.put(path, reference);
        }
        builder.setValue(reference.value());
    }

}
