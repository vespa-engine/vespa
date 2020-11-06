// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.configchange;

import com.yahoo.config.model.api.ConfigChangeRefeedAction;
import com.yahoo.config.model.api.ServiceInfo;

import java.util.List;

/**
 * @author geirst
 */
public class MockRefeedAction extends MockConfigChangeAction implements ConfigChangeRefeedAction {

    private final String name;
    private final boolean allowed;
    private final String documentType;

    public MockRefeedAction(String name, boolean allowed, String message, List<ServiceInfo> services, String documentType) {
        super(message, services);
        this.name = name;
        this.allowed = allowed;
        this.documentType = documentType;
    }

    @Override
    public String name() { return name; }

    @Override
    public boolean allowed() { return allowed; }

    @Override
    public boolean ignoreForInternalRedeploy() {
        return false;
    }

    @Override
    public String getDocumentType() { return documentType; }

}
