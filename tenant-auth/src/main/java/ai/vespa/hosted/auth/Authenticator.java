package ai.vespa.hosted.auth;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;

import static ai.vespa.hosted.api.Properties.getNonBlankProperty;
import static ai.vespa.hosted.api.Properties.requireNonBlankProperty;

/**
 * Authenticates against the hosted Vespa API using private key signatures, and against Vespa applications using mutual TLS.
 *
 * @author jonmv
 */
public class Authenticator implements ai.vespa.hosted.api.Authenticator {

    /**
     * If {@code System.getProperty("vespa.test.credentials.root")} is set, key and certificate files
     * "key" and "cert" in that directory are used; otherwise, the system default SSLContext is returned.
     */
    @Override
    public SSLContext sslContext() {
        try {
            Optional<String> credentialsRootProperty = getNonBlankProperty("vespa.test.credentials.root");
            if (credentialsRootProperty.isEmpty())
                return SSLContext.getDefault();

            Path credentialsRoot = Path.of(credentialsRootProperty.get());
            Path certificateFile = credentialsRoot.resolve("cert");
            Path privateKeyFile = credentialsRoot.resolve("key");

            X509Certificate certificate = X509CertificateUtils.fromPem(new String(Files.readAllBytes(certificateFile)));
            if (   Instant.now().isBefore(certificate.getNotBefore().toInstant())
                || Instant.now().isAfter(certificate.getNotAfter().toInstant()))
                throw new IllegalStateException("Certificate at '" + certificateFile + "' is valid between " +
                                                certificate.getNotBefore() + " and " + certificate.getNotAfter() + " — not now.");

            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile)));
            return new SslContextBuilder().withKeyStore(privateKey, certificate).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public HttpRequest.Builder authenticated(HttpRequest.Builder request) {
        return request;
    }

    /** Returns an authenticating controller client, using the (overridable) project properties of this Vespa application. */
    @Override
    public ControllerHttpClient controller() {
        return ControllerHttpClient.withSignatureKey(Properties.endpoint(),
                                                     Properties.privateKeyFile(),
                                                     Properties.application());
    }

}
