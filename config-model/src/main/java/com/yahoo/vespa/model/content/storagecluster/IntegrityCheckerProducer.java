// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.core.StorIntegritycheckerConfig;
import com.yahoo.config.model.ConfigModelUtils;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Serves stor-integritychecker config for storage clusters.
 */
public class IntegrityCheckerProducer implements StorIntegritycheckerConfig.Producer {

    public static class Builder {
        protected IntegrityCheckerProducer build(ContentCluster cluster, ModelElement clusterElem) {
            if (!cluster.isMemfilePersistence()) {
                return integrityCheckerDisabled();
            }

            ModelElement tuning = clusterElem.getChild("tuning");

            if (tuning == null) {
                return new IntegrityCheckerProducer();
            }

            ModelElement maintenance = tuning.getChild("maintenance");
            if (maintenance == null) {
                return new IntegrityCheckerProducer();
            }

            Integer startTime = null;
            Integer stopTime = null;
            String weeklyCycle = null;

            String start = maintenance.getStringAttribute("start");
            if (start != null) {
                startTime = ConfigModelUtils.getTimeOfDay(start);
            }

            String stop = maintenance.getStringAttribute("stop");
            if (stop != null) {
                stopTime = ConfigModelUtils.getTimeOfDay(stop);
            }

            String high = maintenance.getStringAttribute("high");

            if (high != null) {
                int weekday = ConfigModelUtils.getDayOfWeek(high);
                char[] weeklycycle = "rrrrrrr".toCharArray();
                weeklycycle[weekday] = 'R';
                weeklyCycle = String.valueOf(weeklycycle);
            }

            return new IntegrityCheckerProducer(startTime, stopTime, weeklyCycle);
        }
    }

    private Integer startTime;
    private Integer stopTime;
    private String weeklyCycle;

    IntegrityCheckerProducer() {
    }

    IntegrityCheckerProducer(Integer startTime, Integer stopTime, String weeklyCycle) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.weeklyCycle = weeklyCycle;
    }

    private static IntegrityCheckerProducer integrityCheckerDisabled() {
        // Leave start/start times at default, but mark each day of the week as
        // not allowing the integrity checker to be run.
        return new IntegrityCheckerProducer(null, null, "-------");
    }

    @Override
    public void getConfig(StorIntegritycheckerConfig.Builder builder) {
        if (startTime != null) {
            builder.dailycyclestart(startTime);
        }

        if (stopTime != null) {
            builder.dailycyclestop(stopTime);
        }

        if (weeklyCycle != null) {
            builder.weeklycycle(weeklyCycle);
        }
    }
}
