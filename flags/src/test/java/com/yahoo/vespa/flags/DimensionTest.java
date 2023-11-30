// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
class DimensionTest {
    /**
     * A compile-time test:  If this breaks you have most likely added (or removed) a dimension?
     * If so you need to update the Dimension validation in SystemFlagsDataArchive.
     */
    @SuppressWarnings("unused")
    public String remember_to_update_SystemFlagsDataArchive(Dimension dimension) {
        return switch (dimension) {
            case APPLICATION, ARCHITECTURE, CLAVE, CLOUD, CLOUD_ACCOUNT, CLUSTER_ID, CLUSTER_TYPE,
                    CONSOLE_USER_EMAIL, ENVIRONMENT, HOSTNAME, INSTANCE_ID, NODE_TYPE, SYSTEM, TENANT_ID,
                    VESPA_VERSION, ZONE_ID -> dimension.toWire();
        };
    }
}