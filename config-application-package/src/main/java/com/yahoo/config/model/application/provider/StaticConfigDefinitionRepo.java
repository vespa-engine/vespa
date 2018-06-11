// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.codegen.CNode;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.vespa.defaults.Defaults;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A global pool of all config definitions that this server knows about. These objects can be shared
 * by all tenants, as they are not modified.
 *
 * @author Ulf Lilleengen
 */
public class StaticConfigDefinitionRepo implements ConfigDefinitionRepo {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(StaticConfigDefinitionRepo.class.getName());
    private final Map<ConfigDefinitionKey, ConfigDefinition> configDefinitions = new LinkedHashMap<>();
    private static final String DEFAULT_SERVER_DEF_DIR = Defaults.getDefaults().underVespaHome("share/vespa/configdefinitions");

    public StaticConfigDefinitionRepo() {
        this(new File(DEFAULT_SERVER_DEF_DIR));
    }

    public StaticConfigDefinitionRepo(File definitionDir) {
        initialize(definitionDir);
    }

    private void initialize(File definitionDir) {
        if ( ! definitionDir.exists()) return;

        for (File def : definitionDir.listFiles((dir, name) -> name.matches(".*\\.def")))
            addConfigDefinition(def);
    }

    private void addConfigDefinition(File def) {
        try {
            ConfigDefinitionKey key = ConfigUtils.createConfigDefinitionKeyFromDefFile(def);
            if (key.getNamespace().isEmpty())
                key = new ConfigDefinitionKey(key.getName(), CNode.DEFAULT_NAMESPACE);
            addConfigDefinition(key, def);
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "Exception adding config definition " + def, e);
        }
    }

    private void addConfigDefinition(ConfigDefinitionKey key, File defFile) throws IOException {
        String payload = IOUtils.readFile(defFile);
        configDefinitions.put(key, new ConfigDefinition(key.getName(), StringUtils.split(payload, "\n")));
    }

    @Override
    public Map<ConfigDefinitionKey, ConfigDefinition> getConfigDefinitions() {
        return Collections.unmodifiableMap(configDefinitions);
    }

    @Override
    public ConfigDefinition get(ConfigDefinitionKey key) { return configDefinitions.get(key); }

}
