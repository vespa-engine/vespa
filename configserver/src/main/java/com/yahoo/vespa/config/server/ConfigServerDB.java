// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.Bundle;
import com.yahoo.config.application.ConfigDefinitionDir;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.defaults.Defaults;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Config server db is the maintainer of the serverdb directory containing def files and the file system sessions.
 * See also {@link com.yahoo.vespa.config.server.deploy.TenantFileSystemDirs} which maintains directories per tenant.
 *
 * @author Ulf Lilleengen
 */
public class ConfigServerDB {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ConfigServerDB.class.getName());
    private final File serverDB;
    private final ConfigserverConfig configserverConfig;

    public ConfigServerDB(ConfigserverConfig configserverConfig) {
        if (configserverConfig.configDefinitionsDir().equals(configserverConfig.configServerDBDir()))
            throw new IllegalArgumentException("configDefinitionsDir and configServerDBDir cannot be equal ('" +
                    configserverConfig.configDefinitionsDir() + "')");
        this.configserverConfig = configserverConfig;
        this.serverDB = new File(Defaults.getDefaults().underVespaHome(configserverConfig.configServerDBDir()));
        createDirectory(serverdefs());
        try {
            initialize(configserverConfig.configModelPluginDir());
        } catch (IllegalArgumentException e) {
            log.log(LogLevel.ERROR, "Error initializing serverdb: " + e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize server db", e);
        }
    }

    // The config definitions shipped with Vespa
    public File classes() { return new File(Defaults.getDefaults().underVespaHome(configserverConfig.configDefinitionsDir()));}

    public File serverdefs() { return new File(serverDB, "serverdefs"); }

    public File path() { return serverDB; }

    public static void createDirectory(File d) {
        if (d.exists()) {
            if (!d.isDirectory()) {
                throw new IllegalArgumentException(d.getAbsolutePath() + " exists, but isn't a directory.");
            }
        } else {
            if (!d.mkdirs()) {
                throw new IllegalArgumentException("Couldn't create " + d.getAbsolutePath());
            }
        }
    }

    private void initialize(List<String> pluginDirectories) throws IOException {
        IOUtils.recursiveDeleteDir(serverdefs());
        IOUtils.copyDirectory(classes(), serverdefs(), 1);
        ConfigDefinitionDir configDefinitionDir = new ConfigDefinitionDir(serverdefs());
        ArrayList<Bundle> bundles = new ArrayList<>();
        for (String pluginDirectory : pluginDirectories) {
            bundles.addAll(Bundle.getBundles(new File(pluginDirectory)));
        }
        log.log(LogLevel.DEBUG, "Found " + bundles.size() + " bundles");
        List<Bundle> addedBundles = new ArrayList<>();
        for (Bundle bundle : bundles) {
            log.log(LogLevel.DEBUG, "Bundle in " + bundle.getFile().getAbsolutePath() + " appears to contain " + bundle.getDefEntries().size() + " entries");
            configDefinitionDir.addConfigDefinitionsFromBundle(bundle, addedBundles);
            addedBundles.add(bundle);
        }
    }

}
