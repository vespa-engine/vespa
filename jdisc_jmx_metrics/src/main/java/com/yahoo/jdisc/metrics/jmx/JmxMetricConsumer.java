// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.google.inject.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * <p>This class implements a {@link MetricConsumer} interface that exposes the metrics through a
 * {@link javax.management.DynamicMBean}, in our case a {@link ComponentMetricMBean}.
 * It is <i>not</i> thread-safe. Instances of this class are created through {@link Provider}</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class JmxMetricConsumer implements MetricConsumer {

    private final static Logger logger = Logger.getLogger(JmxMetricConsumer.class.getName());

    // Each entry corresponds to one MBean with a unique ObjectName
    private final Map<Metric.Context, ConsumerContextMetric> componentMetrics =
            new HashMap<Metric.Context, ConsumerContextMetric>();

    // If there is no context, default to the null context component metric
    private final ConsumerContextMetric nullContextMetricInstance;
    private final JmxMetricConfig config;
    private final Timer timer;

    public JmxMetricConsumer(JmxMetricConfig config, Timer timer) {
        this.config = config;
        this.timer = timer;
        nullContextMetricInstance = new ConsumerContextMetric(config.gaugeQueueDepth());

        // Attach the default component metric instance to an MBean
        createAndRegisterMBean(createContext(null), nullContextMetricInstance, config, timer);
    }

    @Override
    public void add(String key, Number val, Metric.Context ctx) {
        getComponentMetric(ctx).incrementMetric(key, val);
    }

    @Override
    public void set(String key, Number val, Metric.Context ctx) {
        getComponentMetric(ctx).setMetric(key, val);
    }

    @Override
    public Metric.Context createContext(Map<String, ?> properties) {
        return new JmxMetricContext(config, properties != null ? properties : Collections.<String, Object>emptyMap());
    }

    // package private for testing purposes
    static synchronized void createAndRegisterMBean(Metric.Context context,
                                                           ConsumerContextMetric dataSource,
                                                           JmxMetricConfig metricConfig, Timer timer) {
        context.getClass(); // throws NullPointerException
        if (! (context instanceof JmxMetricContext)) {
            throw new IllegalArgumentException("Expected " + JmxMetricContext.class.getName() + ", got " + context.getClass().getName() + ".");
        }
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = ((JmxMetricContext)context).getObjectName();
        if (! mbs.isRegistered(objectName)) {
            logger.info("Registering new MBean with name: "+objectName + ".");
            try {
                mbs.registerMBean(new ComponentMetricMBean(metricConfig, dataSource, timer), objectName);
            } catch (JMException e) {
                throw new RuntimeException("Exception thrown by MBeanServer.registerMBean().", e);
            }
        } else {
            // If the MBean with this ObjectName already exists, register the new data source with it
            JMX.newMBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                              objectName,
                              ConsumerContextMetricReader.class).addDataSource(dataSource);
        }
    }

    private ConsumerContextMetric getComponentMetric(Metric.Context context) {
        if (context == null) {
            return nullContextMetricInstance;
        }
        ConsumerContextMetric componentMetric = componentMetrics.get(context);
        if (componentMetric == null) {
            componentMetric = new ConsumerContextMetric(config.gaugeQueueDepth());
            createAndRegisterMBean(context, componentMetric, config, timer);
            componentMetrics.put(context, componentMetric);
        }
        return componentMetric;
    }

    /**
     * <p>This class is a {@link com.google.inject.Provider} of {@link JmxMetricConsumer} objects. There should be
     * only 1 instance of {@link JmxMetricConsumer} per thread since it is not thread-safe</p>
     */
    public static class Provider implements com.google.inject.Provider<MetricConsumer> {

        private final JmxMetricConfig metricConfig;
        private final Timer timer;

        @Inject
        public Provider(JmxMetricConfig metricConfig, Timer timer) {
            // Config is immutable
            this.metricConfig = metricConfig;
            this.timer = timer;
        }

        @Override
        public MetricConsumer get() {
            return new JmxMetricConsumer(metricConfig, timer);
        }
    }
}
