// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;


/**
 * @author bjorncs
 */
public interface AthenzIdentity {
    AthenzDomain getDomain();
    String getName();
    default String getFullName() {
        return getDomain().getName() + "." + getName();
    }
    default String getDomainName() { return getDomain().getName(); }
}
