// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.application.MetricProvider;

import java.util.Map;

/**
 * <p>This interface provides an API for writing metric data to the configured {@link MetricConsumer}. If no {@link
 * Provider} for the MetricConsumer class has been bound by the application, all calls to this interface are no-ops. The
 * implementation of this interface uses thread local consumer instances, so as long as the {@link MetricConsumer} is
 * thread-safe, so is this.</p>
 *
 * <p>An instance of this class can be injected anywhere.</p>
 *
 * @author Simon Thoresen Hult
 */
@ProvidedBy(MetricProvider.class)
public interface Metric {

    /**
     * Set a metric value. This is typically used with histogram-type metrics.
     *
     * @param key The name of the metric to modify.
     * @param val The value to assign to the named metric.
     * @param ctx The context to further describe this entry.
     */
    void set(String key, Number val, Context ctx);

    /**
     * Add to a metric value. This is typically used with counter-type metrics.
     *
     * @param key the name of the metric to modify
     * @param val the value to add to the named metric
     * @param ctx the context to further describe this entry
     */
    void add(String key, Number val, Context ctx);

    /**
     * Creates a {@link MetricConsumer}-specific {@link Context} object that encapsulates the given properties. The
     * returned Context object should be passed along every future call to {@link #set(String, Number, Context)} and
     * {@link #add(String, Number, Context)} where the properties match those given here.
     *
     * @param properties the properties to incorporate in the context
     * @return the created context
     */
    Context createContext(Map<String, ?> properties);

    /**
     * Declares the interface for the arbitrary context object to pass to both the {@link
     * #set(String, Number, Context)} and {@link #add(String, Number, Context)} methods. This is intentionally empty so
     * that implementations can vary.
     */
    interface Context {

    }

}
