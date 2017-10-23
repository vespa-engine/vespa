// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

/**
 * @author bjorncs
 */
public interface KeyProvider {
    String getPrivateKey(int version);

    String getPublicKey(int version);
}
