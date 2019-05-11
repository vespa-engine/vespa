// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.content.TuningDispatch;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * @author Simon Thoresen Hult
 */
public class DomTuningDispatchBuilder {

    public static TuningDispatch build(ModelElement contentXml) {
        TuningDispatch.Builder builder = new TuningDispatch.Builder();
        ModelElement tuningElement = contentXml.child("tuning");
        if (tuningElement == null) {
            return builder.build();
        }
        ModelElement dispatchElement = tuningElement.child("dispatch");
        if (dispatchElement == null) {
            return builder.build();
        }
        builder.setMaxHitsPerPartition(dispatchElement.childAsInteger("max-hits-per-partition"));
        builder.setDispatchPolicy(dispatchElement.childAsString("dispatch-policy"));
        builder.setUseLocalNode(dispatchElement.childAsBoolean("use-local-node"));
        builder.setMinGroupCoverage(dispatchElement.childAsDouble("min-group-coverage"));
        builder.setMinActiveDocsCoverage(dispatchElement.childAsDouble("min-active-docs-coverage"));

        return builder.build();
    }

}
