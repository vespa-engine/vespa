// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author bratseth
 * @deprecated use AllocatedHosts
 */
// TODO: Remove when no version older than 6.143 is in production anywhere
@Deprecated
@SuppressWarnings("unused")
public class ProvisionInfo extends AllocatedHosts {

    private static final String mappingKey = "mapping";
    private static final String hostSpecKey = "hostSpec";

    private ProvisionInfo(Set<HostSpec> hosts) {
        super(hosts);
    }

    public static ProvisionInfo withHosts(Set<HostSpec> hosts) {
        return new ProvisionInfo(hosts);
    }

    public static ProvisionInfo fromJson(byte[] json, Optional<NodeFlavors> nodeFlavors) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get(), nodeFlavors);
    }

    private static ProvisionInfo fromSlime(Inspector inspector, Optional<NodeFlavors> nodeFlavors) {
        Inspector array = inspector.field(mappingKey);
        Set<HostSpec> hosts = new LinkedHashSet<>();
        array.traverse(new ArrayTraverser() {
            @Override
            public void entry(int i, Inspector inspector) {
                hosts.add(hostFromSlime(inspector.field(hostSpecKey), nodeFlavors));
            }
        });
        return new ProvisionInfo(hosts);
    }

}
