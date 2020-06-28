// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.config.FileReference;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.component.Version;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.GetConfigRequest;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.ReloadHandler;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantHandlerProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test utility class
 *
 * @author Ulf Lilleengen
 */
public class MockRequestHandler implements RequestHandler, ReloadHandler, TenantHandlerProvider {

    private Set<ConfigKey<?>> allConfigs = new HashSet<>();
    public Map<ApplicationId, ConfigResponse> responses = new LinkedHashMap<>();
    private final ApplicationId applicationId;

    public MockRequestHandler(ApplicationId applicationId) {
        this.applicationId = applicationId;
    }

    @Override
    public ConfigResponse resolveConfig(ApplicationId appId, GetConfigRequest req, Optional<Version> vespaVersion) {
        return responses.get(appId);
    }

    @Override
    public Set<ConfigKey<?>> listConfigs(ApplicationId appId, Optional<Version> vespaVersion, boolean recursive) {
        return Collections.emptySet();
    }

    @Override
    public void removeApplication(ApplicationId applicationId) { }

    @Override
    public void removeApplicationsExcept(Set<ApplicationId> applicationIds) { }

    @Override
    public void reloadConfig(ApplicationSet application) { }

    @Override
    public Set<ConfigKey<?>> listNamedConfigs(ApplicationId appId, Optional<Version> vespaVersion, ConfigKey<?> key, boolean recursive) {
        return Collections.emptySet();
    }

    @Override
    public Set<String> allConfigIds(ApplicationId appId, Optional<Version> vespaVersion) {
        Set<String> ret = new HashSet<>();
        for (ConfigKey<?> k : allConfigs) {
            ret.add(k.getConfigId());
        }
        return ret;
    }

    @Override
    public Set<ConfigKey<?>> allConfigsProduced(ApplicationId appId, Optional<Version> vespaVersion) {
        return allConfigs;
    }

    public void setAllConfigs(Set<ConfigKey<?>> allConfigs) {
        this.allConfigs = allConfigs;
    }

    @Override
    public boolean hasApplication(ApplicationId appId, Optional<Version> vespaVersion) {
        return responses.containsKey(appId);
    }

    @Override
    public ApplicationId resolveApplicationId(String hostName) {
        return applicationId;
    }

    @Override
    public Set<FileReference> listFileReferences(ApplicationId applicationId) {
        return Set.of();
    }

    @Override
    public RequestHandler getRequestHandler() {
        return this;
    }

}
