// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.buildergen.ConfigDefinition;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Model that only supports the subset necessary to create an ApplicationInstance.
 *
 * @author hakon
 */
public class HostsModel implements Model {
    private final Collection<HostInfo> hosts;

    public HostsModel(List<HostInfo> hosts) {
        this.hosts = Collections.unmodifiableCollection(hosts);
    }

    @Override
    public Collection<HostInfo> getHosts() {
        return hosts;
    }

    @Override
    public ConfigPayload getConfig(ConfigKey<?> configKey, ConfigDefinition configDefinition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> allConfigIds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void distributeFiles(FileDistribution fileDistribution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<FileReference> fileReferences() {
        throw new UnsupportedOperationException();
    }

    @Override
    public AllocatedHosts allocatedHosts() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean allowModelVersionMismatch(Instant now) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean skipOldConfigModels(Instant now) {
        throw new UnsupportedOperationException();
    }
}
