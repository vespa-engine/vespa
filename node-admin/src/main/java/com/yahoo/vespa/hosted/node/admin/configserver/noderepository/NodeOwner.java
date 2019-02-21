// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.ApplicationId;

/**
 * @author freva
 */
public class NodeOwner {
    private final String tenant;
    private final String application;
    private final String instance;

    public NodeOwner(String tenant, String application, String instance) {
        this.tenant = tenant;
        this.application = application;
        this.instance = instance;
    }

    public String getTenant() {
        return tenant;
    }

    public String getApplication() {
        return application;
    }

    public String getInstance() {
        return instance;
    }

    public ApplicationId asApplicationId() {
        return ApplicationId.from(tenant, application, instance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeOwner owner = (NodeOwner) o;

        if (!tenant.equals(owner.tenant)) return false;
        if (!application.equals(owner.application)) return false;
        return instance.equals(owner.instance);

    }

    @Override
    public int hashCode() {
        int result = tenant.hashCode();
        result = 31 * result + application.hashCode();
        result = 31 * result + instance.hashCode();
        return result;
    }

    public String toString() {
        return "Owner {" +
                " tenant = " + tenant +
                " application = " + application +
                " instance = " + instance +
                " }";
    }
}
