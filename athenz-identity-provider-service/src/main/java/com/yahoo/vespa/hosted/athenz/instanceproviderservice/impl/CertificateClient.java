// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import java.security.PrivateKey;
import java.time.temporal.TemporalAmount;

/**
 * @author bjorncs
 */
@FunctionalInterface
public interface CertificateClient {
    String updateCertificate(PrivateKey privateKey, TemporalAmount expiryTime);
}
