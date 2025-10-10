// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.util.Optional;

/**
 * Ids of validations that can be overridden
 *
 * @author bratseth
 */
public enum ValidationId {

    accessControl("access-control", "For internal use, used in zones where there should be no access-control"),
    certificateRemoval("certificate-removal", "Remove data plane certificates"),
    clusterSizeReduction("cluster-size-reduction", "NOT USED"), // TODO: Remove on Vespa 9
    configModelVersionMismatch("config-model-version-mismatch", "For internal use, allow using config models for a different Vespa version"),
    contentClusterRemoval("content-cluster-removal", "Removal (or id change) of content clusters"),
    contentTypeRemoval("schema-removal", "Removal of a schema causes deletion of all documents"),
    deploymentRemoval("deployment-removal", "Removal of production zones from deployment.xml"),
    fieldTypeChange("field-type-change", "Field type changes"),
    globalDocumentChange("global-document-change", "Changing global attribute for document types in content clusters"),
    globalEndpointChange("global-endpoint-change", "Changing global endpoints"),
    hnswSettingsChange("hnsw-settings-change", "Changes to hnsw index settings"),
    indexingChange("indexing-change", "Changing what tokens are expected and stored in field indexes"),
    indexModeChange("indexing-mode-change", "Changing the index mode (streaming, indexed, store-only) of documents"),
    pagedSettingRemoval("paged-setting-removal", "Removing paged for an attribute. May cause content nodes to run out of memory"),
    redundancyIncrease("redundancy-increase", "Not in use"), // TODO: Remove on Vespa 9
    redundancyOne("redundancy-one", "Setting redundancy=1 requires a validation override on first deployment"),
    resourcesReduction("resources-reduction", "Large reductions in node resources (> 50% of the current max total resources)"),
    skipOldConfigModels("skip-old-config-models", "For internal use, skip building old config models"),
    tensorTypeChange("tensor-type-change", "NOT USED"), // TODO: Remove on Vespa 9
    zoneEndpointChange("zone-endpoint-change", "Changing zone (possibly private) endpoint settings");

    private final String id;
    private final String description;

    ValidationId(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String value() { return id; }

    public String description() { return description; }

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
