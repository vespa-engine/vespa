// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.util.http.hc4;

import ai.vespa.util.http.AcceptAllHostnamesVerifier;
import com.yahoo.security.tls.TlsContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.Collection;

import static com.yahoo.security.tls.TlsContext.getAllowedCipherSuites;
import static com.yahoo.security.tls.TlsContext.getAllowedProtocols;

/**
 * Provides {@link SSLConnectionSocketFactory} that applies protocol restrictions from {@link TlsContext}.
 *
 * @author bjorncs
 */
public class SslConnectionSocketFactory {
    private SslConnectionSocketFactory() {}

    public static SSLConnectionSocketFactory of(SSLContext ctx, HostnameVerifier verifier) {
        return new SSLConnectionSocketFactory(ctx, protocols(ctx), cipherSuites(ctx), verifier);
    }

    public static SSLConnectionSocketFactory of(SSLContext ctx) { return of(ctx, defaultVerifier()); }

    public static SSLConnectionSocketFactory of(TlsContext ctx, HostnameVerifier verifier) {
        return new SSLConnectionSocketFactory(
                ctx.sslContext().context(), ctx.parameters().getProtocols(), ctx.parameters().getCipherSuites(), verifier);
    }

    public static SSLConnectionSocketFactory of(SSLSocketFactory fac, HostnameVerifier verifier) {
        return new SSLConnectionSocketFactory(fac, protocols(), cipherSuites(), verifier);
    }

    public static SSLConnectionSocketFactory of() {
        return new SSLConnectionSocketFactory(TlsContext.defaultSslContext(), protocols(), cipherSuites(), defaultVerifier());
    }

    public static SSLConnectionSocketFactory of(TlsContext ctx) { return of(ctx, defaultVerifier()); }

    public static HostnameVerifier defaultVerifier() { return SSLConnectionSocketFactory.getDefaultHostnameVerifier(); }

    public static HostnameVerifier noopVerifier() { return AcceptAllHostnamesVerifier.instance(); }

    private static String[] cipherSuites(SSLContext ctx) { return array(getAllowedCipherSuites(ctx)); }
    private static String[] protocols(SSLContext ctx) { return array(getAllowedProtocols(ctx)); }
    private static String[] cipherSuites() { return array(getAllowedCipherSuites()); }
    private static String[] protocols() { return array(getAllowedProtocols()); }
    private static String[] array(Collection<String> c) { return c.toArray(String[]::new); }
}
