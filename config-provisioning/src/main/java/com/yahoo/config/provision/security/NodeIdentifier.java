// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.security;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Identifies Vespa nodes from the their X509 certificate.
 *
 * @author bjorncs
 */
public interface NodeIdentifier {

    NodeIdentity identifyNode(List<X509Certificate> peerCertificateChain) throws NodeIdentifierException;

}
