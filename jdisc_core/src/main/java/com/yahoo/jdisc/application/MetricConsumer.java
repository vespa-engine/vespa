// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.yahoo.jdisc.Metric;

import java.util.Map;

/**
 * <p>This interface defines the consumer counterpart of the {@link Metric} interface. All Metric objects contain their
 * own thread local instance of this interface, so most implementations will require a registry of sorts to manage the
 * aggregation of state across MetricConsumers.</p>
 *
 * <p>An {@link Application} needs to bind a {@link Provider} of this interface to an implementation, or else all calls
 * to the Metric objects become no-ops. An implementation will look similar to:</p>
 *
 * <pre>
 * private final MyMetricRegistry myMetricRegistry = new MyMetricRegistry();
 * void createContainer() {
 *     ContainerBuilder builder = containerActivator.newContainerBuilder();
 *     builder.guice().install(new MyGuiceModule());
 *     (...)
 * }
 * class MyGuiceModule extends com.google.inject.AbstractModule {
 *     void configure() {
 *         bind(MetricConsumer.class).toProvider(myMetricRegistry);
 *         (...)
 *     }
 * }
 * class MyMetricRegistry implements com.google.inject.Provider&lt;MetricConsumer&gt; {
 *     (...)
 * }
 * </pre>
 *
 * @author Simon Thoresen Hult
 */
@ProvidedBy(MetricNullProvider.class)
public interface MetricConsumer {

    /**
     * Consume a call to <code>Metric.set(String, Number, Metric.Context)</code>.
     *
     * @param key the name of the metric to modify
     * @param val the value to assign to the named metric
     * @param ctx the context to further describe this entry
     */
    void set(String key, Number val, Metric.Context ctx);

    /**
     * Consume a call to <code>Metric.add(String, Number, Metric.Context)</code>.
     *
     * @param key the name of the metric to modify
     * @param val the value to add to the named metric
     * @param ctx the context to further describe this entry
     */
    void add(String key, Number val, Metric.Context ctx);

    /**
     * Creates a <code>Metric.Context</code> object that encapsulates the given properties. The returned Context object
     * will be passed along every future call to <code>set(String, Number, Metric.Context)</code> and
     * <code>add(String, Number, Metric.Context)</code> where the properties match those given here.
     *
     * @param properties the properties to incorporate in the context
     * @return the created context
     */
    Metric.Context createContext(Map<String, ?> properties);

}
