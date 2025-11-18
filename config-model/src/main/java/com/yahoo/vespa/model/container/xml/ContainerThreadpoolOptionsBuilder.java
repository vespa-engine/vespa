// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import org.w3c.dom.Element;

import java.util.logging.Level;

/**
 * Parses <code>threadpool</code> XML-elements into {@link ContainerThreadpool.UserOptions}.
 * <p>
 * If <code>threads</code> or <code>queue</code> elements are present as children of the
 * <code>threadpool</code> element, only the new syntax will be used. If a combination of the old
 * and new syntax is used, the old syntax will be ignored.
 *
 * @author johsol
 */
public class ContainerThreadpoolOptionsBuilder {
    public static ContainerThreadpool.UserOptions build(DeployState ds, Element parent) {
        var options = new ContainerThreadpool.UserOptions(null, null, null, false);
        var threadpoolElem = XmlHelper.getOptionalChild(parent, "threadpool").orElse(null);
        if (threadpoolElem == null)
            return options;

        // TODO Vespa 9 Remove min-threads, max-threads and queue-size

        // TODO Vespa 9 Remove variable pool size (aka 'boost')
        //      It's rarely used and the semantics are confusing.
        //      The number of threads are scaled up after the queue is full, which is surprising,
        //      as one would expect the pool size to scale before queuing up tasks.
        //      This is a Java limitation in the thread pool class from the standard library.
        Double max = null;
        Double min = null;
        Double queue = null;
        boolean isRelative;
        var threadsElem = XmlHelper.getOptionalChild(threadpoolElem, "threads").orElse(null);
        var queueElem = XmlHelper.getOptionalChild(threadpoolElem, "queue").orElse(null);
        if (threadsElem != null || queueElem != null) {
            // New syntax with values relative to number of CPU cores
            if (threadsElem != null) {
                min = Double.parseDouble(threadsElem.getTextContent());

                // Until variable pool size is removed, prefer max to boost.
                var maxAttribute = XmlHelper.getOptionalAttribute(threadsElem, "max").orElse(null);
                var boostAttribute = XmlHelper.getOptionalAttribute(threadsElem, "boost").orElse(null);
                if (maxAttribute != null && boostAttribute != null) {
                    throw new IllegalArgumentException("For <threads>: both 'max' and 'boost' cannot be specified at the same time. Please use 'max' only.");
                } else if (maxAttribute != null) {
                    max = Double.parseDouble(maxAttribute);
                } else if (boostAttribute != null) {
                    ds.getDeployLogger()
                            .logApplicationPackage(Level.WARNING, "For <threads>: the 'boost' attribute is deprecated, use 'max' instead.");
                    max = Double.parseDouble(boostAttribute);
                } else {
                    max = min;
                }
            }

            if (queueElem != null) queue = Double.parseDouble(queueElem.getTextContent());
            isRelative = true;
        } else {
            // Old syntax with absolute values
            var minElem = XmlHelper.getOptionalChild(threadpoolElem, "min-threads").orElse(null);
            if (minElem != null) ds.getDeployLogger()
                    .logApplicationPackage(Level.WARNING, "For <threadpool>: <min-threads> is deprecated, use <threads> instead");
            var maxElem = XmlHelper.getOptionalChild(threadpoolElem, "max-threads").orElse(null);
            if (maxElem != null) ds.getDeployLogger()
                    .logApplicationPackage(Level.WARNING, "For <threadpool>: <max-threads> is deprecated, use <threads> with 'max' instead");
            if (minElem != null) {
                min = Double.parseDouble(minElem.getTextContent());
            }
            if (maxElem != null) {
                max = Double.parseDouble(maxElem.getTextContent());
            }
            var queueSizeElem = XmlHelper.getOptionalChild(threadpoolElem, "queue-size").orElse(null);
            if (queueSizeElem != null) ds.getDeployLogger()
                    .logApplicationPackage(Level.WARNING, "For <threadpool>: <queue-size> is deprecated, use <queue> instead");
            if (queueSizeElem != null) queue = Double.parseDouble(queueSizeElem.getTextContent());
            isRelative = false;
        }

        if (min != null && min < 0)
            throw new IllegalArgumentException("For <threadpool>: <threads> must be positive.");
        if (max != null && max <= 0)
            throw new IllegalArgumentException("For <threadpool>: 'max' on <threads> must be positive.");
        if (queue != null && queue < 0)
            throw new IllegalArgumentException("For <threadpool>: <queue> must be positive.");
        if (min != null && max != null && min > max)
            throw new IllegalArgumentException("For <threadpool>: 'max' on <threads> must be greater than <threads>.");
        options = new ContainerThreadpool.UserOptions(max, min, queue, isRelative);
        return options;
    }
}