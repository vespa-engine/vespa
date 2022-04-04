// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.cloud.config.ApplicationIdConfig;

/**
 * A complete, immutable identification of an application instance.
 *
 * @author Ulf Lilleengen
 * @author vegard
 * @author bratseth
 */
public final class ApplicationId implements Comparable<ApplicationId> {

    private final TenantName tenant;
    private final ApplicationName application;
    private final InstanceName instance;

    private final String stringValue;
    private final String serializedForm;

    public ApplicationId(ApplicationIdConfig config) {
        this(TenantName.from(config.tenant()), ApplicationName.from(config.application()), InstanceName.from(config.instance()));
    }

    private ApplicationId(TenantName tenant, ApplicationName applicationName, InstanceName instanceName) {
        this.tenant = tenant;
        this.application = applicationName;
        this.instance = instanceName;
        this.stringValue = toStringValue();
        this.serializedForm = toSerializedForm();
    }

    public static ApplicationId from(TenantName tenant, ApplicationName application, InstanceName instance) {
        return new ApplicationId(tenant, application, instance);
    }

    public static ApplicationId from(String tenant, String application, String instance) {
        return new ApplicationId(TenantName.from(tenant), ApplicationName.from(application), InstanceName.from(instance));
    }

    public static ApplicationId fromSerializedForm(String idString) {
        String[] parts = idString.split(":");
        if (parts.length < 3)
            throw new IllegalArgumentException("Application ids must be on the form tenant:application:instance, but was " + idString);

        return new Builder().tenant(parts[0]).applicationName(parts[1]).instanceName(parts[2]).build();
    }

    public static ApplicationId fromFullString(String idString) {
        String[] parts = idString.split("\\.");
        if (parts.length < 3)
            throw new IllegalArgumentException("Application ids must be on the form tenant.application.instance, but was " + idString);

        return new Builder().tenant(parts[0]).applicationName(parts[1]).instanceName(parts[2]).build();
    }

    @Override
    public int hashCode() { return stringValue.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        ApplicationId rhs = (ApplicationId) other;
        return tenant.equals(rhs.tenant) &&
               application.equals(rhs.application) &&
               instance.equals(rhs.instance);
    }

    /** Returns a serialized form of the content of this: tenant:application:instance */
    public String serializedForm() { return serializedForm; }

    private String toStringValue() {
        return "tenant '" + tenant + "', application '" + application + "', instance '" + instance + "'";
    }

    /** Returns "dotted" string (tenant.application.instance) with instance name omitted if it is "default" */
    public String toShortString() {
        return tenant().value() + "." + application().value() +
               ( instance().isDefault() ? "" : "." + instance().value() );
    }

    /** Returns "dotted" string (tenant.application.instance) with instance name always included */
    public String toFullString() {
        return tenant().value() + "." + application().value() + "." + instance().value();
    }

    private String toSerializedForm() {
        return tenant + ":" + application + ":" + instance;
    }

    @Override
    public String toString() { return toShortString(); }

    public TenantName tenant() { return tenant; }
    public ApplicationName application() { return application; }
    public InstanceName instance() { return instance; }

    @Override
    public int compareTo(ApplicationId other) {
        int diff;

        diff = tenant.compareTo(other.tenant);
        if (diff != 0) { return diff; }

        diff = application.compareTo(other.application);
        if (diff != 0) { return diff; }

        diff = instance.compareTo(other.instance);
        if (diff != 0) { return diff; }

        return 0;
    }

    /** Returns an application id where all fields are "default" */
    public static ApplicationId defaultId() {
        return new ApplicationId(TenantName.defaultName(), ApplicationName.defaultName(), InstanceName.defaultName());
    }

    /** Returns an application id where all fields are "*" */
    public static ApplicationId global() {
        return new Builder().tenant("*")
                            .applicationName("*")
                            .instanceName("*")
                            .build();
    }

    public static class Builder {

        private TenantName tenant;
        private ApplicationName application;
        private InstanceName instance;

        public Builder() {
            this.tenant = TenantName.defaultName();
            this.application = null;
            this.instance = InstanceName.defaultName();
        }

        public Builder tenant(TenantName ten) { this.tenant = ten; return this; }
        public Builder tenant(String ten) { return tenant(TenantName.from(ten)); }

        public Builder applicationName(ApplicationName nam) { this.application = nam; return this; }
        public Builder applicationName(String nam) { return applicationName(ApplicationName.from(nam)); }

        public Builder instanceName(InstanceName ins) { this.instance = ins; return this; }
        public Builder instanceName(String ins) { return instanceName(InstanceName.from(ins)); }

        public ApplicationId build() {
            if (application == null) {
                throw new IllegalArgumentException("must set application name in builder");
            }
            return ApplicationId.from(tenant, application, instance);
        }

    }

}
