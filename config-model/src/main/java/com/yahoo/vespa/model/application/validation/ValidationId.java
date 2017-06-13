// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import java.util.Optional;

/**
 * Ids of validations that can be overridden
 *
 * @author bratseth
 */
public enum ValidationId {

    indexingChange("indexing-change"),
    indexModeChange("indexing-mode-change"),
    fieldTypeChange("field-type-change"),
    clusterSizeReduction("cluster-size-reduction"),
    contentClusterRemoval("content-cluster-removal"),
    configModelVersionMismatch("config-model-version-mismatch"),
    skipOldConfigModels("skip-old-config-models"),
    skipAutomaticTenantUpgradeTests("skip-automatic-tenant-upgrade-test"),
    forceAutomaticTenantUpgradeTests("force-automatic-tenant-upgrade-test");

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
        for (ValidationId candidate : ValidationId.values())
            if (id.equals(candidate.toString())) return Optional.of(candidate);
        return Optional.empty();
    }

}
