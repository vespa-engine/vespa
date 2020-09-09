// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

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
}
