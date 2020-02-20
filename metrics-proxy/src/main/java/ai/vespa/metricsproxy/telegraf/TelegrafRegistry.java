// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import com.yahoo.log.LogLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author olaa
 */
public class TelegrafRegistry {

    private static final List<Telegraf> telegrafInstances = Collections.synchronizedList(new ArrayList<>());

    private static final Logger logger = Logger.getLogger(TelegrafRegistry.class.getName());

    public void addInstance(Telegraf telegraf) {
        logger.log(LogLevel.DEBUG, () -> "Adding Telegraf instance to registry: " + telegraf);
        telegrafInstances.add(telegraf);
    }

    public void removeInstance(Telegraf telegraf) {
        logger.log(LogLevel.DEBUG, () -> "Removing Telegraf instance from registry");
        telegrafInstances.remove(telegraf);
    }

    public boolean isEmpty() {
        return telegrafInstances.isEmpty();
    }
}
