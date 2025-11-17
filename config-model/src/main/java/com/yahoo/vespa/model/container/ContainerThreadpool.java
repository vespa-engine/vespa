// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolImpl;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.xml.ContainerThreadpoolOptionsBuilder;
import org.w3c.dom.Element;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 * @author johsol
 */
public abstract class ContainerThreadpool extends SimpleComponent implements ContainerThreadpoolConfig.Producer {

    private final String name;
    private final UserOptions options;

    public record UserOptions(Double max, Double min, Double queueSize, boolean isRelative){}

    protected ContainerThreadpool(DeployState ds, String name, Element parent) {
        super(new ComponentModel(
                BundleInstantiationSpecification.fromStrings(
                        "threadpool@" + name,
                        ContainerThreadpoolImpl.class.getName(),
                        null)));
        this.name = name;
        this.options = ContainerThreadpoolOptionsBuilder.build(ds, parent);
    }

    // Must be implemented by subclasses to set values that may be overridden by user options.
    protected abstract void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder);

    @Override
    public void getConfig(ContainerThreadpoolConfig.Builder builder) {
        setDefaultConfigValues(builder);

        builder.name(this.name);

        // If absolute value is set in config model, clear the relative value. And vice versa.
        // They could be set as default values in setDefaultConfigValues.
        if (options.max() != null) {
            if (options.isRelative()) {
                builder.relativeMaxThreads(options.max());
                builder.maxThreads(-1);
            } else {
                builder.maxThreads(options.max().intValue());
                builder.relativeMaxThreads(-1);
            }
        }
        if (options.min() != null) {
            if (options.isRelative()) {
                builder.relativeMinThreads(options.min());
                builder.minThreads(-1);
            } else {
                builder.minThreads(options.min().intValue());
                builder.relativeMinThreads(-1);
            }
        }
        if (options.queueSize() != null) {
            if (options.isRelative()) {
                builder.relativeQueueSize(options.queueSize());
                builder.queueSize(-1);
            } else {
                builder.queueSize(options.queueSize().intValue());
                builder.relativeQueueSize(-1);
            }
        }
    }
}
