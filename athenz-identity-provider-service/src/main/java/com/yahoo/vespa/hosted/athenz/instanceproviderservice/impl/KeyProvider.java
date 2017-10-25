// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author bjorncs
 */
public interface KeyProvider {
    PrivateKey getPrivateKey(int version);

    PublicKey getPublicKey(int version);
}
