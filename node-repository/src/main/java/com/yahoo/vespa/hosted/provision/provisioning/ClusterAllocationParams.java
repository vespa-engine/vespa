// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.custom.SharedHost;

/**
 * Miscellaneous constants used while preparing an allocation for a cluster.
 *
 * <p>Typically used to access feature flags that was evaluated once and at the start of the preparation,
 * to avoid inconsistencies if evaluated multiple times during preparation.</p>
 *
 * @author hakonhall
 */
public record ClusterAllocationParams(boolean makeExclusive, SharedHost sharedHost) {
    public static ClusterAllocationParams from(FlagSource flagSource, ApplicationId application, Version version) {
        return new ClusterAllocationParams(Flags.MAKE_EXCLUSIVE.bindTo(flagSource)
                                                               .with(FetchVector.Dimension.TENANT_ID, application.tenant().value())
                                                               .with(FetchVector.Dimension.INSTANCE_ID, application.serializedForm())
                                                               .with(FetchVector.Dimension.VESPA_VERSION, version.toFullString())
                                                               .value(),
                                           PermanentFlags.SHARED_HOST.bindTo(flagSource).value());
    }
}
