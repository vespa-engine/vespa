// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * The application id this is running as.
 * This is a combination of a tenant, application, and instance name.
 *
 * @author bratseth
 */
public class ApplicationId {

    private final String tenant;
    private final String application;
    private final String instance;

    public ApplicationId(String tenant, String application, String instance) {
        this.tenant = tenant;
        this.application = application;
        this.instance = instance;
    }

    public String tenant() { return tenant; }
    public String application() { return application; }
    public String instance() { return instance; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ApplicationId)) return false;
        ApplicationId other = (ApplicationId)o;
        if ( ! other.tenant.equals(this.tenant)) return false;
        if ( ! other.application.equals(this.application)) return false;
        if ( ! other.instance.equals(this.instance)) return false;
        return true;
    }

    @Override
    public int hashCode() { return Objects.hash(tenant, application, instance); }

    /** Returns the string tenant.application.instance */
    @Override
    public String toString() { return tenant + "." + application + "." + instance; }

}
