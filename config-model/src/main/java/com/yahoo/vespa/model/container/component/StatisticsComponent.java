// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.container.StatisticsConfig;

/**
 * @author Tony Vaagenes
 */
public class StatisticsComponent extends SimpleComponent implements StatisticsConfig.Producer {

    public StatisticsComponent() {
        super("com.yahoo.statistics.StatisticsImpl");
    }
    
    @Override
    public void getConfig(StatisticsConfig.Builder builder) {
        Monitoring monitoring = getMonitoringService();
        if (monitoring != null) {
            builder.
                collectionintervalsec(monitoring.getIntervalSeconds().doubleValue()).
                loggingintervalsec(monitoring.getIntervalSeconds().doubleValue());
        }
        builder.values(new StatisticsConfig.Values.Builder().
                name("query_latency").
                operations(new StatisticsConfig.Values.Operations.Builder().
                        name(StatisticsConfig.Values.Operations.Name.REGULAR).
                            arguments(new StatisticsConfig.Values.Operations.Arguments.Builder().
                                    key("limits").
                                    value("25,50,100,500"))));
                    
    }
}
