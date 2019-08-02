// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.Redundancy;

/**
 * Builds redundancy config for a content cluster.
 */
public class RedundancyBuilder {

    Redundancy build(ModelElement clusterXml) {
        Integer initialRedundancy = 2;
        Integer finalRedundancy = 3;
        Integer readyCopies = 2;

        ModelElement redundancyElement = clusterXml.child("redundancy");
        if (redundancyElement != null) {
            initialRedundancy = redundancyElement.integerAttribute("reply-after");
            finalRedundancy = (int)redundancyElement.asLong();

            if (initialRedundancy == null) {
                initialRedundancy = finalRedundancy;
            } else {
                if (finalRedundancy < initialRedundancy) {
                    throw new IllegalArgumentException("Final redundancy must be higher than or equal to initial redundancy");
                }
            }

            readyCopies = clusterXml.childAsInteger("engine.proton.searchable-copies");
            if (readyCopies == null) {
                readyCopies = Math.min(finalRedundancy, 2);
            }
            if (readyCopies > finalRedundancy) {
                throw new IllegalArgumentException("Number of searchable copies can not be higher than final redundancy");
            }
        }

        return new Redundancy(initialRedundancy, finalRedundancy, readyCopies);
    }

}
