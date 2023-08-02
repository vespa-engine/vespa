// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.engines.DummyPersistence;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonEngine;

/**
 * Creates the correct engine factory from XML.
 */
public class EngineFactoryBuilder {
    public PersistenceEngine.PersistenceFactory build(ModelElement clusterElem, ContentCluster c) {
        ModelElement persistence = clusterElem.child("engine");
        if (persistence != null) {
            if (c.getSearch().hasIndexedCluster() && persistence.child("proton") == null) {
                throw new IllegalArgumentException("Persistence engine does not allow for indexed search. Please use <proton> as your engine.");
            }

            if (persistence.child("proton") != null) {
                return new ProtonEngine.Factory(c.getSearch());
            } else if (persistence.child("dummy") != null) {
                return new DummyPersistence.Factory();
            }
        }

        return new ProtonEngine.Factory(c.getSearch());
    }
}
