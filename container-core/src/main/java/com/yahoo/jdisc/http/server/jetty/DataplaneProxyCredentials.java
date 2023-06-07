// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.component.AbstractComponent;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Generates temporary credentials to be used by a proxy for accessing Jdisc.
 * Credentials are written to vespa_home/tmp/.
 *
 * @author mortent
 */
public class DataplaneProxyCredentials extends AbstractComponent {

    private final Path certificateFile;
    private final Path keyFile;

    public DataplaneProxyCredentials() {
        certificateFile = Paths.get(Defaults.getDefaults().underVespaHome("tmp/proxy_cert.pem"));
        keyFile = Paths.get(Defaults.getDefaults().underVespaHome("tmp/proxy_key.pem"));
        if (regenerateCredentials(certificateFile, keyFile)) {
            X509CertificateWithKey selfSigned = X509CertificateUtils.createSelfSigned("cn=vespa dataplane proxy", Duration.ofDays(30));
            Exceptions.uncheck(() -> Files.writeString(certificateFile, X509CertificateUtils.toPem(selfSigned.certificate())));
            Exceptions.uncheck(() -> Files.writeString(keyFile, KeyUtils.toPem(selfSigned.privateKey())));
        }
    }

    /*
     * Returns true if credentials should be regenerated.
     */
    private boolean regenerateCredentials(Path certificateFile, Path keyFile) {
        if (!Files.exists(certificateFile) || !Files.exists(keyFile)) {
            return true;
        }
        try {
            X509Certificate x509Certificate = X509CertificateUtils.fromPem(Files.readString(certificateFile));
            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(Files.readString(keyFile));
            return !X509CertificateUtils.privateKeyMatchesPublicKey(privateKey, x509Certificate.getPublicKey());
        } catch (IOException e) {
            // Some exception occured, assume credentials corrupted and requires a new pair.
            return true;
        }
    }

    public Path certificateFile() {
        return certificateFile;
    }

    public Path keyFile() {
        return keyFile;
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }

}
