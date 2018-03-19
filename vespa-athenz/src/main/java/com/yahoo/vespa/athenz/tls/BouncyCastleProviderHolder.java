// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * @author bjorncs
 */
class BouncyCastleProviderHolder {

    private static final BouncyCastleProvider bcProvider = new BouncyCastleProvider();

    static BouncyCastleProvider getInstance() { return bcProvider; }
}
