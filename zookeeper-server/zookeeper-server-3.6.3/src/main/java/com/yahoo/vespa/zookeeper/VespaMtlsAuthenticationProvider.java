// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.common.X509Exception;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.server.ServerCnxn;
import org.apache.zookeeper.server.auth.AuthenticationProvider;
import org.apache.zookeeper.server.auth.X509AuthenticationProvider;

import java.security.cert.X509Certificate;
import java.util.logging.Logger;

/**
 * A {@link AuthenticationProvider} to be used in combination with Vespa mTLS
 *
 * @author bjorncs
 */
public class VespaMtlsAuthenticationProvider extends X509AuthenticationProvider {

    private static final Logger log = Logger.getLogger(VespaMtlsAuthenticationProvider.class.getName());

    public VespaMtlsAuthenticationProvider() throws X509Exception { super(null, null);}

    @Override
    public KeeperException.Code handleAuthentication(ServerCnxn cnxn, byte[] authData) {
        // Vespa's mTLS peer authorization rules are performed by the underlying trust manager implementation.
        // The client is authorized once the SSL handshake has completed.
        X509Certificate[] certificateChain = (X509Certificate[]) cnxn.getClientCertificateChain();
        if (certificateChain == null || certificateChain.length == 0) {
            log.warning("Client not authenticated - should not be possible with clientAuth=NEED");
            return KeeperException.Code.AUTHFAILED;
        }
        X509Certificate certificate = certificateChain[0];
        cnxn.addAuthInfo(new Id(getScheme(), certificate.getSubjectX500Principal().getName()));
        return KeeperException.Code.OK;
    }

    @Override public String getScheme() { return "x509"; }

}
