// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 */
public class ContainerThreadpoolComponent extends SimpleComponent implements ContainerThreadpoolConfig.Producer {

    private final String name;

    public ContainerThreadpoolComponent(String name) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "threadpool@" + name,
                        ContainerThreadPool.class.getName(),
                        null)));
        this.name = name;
    }

    @Override public void getConfig(ContainerThreadpoolConfig.Builder builder) { builder.name(this.name); }

    protected static double vcpu(ContainerCluster<?> cluster) {
        List<Double> vcpus = cluster.getContainers().stream()
                .filter(c -> c.getHostResource() != null && c.getHostResource().realResources() != null)
                .map(c -> c.getHostResource().realResources().vcpu())
                .distinct()
                .collect(Collectors.toList());
        // We can only use host resource for calculation if all container nodes in the cluster are homogeneous (in terms of vcpu)
        if (vcpus.size() != 1 || vcpus.get(0) == 0) return 0;
        return vcpus.get(0);
    }
}
