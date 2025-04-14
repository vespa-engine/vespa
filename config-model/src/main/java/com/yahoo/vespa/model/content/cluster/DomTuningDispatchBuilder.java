// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.content.DispatchTuning;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import static java.util.logging.Level.WARNING;

/**
 * @author Simon Thoresen Hult
 */
public class DomTuningDispatchBuilder {

    public static DispatchTuning build(ModelElement contentXml, DeployLogger logger) {
        DispatchTuning.Builder builder = new DispatchTuning.Builder();
        ModelElement tuningElement = contentXml.child("tuning");
        if (tuningElement == null) {
            return builder.build();
        }
        ModelElement dispatchElement = tuningElement.child("dispatch");
        if (dispatchElement == null) {
            return builder.build();
        }
        builder.setMaxHitsPerPartition(dispatchElement.childAsInteger("max-hits-per-partition"));

        var policy = dispatchElement.childAsString("dispatch-policy");
        // TODO: Remove support for 'random' on Vespa 9 (already removed from doc)
        if (policy != null && policy.equalsIgnoreCase("random")) {
            logger.logApplicationPackage(WARNING, "'dispatch-policy' is set to 'random', this is deprecated and 'adaptive' will be used instead");
        }
        builder.setDispatchPolicy(dispatchElement.childAsString("dispatch-policy"));

        builder.setPrioritizeAvailability(dispatchElement.childAsBoolean("prioritize-availability"));
        builder.setMinActiveDocsCoverage(dispatchElement.childAsDouble("min-active-docs-coverage"));
        builder.setTopKProbability(dispatchElement.childAsDouble("top-k-probability"));

        return builder.build();
    }

}
