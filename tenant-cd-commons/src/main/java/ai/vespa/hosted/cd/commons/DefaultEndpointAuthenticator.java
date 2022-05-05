// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.commons;

import ai.vespa.hosted.api.Properties;
import ai.vespa.hosted.cd.EndpointAuthenticator;
import com.yahoo.config.provision.SystemName;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Logger;

import static ai.vespa.hosted.api.Properties.getNonBlankProperty;

/**
 * Authenticates against the hosted Vespa API using private key signatures, and against Vespa applications using mutual TLS.
 *
 * @author jonmv
 */
public class DefaultEndpointAuthenticator implements EndpointAuthenticator {

    private static final Logger logger = Logger.getLogger(DefaultEndpointAuthenticator.class.getName());

    /** Don't touch. */
    public DefaultEndpointAuthenticator(@SuppressWarnings("unused") SystemName __) { }

    /**
     * If {@code System.getProperty("vespa.test.credentials.root")} is set, key and certificate files
     * "key" and "cert" in that directory are used; otherwise, the system default SSLContext is returned.
     */
    @Override
    public SSLContext sslContext() {
        try {
            Path certificateFile = null;
            Path privateKeyFile = null;
            Optional<String> credentialsRootProperty = getNonBlankProperty("vespa.test.credentials.root");
            if (credentialsRootProperty.isPresent()) {
                Path credentialsRoot = Path.of(credentialsRootProperty.get());
                certificateFile = credentialsRoot.resolve("cert");
                privateKeyFile = credentialsRoot.resolve("key");
            }
            else {
                if (Properties.dataPlaneCertificateFile().isPresent())
                    certificateFile = Properties.dataPlaneCertificateFile().get();
                if (Properties.dataPlaneKeyFile().isPresent())
                    privateKeyFile = Properties.dataPlaneKeyFile().get();
            }
            if (certificateFile != null && privateKeyFile != null) {
                X509Certificate certificate = X509CertificateUtils.fromPem(new String(Files.readAllBytes(certificateFile)));
                if (   Instant.now().isBefore(certificate.getNotBefore().toInstant())
                    || Instant.now().isAfter(certificate.getNotAfter().toInstant()))
                    throw new IllegalStateException("Certificate at '" + certificateFile + "' is valid between " +
                                                    certificate.getNotBefore() + " and " + certificate.getNotAfter() + " — not now.");

                PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile)));
                return new SslContextBuilder().withKeyStore(privateKey, certificate).build();
            }
            logger.warning(  "##################################################################################\n"
                           + "# Data plane key and/or certificate missing; please specify                      #\n"
                           + "# '-DdataPlaneCertificateFile=/path/to/certificate' and                          #\n"
                           + "# '-DdataPlaneKeyFile=/path/to/private_key'.                                     #\n"
                           + "# Trying the default SSLContext, but this will most likely cause HTTP error 401. #\n"
                           + "##################################################################################");
            return SSLContext.getDefault();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
