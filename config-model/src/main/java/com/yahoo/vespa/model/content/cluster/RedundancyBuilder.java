// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.IndexedHierarchicDistributionValidator;
import com.yahoo.vespa.model.content.Redundancy;

/**
 * Builds redundancy config for a content cluster.
 */
public class RedundancyBuilder {

    private Integer initialRedundancy = 2;
    private Integer finalRedundancy = 3;
    private Integer readyCopies = 2;

    RedundancyBuilder(ModelElement clusterXml) {
        ModelElement redundancyElement = clusterXml.child("redundancy");
        if (redundancyElement != null) {
            initialRedundancy = redundancyElement.integerAttribute("reply-after");
            finalRedundancy = (int) redundancyElement.asLong();

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
    }
    public Redundancy build(String clusterName, boolean isHosted, int subGroups, int leafGroups,  int totalNodes) {
        if (isHosted) {
            return new Redundancy(initialRedundancy, finalRedundancy, readyCopies, leafGroups, totalNodes);
        } else {
            subGroups = Math.max(1, subGroups);
            IndexedHierarchicDistributionValidator.validateThatLeafGroupsCountIsAFactorOfRedundancy(clusterName, finalRedundancy, subGroups);
            IndexedHierarchicDistributionValidator.validateThatReadyCopiesIsCompatibleWithRedundancy(clusterName, finalRedundancy, readyCopies, subGroups);
            return new Redundancy(initialRedundancy/subGroups, finalRedundancy/subGroups, readyCopies/subGroups, subGroups, totalNodes);
        }
    }

}
