// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: thomasg
 * Date: 5/7/12
 * Time: 2:00 PM
 * To change this template use File | Settings | File Templates.
 */
public class PriorityMapping {
    ModelElement clusterXml;
    Map<DocumentProtocol.Priority, Integer> priorityMappings = new HashMap<>();

    public PriorityMapping(ModelElement clusterXml) {
        this.clusterXml = clusterXml;

        int val = 50;
        for (DocumentProtocol.Priority p : DocumentProtocol.Priority.values()) {
            priorityMappings.put(p, val);
            val += 10;
        }
        priorityMappings.put(DocumentProtocol.Priority.HIGHEST, 0);
        priorityMappings.put(DocumentProtocol.Priority.LOWEST, 255);
    }

    public int getPriorityMapping(String priorityName) {
        return priorityMappings.get(Enum.valueOf(DocumentProtocol.Priority.class, priorityName));
    }

}
