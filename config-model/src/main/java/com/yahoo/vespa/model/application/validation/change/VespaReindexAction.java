// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.model.api.ConfigChangeReindexAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents an action to re-index a document type in order to handle a config change.
 *
 * @author bjorncs
 */
public class VespaReindexAction extends VespaConfigChangeAction implements ConfigChangeReindexAction {

    /**
     * The name of this action, which must be a valid ValidationId. This is a string here because
     * the validation ids belong to the Vespa model while these names are exposed to the config server,
     * which is model version independent.
     */
    private final String name;
    private final String documentType;
    private final boolean allowed;

    private VespaReindexAction(ClusterSpec.Id id, String name, String message, List<ServiceInfo> services, String documentType, boolean allowed) {
        super(id, message, services);
        this.name = name;
        this.documentType = documentType;
        this.allowed = allowed;
    }

    public static VespaReindexAction of(
            ClusterSpec.Id id, String name, ValidationOverrides overrides, String message, Instant now) {
        return new VespaReindexAction(id, name, message, List.of(), /*documentType*/null, overrides.allows(name, now));
    }

    public static VespaReindexAction of(
            ClusterSpec.Id id, String name, ValidationOverrides overrides, String message,
            List<ServiceInfo> services, String documentType, Instant now) {
        return new VespaReindexAction(id, name, message, services, documentType, overrides.allows(name, now));
    }

    @Override
    public VespaConfigChangeAction modifyAction(String newMessage, List<ServiceInfo> newServices, String documentType) {
        return new VespaReindexAction(clusterId(), name, newMessage, newServices, documentType, allowed);
    }

    @Override public String name() { return name; }
    @Override public String getDocumentType() { return documentType; }
    @Override public boolean allowed() { return allowed; }
    @Override public boolean ignoreForInternalRedeploy() { return false; }
    @Override public String toString() { return super.toString() + ", documentType='" + documentType + "'"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VespaReindexAction that = (VespaReindexAction) o;
        return allowed == that.allowed &&
                Objects.equals(name, that.name) &&
                Objects.equals(documentType, that.documentType);
    }

    @Override public int hashCode() { return Objects.hash(super.hashCode(), name, documentType, allowed); }
}
