// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container;

import com.yahoo.container.config.StatisticsEmitter;

/**
 * Distribution point for QRS specific stuff in a more or less
 * container agnostic way. This is only a stepping stone to moving these things
 * to Container and other pertinent classes, or simply removing the problems.
 *
 * <p>
 * This class should not reach a final release.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class ConfigHack {

    private volatile StatisticsEmitter statisticsEmitter = new StatisticsEmitter();
    public static final String TILED_TEMPLATE = "tiled";

    public static final ConfigHack instance = new ConfigHack();

    public StatisticsEmitter getStatisticsHandler() {
        return statisticsEmitter;
    }

    public void setStatisticsEmitter(StatisticsEmitter statisticsEmitter) {
        this.statisticsEmitter = statisticsEmitter;
    }

}
