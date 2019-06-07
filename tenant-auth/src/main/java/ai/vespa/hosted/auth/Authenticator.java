package ai.vespa.hosted.auth;

import ai.vespa.hosted.api.ControllerHttpClient;
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
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;

/**
 * Authenticates {@link HttpRequest}s against a hosted Vespa application based on mutual TLS.
 *
 * @author jonmv
 */
public class Authenticator {

    /** Returns an SSLContext from "key" and "cert" files found under {@code System.getProperty("vespa.test.credentials.root")}. */
    public SSLContext sslContext() {
        try {
            Path credentialsRoot = Path.of(System.getProperty("vespa.test.credentials.root"));
            Path certificateFile = credentialsRoot.resolve("cert");
            Path privateKeyFile = credentialsRoot.resolve("key");

            X509Certificate certificate = X509CertificateUtils.fromPem(new String(Files.readAllBytes(certificateFile)));
            if (Instant.now().isBefore(certificate.getNotBefore().toInstant())
                || Instant.now().isAfter(certificate.getNotAfter().toInstant()))
                throw new IllegalStateException("Certificate at '" + certificateFile + "' is valid between " +
                                                certificate.getNotBefore() + " and " + certificate.getNotAfter() + " — not now.");

            PrivateKey privateKey = KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile)));
            return new SslContextBuilder().withKeyStore(privateKey, certificate).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public HttpRequest.Builder authenticated(HttpRequest.Builder request) {
        return request;
    }

    ApplicationId id = ApplicationId.from(requireNonBlankProperty("tenant"),
                                          requireNonBlankProperty("application"),
                                          getNonBlankProperty("instance").orElse("default"));

    URI endpoint = URI.create(requireNonBlankProperty("endpoint"));
    Path privateKeyFile = Paths.get(requireNonBlankProperty("privateKeyFile"));
    Optional<Path> certificateFile = getNonBlankProperty("certificateFile").map(Paths::get);

    ControllerHttpClient controller = certificateFile.isPresent()
            ? ControllerHttpClient.withKeyAndCertificate(endpoint, privateKeyFile, certificateFile.get())
            : ControllerHttpClient.withSignatureKey(endpoint, privateKeyFile, id);

    static Optional<String> getNonBlankProperty(String name) {
        return Optional.ofNullable(System.getProperty(name)).filter(value -> ! value.isBlank());
    }

    static String requireNonBlankProperty(String name) {
        return getNonBlankProperty(name).orElseThrow(() -> new IllegalStateException("Missing required property '" + name + "'"));
    }

}
