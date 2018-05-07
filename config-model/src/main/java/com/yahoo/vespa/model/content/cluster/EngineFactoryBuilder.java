// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.engines.*;

/**
 * Creates the correct engine factory from XML.
 */
public class EngineFactoryBuilder {
    public PersistenceEngine.PersistenceFactory build(ModelElement clusterElem, ContentCluster c) {
        ModelElement persistence = clusterElem.getChild("engine");
        if (persistence != null) {
            if (c.getSearch().hasIndexedCluster() && persistence.getChild("proton") == null) {
                throw new IllegalArgumentException("Persistence engine does not allow for indexed search. Please use <proton> as your engine.");
            }

            if (persistence.getChild("proton") != null) {
                return new ProtonEngine.Factory(c.getSearch());
            } else if (persistence.getChild("dummy") != null) {
                return new com.yahoo.vespa.model.content.engines.DummyPersistence.Factory();
            }
        }

        return new ProtonEngine.Factory(c.getSearch());
    }
}
