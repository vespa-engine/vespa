// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadpoolImpl;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import org.w3c.dom.Element;

import java.util.logging.Level;

/**
 * Component definition for a {@link java.util.concurrent.Executor} using {@link ContainerThreadPool}.
 *
 * @author bjorncs
 */
public abstract class ContainerThreadpool extends SimpleComponent implements ContainerThreadpoolConfig.Producer {

    private final String name;
    private final UserOptions options;

    record UserOptions(Double max, Double min, Double queue){}

    protected ContainerThreadpool(DeployState ds, String name, Element parent) {
        super(new ComponentModel(
                BundleInstantiationSpecification.fromStrings(
                        "threadpool@" + name,
                        ContainerThreadpoolImpl.class.getName(),
                        null)));
        this.name = name;
        var threadpoolElem = XmlHelper.getOptionalChild(parent, "threadpool").orElse(null);
        if (threadpoolElem == null) options = new UserOptions(null, null, null);
        else {
            // TODO Vespa 9 Remove min-threads, max-threads and queue-size
            Double max = null;
            Double min = null;
            Double queue = null;
            var minElem = XmlHelper.getOptionalChild(threadpoolElem, "min-threads").orElse(null);
            if (minElem != null) ds.getDeployLogger().logApplicationPackage(Level.WARNING, "For <threadpool>: <min-threads> is deprecated, use <threads> instead");
            var maxElem = XmlHelper.getOptionalChild(threadpoolElem, "max-threads").orElse(null);
            if (maxElem != null) ds.getDeployLogger().logApplicationPackage(Level.WARNING, "For <threadpool>: <max-threads> is deprecated, use <threads> with 'boost' instead");
            var queueElem = XmlHelper.getOptionalChild(threadpoolElem, "queue").orElse(null);
            var queueSizeElem = XmlHelper.getOptionalChild(threadpoolElem, "queue-size").orElse(null);
            if (queueSizeElem != null) ds.getDeployLogger().logApplicationPackage(Level.WARNING, "For <threadpool>: <queue-size> is deprecated, use <queue> instead");
            var threadsElem = XmlHelper.getOptionalChild(threadpoolElem, "threads").orElse(null);
            if (threadsElem != null) {
                min = parseMultiplier(threadsElem.getTextContent());
                max = threadsElem.hasAttribute("boost") ? parseMultiplier(threadsElem.getAttribute("boost")) : min;
            } else if (minElem != null) {
                min = parseFixed(minElem.getTextContent());
            }
            if (max == null && maxElem != null) {
                max = parseFixed(maxElem.getTextContent());
            }
            if (queueElem != null) queue = parseMultiplier(queueElem.getTextContent());
            else if (queueSizeElem != null) queue = parseFixed(queueSizeElem.getTextContent());
            options = new UserOptions(max, min, queue);
        }
    }

    private static Double parseMultiplier(String text) { return -parseFixed(text); }
    private static Double parseFixed(String text) { return Double.parseDouble(text); }

    // Must be implemented by subclasses to set values that may be overridden by user options.
    protected abstract void setDefaultConfigValues(ContainerThreadpoolConfig.Builder builder);

    @Override
    public void getConfig(ContainerThreadpoolConfig.Builder builder) {
        setDefaultConfigValues(builder);

        builder.name(this.name);
        if (options.max() != null) {
            int max = (int) Math.round(options.max());
            if (options.max() != 0 && max == 0) max = options.max() > 0 ? 1 : -1;
            builder.maxThreads(max);
        }
        if (options.min() != null) {
            int min = (int) Math.round(options.min());
            if (options.min() != 0 && min == 0) min = options.min() > 0 ? 1 : -1;
            builder.minThreads(min);
        }
        if (options.queue() != null) {
            int queue = (int) Math.round(options.queue());
            if (options.queue() != 0 && queue == 0) queue = options.queue() > 0 ? 1 : -1;
            builder.queueSize(queue);
        }
    }
}
