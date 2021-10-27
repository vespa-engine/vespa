// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import java.util.logging.Level;

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
        logger.log(Level.FINE, () -> "Adding Telegraf instance to registry: " + telegraf.hashCode());
        telegrafInstances.add(telegraf);
    }

    public void removeInstance(Telegraf telegraf) {
        logger.log(Level.FINE, () -> "Removing Telegraf instance from registry: " + telegraf.hashCode());
        telegrafInstances.remove(telegraf);
    }

    public boolean isEmpty() {
        return telegrafInstances.isEmpty();
    }
}
