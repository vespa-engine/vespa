// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.util.Optional;

/**
 * Ids of validations that can be overridden
 *
 * @author bratseth
 */
public enum ValidationId {

    indexingChange("indexing-change"), // Changing what tokens are expected and stored in field indexes
    indexModeChange("indexing-mode-change"), // Changing the index mode (streaming, indexed, store-only) of documents 
    fieldTypeChange("field-type-change"), // Field type changes
    clusterSizeReduction("cluster-size-reduction"), // NOT USED. TODO: Remove on Vespa 9
    tensorTypeChange("tensor-type-change"), // Tensor type change
    resourcesReduction("resources-reduction"), // Large reductions in node resources (> 50% of the current max total resources)
    contentTypeRemoval("schema-removal"), // Removal of a schema (causes deletion of all documents)
    contentClusterRemoval("content-cluster-removal"), // Removal (or id change) of content clusters
    deploymentRemoval("deployment-removal"), // Removal of production zones from deployment.xml
    globalDocumentChange("global-document-change"), // Changing global attribute for document types in content clusters
    configModelVersionMismatch("config-model-version-mismatch"), // Internal use
    skipOldConfigModels("skip-old-config-models"), // Internal use
    accessControl("access-control"), // Internal use, used in zones where there should be no access-control
    globalEndpointChange("global-endpoint-change"), // Changing global endpoints
    zoneEndpointChange("zone-endpoint-change"), // Changing zone (possibly private) endpoint settings
    redundancyIncrease("redundancy-increase"), // Increasing redundancy - may easily cause feed blocked
    redundancyOne("redundancy-one"), // redundancy=1 requires a validation override on first deployment
    pagedSettingRemoval("paged-setting-removal"), // May cause content nodes to run out of memory
    certificateRemoval("certificate-removal"); // Remove data plane certificates

    private final String id;

    ValidationId(String id) { this.id = id; }

    public String value() { return id; }

    @Override
    public String toString() { return id; }

    /**
     * Returns the validation id from this string.
     * Use this instead of valueOf to match string on the (canonical) dash-separated form.
     *
     * @return the matching validation id or empty if none
     */
    public static Optional<ValidationId> from(String id) {
        // ToDo: Vespa 9 remove support for content-type-removal
        if ("content-type-removal".equals(id)) return from("schema-removal");

        for (ValidationId candidate : ValidationId.values())
            if (id.equals(candidate.toString())) return Optional.of(candidate);
        return Optional.empty();
    }

}
