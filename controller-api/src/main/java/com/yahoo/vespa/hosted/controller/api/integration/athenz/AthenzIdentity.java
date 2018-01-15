// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;


import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;

/**
 * @author bjorncs
 */
public interface AthenzIdentity {
    AthenzDomain getDomain();
    String getName();
    default String getFullName() {
        return getDomain().id() + "." + getName();
    }
}
