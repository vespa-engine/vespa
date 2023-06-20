// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.vespa.defaults.Defaults;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Generates temporary credentials to be used by a proxy for accessing Jdisc.
 * Credentials are written to vespa_home/tmp/.
 *
 * @author mortent
 */
public class DataplaneProxyCredentials extends AbstractComponent {

    private static final Logger log = Logger.getLogger(DataplaneProxyCredentials.class.getName());

    private final Path certificateFile;
    private final Path keyFile;
    private final X509Certificate certificate;

    @Inject
    public DataplaneProxyCredentials() {
        this(
                Paths.get(Defaults.getDefaults().underVespaHome("tmp/proxy_cert.pem")),
                Paths.get(Defaults.getDefaults().underVespaHome("tmp/proxy_key.pem"))
        );
    }

    public DataplaneProxyCredentials(Path certificateFile, Path keyFile){
        this.certificateFile = certificateFile;
        this.keyFile = keyFile;

        var existing = regenerateCredentials(certificateFile, keyFile).orElse(null);
        if (existing == null) {
            X509CertificateWithKey selfSigned = X509CertificateUtils.createSelfSigned("cn=vespa dataplane proxy", Duration.ofDays(30));
            uncheck(() -> Files.writeString(certificateFile, X509CertificateUtils.toPem(selfSigned.certificate())));
            uncheck(() -> Files.writeString(keyFile, KeyUtils.toPem(selfSigned.privateKey())));
            this.certificate = selfSigned.certificate();
        } else {
            this.certificate = existing;
        }

    }

    /**
     * @return old certificate if credentials should not be regenerated, empty otherwise.
     */
    private Optional<X509Certificate> regenerateCredentials(Path certificateFile, Path keyFile) {
        if (!Files.exists(certificateFile) || !Files.exists(keyFile)) {
            return Optional.empty();
        }
        try {
            X509Certificate x509Certificate = X509CertificateUtils.fromPem(Files.readString(certificateFile));
            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(Files.readString(keyFile));
            if (!X509CertificateUtils.privateKeyMatchesPublicKey(privateKey, x509Certificate.getPublicKey())) return Optional.empty();
            return Optional.of(x509Certificate);
        } catch (IOException e) {
            // Some exception occurred, assume credentials corrupted and requires a new pair.
            log.log(Level.WARNING, "Failed to load credentials: %s".formatted(e.getMessage()));
            log.log(Level.FINE, e.toString(), e);
            return Optional.empty();
        }
    }

    public Path certificateFile() {
        return certificateFile;
    }

    public Path keyFile() {
        return keyFile;
    }

    public X509Certificate certificate() { return certificate; }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }

}
