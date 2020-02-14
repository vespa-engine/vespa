// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.telegraf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author olaa
 */
public class TelegrafRegistry {

    private static final List<Telegraf> telegrafInstances = Collections.synchronizedList(new ArrayList<>());

    public void addInstance(Telegraf telegraf) {
        telegrafInstances.add(telegraf);
    }

    public void removeInstance(Telegraf telegraf) {
        telegrafInstances.remove(telegraf);
    }

    public boolean isEmpty() {
        return telegrafInstances.isEmpty();
    }
}
