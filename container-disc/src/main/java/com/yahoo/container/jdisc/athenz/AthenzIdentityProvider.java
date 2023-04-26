// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.athenz;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author mortent
 */
public interface AthenzIdentityProvider {

    String domain();
    String service();
    SSLContext getIdentitySslContext();
    SSLContext getRoleSslContext(String domain, String role);
    String getRoleToken(String domain);
    String getRoleToken(String domain, String role);
    String getAccessToken(String domain);
    String getAccessToken(String domain, List<String> roles);
    List<X509Certificate> getIdentityCertificate();
    X509Certificate getRoleCertificate(String domain, String role);
    PrivateKey getPrivateKey();
    Path trustStorePath();
    void deconstruct();
}
