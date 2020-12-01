// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author bratseth
 */
public class ModelStub implements Model {

    @Override
    public ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition targetDef) {
        return null;
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced() {
        return null;
    }

    @Override
    public Collection<HostInfo> getHosts() {
        return null;
    }

    @Override
    public Set<String> allConfigIds() {
        return null;
    }

    @Override
    public void distributeFiles(FileDistribution fileDistribution) { }

    @Override
    public Set<FileReference> fileReferences() { return new HashSet<>(); }

    @Override
    public AllocatedHosts allocatedHosts() {
        return null;
    }

}
