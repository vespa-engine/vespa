// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tls;

import com.yahoo.component.annotation.Inject;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.hosted.controller.tls.config.TlsConfig;

/**
 * A secret store mock that's pre-populated with a certificate and key.
 *
 * @author mpolden
 */
@SuppressWarnings("unused") // Injected
public class SecretStoreMock extends com.yahoo.vespa.hosted.controller.integration.SecretStoreMock {

    @Inject
    public SecretStoreMock(TlsConfig config) {
        addKeyPair(config);
    }

    private void addKeyPair(TlsConfig config) {
        setSecret(config.privateKeySecret(), KeyUtils.toPem(Keys.keyPair.getPrivate()));
        setSecret(config.certificateSecret(), X509CertificateUtils.toPem(Keys.certificate));
    }

}
