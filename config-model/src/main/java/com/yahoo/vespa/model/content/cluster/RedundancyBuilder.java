// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.Redundancy;

/**
 * Builds redundancy config for a content cluster.
 */
public class RedundancyBuilder {
    Redundancy build(ModelElement clusterXml) {
        Integer redundancy = 3;
        Integer readyCopies = 2;

        ModelElement redundancyElement = clusterXml.getChild("redundancy");
        if (redundancyElement != null) {
            redundancy = (int)redundancyElement.asLong();

            readyCopies = clusterXml.childAsInteger("engine.proton.searchable-copies");
            if (readyCopies == null) {
                readyCopies = Math.min(redundancy, 2);
            }
            if (readyCopies > redundancy) {
                throw new IllegalArgumentException("Number of searchable copies can not be higher than final redundancy");
            }
        }

        return new Redundancy(redundancy, readyCopies);
    }
}
