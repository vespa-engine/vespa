// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.cloud.config.ApplicationIdConfig;

import java.util.Comparator;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.util.logging.Level.SEVERE;

/**
 * A complete, immutable identification of an application instance.
 *
 * @author Ulf Lilleengen
 * @author vegard
 * @author bratseth
 */
public class ApplicationId implements Comparable<ApplicationId> {

    private static final Logger log = Logger.getLogger(ApplicationId.class.getName());

    static final Pattern namePattern = Pattern.compile("[a-zA-Z0-9_-]{1,256}");

    private static final ApplicationId global = new ApplicationId(TenantName.from("hosted-vespa"),
                                                                  ApplicationName.from("routing"),
                                                                  InstanceName.from("default")) { };

    private static final Comparator<ApplicationId> comparator = Comparator.comparing(ApplicationId::tenant)
                                                                          .thenComparing(ApplicationId::application)
                                                                          .thenComparing(ApplicationId::instance)
                                                                          .thenComparing(global::equals, Boolean::compare);

    private final TenantName tenant;
    private final ApplicationName application;
    private final InstanceName instance;
    private final String serializedForm;

    private ApplicationId(TenantName tenant, ApplicationName applicationName, InstanceName instanceName) {
        this.tenant = tenant;
        this.application = applicationName;
        this.instance = instanceName;
        this.serializedForm = toSerializedForm();
    }

    public static ApplicationId from(ApplicationIdConfig config) {
        return from(TenantName.from(config.tenant()),
                    ApplicationName.from(config.application()),
                    InstanceName.from(config.instance()));
    }

    public static ApplicationId from(TenantName tenant, ApplicationName application, InstanceName instance) {
        return new ApplicationId(tenant, application, instance);
    }

    public static ApplicationId from(String tenant, String application, String instance) {
        return new ApplicationId(TenantName.from(tenant), ApplicationName.from(application), InstanceName.from(instance));
    }

    public static ApplicationId fromSerializedForm(String idString) { return fromIdString(idString, ":"); }

    public static ApplicationId fromFullString(String idString) { return fromIdString(idString, "."); }

    private static ApplicationId fromIdString(String idString, String splitCharacter) {
        String[] parts = idString.split(Pattern.quote(splitCharacter));
        if (parts.length != 3)
            throw new IllegalArgumentException("Application ids must be on the form tenant" +
                                                       splitCharacter + "application" + splitCharacter + "instance, but was " + idString);
        return from(parts[0], parts[1], parts[2]);
    }

    @Override
    public int hashCode() { return Objects.hash(tenant, application, instance); }

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
        return tenant.value() + ":" + application.value() + ":" + instance.value();
    }

    @Override
    public String toString() { return toShortString(); }

    public TenantName tenant() { return tenant; }
    public ApplicationName application() { return application; }
    public InstanceName instance() { return instance; }

    @Override
    public int compareTo(ApplicationId other) {
        return comparator.compare(this, other);
    }

    /** Returns an application id where all fields are "default" */
    public static ApplicationId defaultId() {
        return new ApplicationId(TenantName.defaultName(), ApplicationName.defaultName(), InstanceName.defaultName());
    }

    // TODO: kill this
    /** Returns a very special application id, which is not equal to any other id. */
    public static ApplicationId global() {
        return global;
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
