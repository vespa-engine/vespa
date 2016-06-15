// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import java.util.Map;
import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;

/**
 * <p>This interface is necessary so that we can retrieve MBeans in real-time through an MBean proxy and add
 * new data sources to those existing MBeans. It is implemented by {@link ComponentMetricMBean}</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public interface ConsumerContextMetricReader {

    public boolean addDataSource(ConsumerContextMetric contextMetric);

    public int dataSourceCount();

    public Map<String, MetricUnit> snapshot();
}
