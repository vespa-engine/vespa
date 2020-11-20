// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.api.ConfigChangeRefeedAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;

import java.util.List;
import java.util.Optional;

/**
 * @author geirst
 */
public class MockRefeedAction extends MockConfigChangeAction implements ConfigChangeRefeedAction {

    private final ValidationId validationId;
    private final String documentType;

    public MockRefeedAction(ValidationId validationId, String message, List<ServiceInfo> services, String documentType) {
        super(message, services);
        this.validationId = validationId;
        this.documentType = documentType;
    }

    @Override
    public Optional<ValidationId> validationId() { return Optional.of(validationId); }

    @Override
    public ClusterSpec.Id clusterId() {
        return null;
    }

    @Override
    public boolean ignoreForInternalRedeploy() {
        return false;
    }

    @Override
    public String getDocumentType() { return documentType; }

}
