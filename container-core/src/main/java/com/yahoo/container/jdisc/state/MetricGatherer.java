// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers metrics regarding currently processing coredumps and host life.
 *
 * @author olaa
 */
public class MetricGatherer {

    static List<JsonNode> getAdditionalMetrics() {
        FileWrapper fileWrapper = new FileWrapper();
        List<JsonNode> packetList = new ArrayList<>();
        if (System.getProperty("os.name").contains("nux"))
            packetList.add(HostLifeGatherer.getHostLifePacket(fileWrapper));
        return packetList;
    }

}
