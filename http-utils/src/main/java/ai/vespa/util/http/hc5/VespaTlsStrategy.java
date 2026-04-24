// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc5;

import ai.vespa.util.http.AcceptAllHostnamesVerifier;
import com.yahoo.security.tls.TlsContext;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.HttpsSupport;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import java.util.Collection;

import static com.yahoo.security.tls.TlsContext.getAllowedCipherSuites;
import static com.yahoo.security.tls.TlsContext.getAllowedProtocols;

/**
 * Provides {@link TlsSocketStrategy} that applies protocol restrictions from {@link TlsContext}.
 *
 * @author bjorncs
 */
public class VespaTlsStrategy {
    private VespaTlsStrategy() {}

    public static ClientTlsStrategyBuilder tlsStrategyBuilder() {
        return ClientTlsStrategyBuilder.create()
                .setHostnameVerifier(noopVerifier())
                .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT);
    }

    public static TlsSocketStrategy of(SSLContext ctx, HostnameVerifier verifier) {
        return tlsStrategyBuilder()
                .setHostnameVerifier(verifier)
                .setSslContext(ctx)
                .setTlsVersions(protocols(ctx))
                .setCiphers(cipherSuites(ctx))
                .buildClassic();
    }

    public static TlsSocketStrategy of(SSLContext ctx) { return of(ctx, defaultVerifier()); }

    public static TlsSocketStrategy of(TlsContext ctx, HostnameVerifier verifier) {
        return tlsStrategyBuilder()
                .setHostnameVerifier(verifier)
                .setSslContext(ctx.sslContext().context())
                .setTlsVersions(ctx.parameters().getProtocols())
                .setCiphers(ctx.parameters().getCipherSuites())
                .buildClassic();
    }

    public static TlsSocketStrategy of(TlsContext ctx) { return of(ctx, defaultVerifier()); }

    public static TlsSocketStrategy of(HostnameVerifier verifier) {
        return of(TlsContext.defaultSslContext(), verifier);
    }

    public static HostnameVerifier defaultVerifier() { return HttpsSupport.getDefaultHostnameVerifier(); }

    public static HostnameVerifier noopVerifier() { return AcceptAllHostnamesVerifier.instance(); }

    private static String[] cipherSuites(SSLContext ctx) { return array(getAllowedCipherSuites(ctx)); }
    private static String[] protocols(SSLContext ctx) { return array(getAllowedProtocols(ctx)); }
    private static String[] array(Collection<String> c) { return c.toArray(String[]::new); }
}
