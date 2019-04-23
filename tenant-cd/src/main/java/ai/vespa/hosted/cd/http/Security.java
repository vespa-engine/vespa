package ai.vespa.hosted.cd.http;

import com.yahoo.security.KeyUtils;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.X509CertificateUtils;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;

/**
 * Miscellaneous related to HTTP security and authentication.
 */
public class Security {

    private Security() { }

    /** Returns an SSLContext from "key" and "cert" files found under {@code System.getProperty("vespa.test.credentials.root")}. */
    public static SSLContext sslContext() {
        try {
            Path credentialsRoot = Path.of(System.getProperty("vespa.test.credentials.root"));
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
    }

}
