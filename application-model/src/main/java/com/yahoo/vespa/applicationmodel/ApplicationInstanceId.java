// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.applicationmodel;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * @author bjorncs
 */
// TODO: Remove this and use ApplicationName/InstanceName instead (if you need it for the JSON stuff move it to that layer and don't let it leak)
public class ApplicationInstanceId {

    public static final ApplicationInstanceId CONFIG_SERVER = new ApplicationInstanceId(InfrastructureApplication.CONFIG_SERVER.applicationName());
    public static final ApplicationInstanceId CONTROLLER = new ApplicationInstanceId(InfrastructureApplication.CONTROLLER.applicationName() +
                                                                                     ":prod:default:default");
    // Unfortunately, for config server host the ApplicationInstanceId is: configserver-host:prod:cd-us-central-1:default
    public boolean isConfigServerHost() { return id.startsWith(InfrastructureApplication.CONFIG_SERVER_HOST.applicationName() + ":"); }
    public static final ApplicationInstanceId CONTROLLER_HOST = new ApplicationInstanceId(InfrastructureApplication.CONTROLLER_HOST.applicationName() +
                                                                                          ":prod:default:default");
    public boolean isTenantHost() { return id.startsWith(InfrastructureApplication.TENANT_HOST.applicationName() + ":"); }
    public boolean isProxyHost() { return id.startsWith(InfrastructureApplication.PROXY_HOST.applicationName() + ":"); }
    // Routing application instance ID is of the form: routing:prod:eu-west-1:default
    public boolean isProxy() { return id.startsWith(InfrastructureApplication.PROXY.applicationName() + ":"); }

    private final String id;

    public ApplicationInstanceId(String id) {
        this.id = id;
    }

    // Jackson's StdKeySerializer uses toString() (and ignores annotations) for objects used as Map keys.
    // Therefore, we use toString() as the JSON-producing method, which is really sad.
    @Override
    @JsonValue
    public String toString() {
        return id;
    }

    public String value() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationInstanceId that = (ApplicationInstanceId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
