// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 * Gathers metrics regarding currently processing coredumps and host life
 */
public class MetricGatherer {

    static List<JSONObject> getAdditionalMetrics() {
        List<JSONObject> packetList = new ArrayList<>();
        packetList.add(CoredumpGatherer.gatherCoredumpMetrics());
        if (System.getProperty("os.name").contains("nux"))
                packetList.add(HostLifeGatherer.getHostLifePacket());
        return packetList;
    }
}
