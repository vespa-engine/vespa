// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.FeedClient;
import ai.vespa.feed.client.FeedClientBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Builder for creating a {@link FeedClient} instance.
 *
 * @author bjorncs
 * @author jonmv
 */
public class FeedClientBuilderImpl implements FeedClientBuilder {

    static final FeedClient.RetryStrategy defaultRetryStrategy = new FeedClient.RetryStrategy() { };

    List<URI> endpoints;
    final Map<String, Supplier<String>> requestHeaders = new HashMap<>();
    SSLContext sslContext;
    HostnameVerifier hostnameVerifier;
    int connectionsPerEndpoint = 4;
    int maxStreamsPerConnection = 4096;
    FeedClient.RetryStrategy retryStrategy = defaultRetryStrategy;
    FeedClient.CircuitBreaker circuitBreaker = new GracePeriodCircuitBreaker(Duration.ofSeconds(10));
    Path certificateFile;
    Path privateKeyFile;
    Path caCertificatesFile;
    Collection<X509Certificate> certificate;
    PrivateKey privateKey;
    Collection<X509Certificate> caCertificates;
    boolean benchmark = true;
    boolean dryrun = false;
    boolean speedTest = false;
    URI proxy;


    public FeedClientBuilderImpl() {
    }

    FeedClientBuilderImpl(List<URI> endpoints) {
        this();
        setEndpointUris(endpoints);
    }

    @Override
    public FeedClientBuilder setEndpointUris(List<URI> endpoints) {
        if (endpoints.isEmpty())
            throw new IllegalArgumentException("At least one endpoint must be provided");

        for (URI endpoint : endpoints)
            requireNonNull(endpoint.getHost());
        this.endpoints = new ArrayList<>(endpoints);
        return this;
    }

    @Override
    public FeedClientBuilderImpl setConnectionsPerEndpoint(int max) {
        if (max < 1) throw new IllegalArgumentException("Max connections must be at least 1, but was " + max);
        this.connectionsPerEndpoint = max;
        return this;
    }

    @Override
    public FeedClientBuilderImpl setMaxStreamPerConnection(int max) {
        if (max < 1) throw new IllegalArgumentException("Max streams per connection must be at least 1, but was " + max);
        this.maxStreamsPerConnection = max;
        return this;
    }

    /** Sets {@link SSLContext} instance. */
    @Override
    public FeedClientBuilderImpl setSslContext(SSLContext context) {
        this.sslContext = requireNonNull(context);
        return this;
    }

    /** Sets {@link HostnameVerifier} instance (e.g for disabling default SSL hostname verification). */
    @Override
    public FeedClientBuilderImpl setHostnameVerifier(HostnameVerifier verifier) {
        this.hostnameVerifier = requireNonNull(verifier);
        return this;
    }

    /** Turns off benchmarking. Attempting to get {@link FeedClient#stats()} will result in an exception. */
    @Override
    public FeedClientBuilderImpl noBenchmarking() {
        this.benchmark = false;
        return this;
    }

    /** Adds HTTP request header to all client requests. */
    @Override
    public FeedClientBuilderImpl addRequestHeader(String name, String value) {
        return addRequestHeader(name, () -> requireNonNull(value));
    }

    /**
     * Adds HTTP request header to all client requests. Value {@link Supplier} is invoked for each HTTP request,
     * i.e. value can be dynamically updated during a feed.
     */
    @Override
    public FeedClientBuilderImpl addRequestHeader(String name, Supplier<String> valueSupplier) {
        this.requestHeaders.put(requireNonNull(name), requireNonNull(valueSupplier));
        return this;
    }

    /**
     * Overrides default retry strategy.
     * @see FeedClient.RetryStrategy
     */
    @Override
    public FeedClientBuilderImpl setRetryStrategy(FeedClient.RetryStrategy strategy) {
        this.retryStrategy = requireNonNull(strategy);
        return this;
    }

    /**
     * Overrides default circuit breaker.
     * @see FeedClient.CircuitBreaker
     */
    @Override
    public FeedClientBuilderImpl setCircuitBreaker(FeedClient.CircuitBreaker breaker) {
        this.circuitBreaker = requireNonNull(breaker);
        return this;
    }

    /** Sets path to client SSL certificate/key PEM files */
    @Override
    public FeedClientBuilderImpl setCertificate(Path certificatePemFile, Path privateKeyPemFile) {
        this.certificateFile = certificatePemFile;
        this.privateKeyFile = privateKeyPemFile;
        return this;
    }

    /** Sets client SSL certificates/key */
    @Override
    public FeedClientBuilderImpl setCertificate(Collection<X509Certificate> certificate, PrivateKey privateKey) {
        this.certificate = certificate;
        this.privateKey = privateKey;
        return this;
    }

    /** Sets client SSL certificate/key */
    @Override
    public FeedClientBuilderImpl setCertificate(X509Certificate certificate, PrivateKey privateKey) {
        return setCertificate(Collections.singletonList(certificate), privateKey);
    }

    @Override
    public FeedClientBuilderImpl setDryrun(boolean enabled) {
        this.dryrun = enabled;
        return this;
    }

    @Override
    public FeedClientBuilder setSpeedTest(boolean enabled) {
        this.speedTest = enabled;
        return this;
    }

    /**
     * Overrides JVM default SSL truststore
     * @param caCertificatesFile Path to PEM encoded file containing trusted certificates
     */
    @Override
    public FeedClientBuilderImpl setCaCertificatesFile(Path caCertificatesFile) {
        this.caCertificatesFile = caCertificatesFile;
        return this;
    }

    /** Overrides JVM default SSL truststore */
    @Override
    public FeedClientBuilderImpl setCaCertificates(Collection<X509Certificate> caCertificates) {
        this.caCertificates = caCertificates;
        return this;
    }

    @Override public FeedClientBuilder setProxy(URI uri) { this.proxy = uri; return this; }

    /** Constructs instance of {@link ai.vespa.feed.client.FeedClient} from builder configuration */
    @Override
    public FeedClient build() {
        try {
            validateConfiguration();
            return new HttpFeedClient(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    SSLContext constructSslContext() throws IOException {
        if (sslContext != null) return sslContext;
        SslContextBuilder sslContextBuilder = new SslContextBuilder();
        if (certificateFile != null && privateKeyFile != null) {
            sslContextBuilder.withCertificateAndKey(certificateFile, privateKeyFile);
        } else if (certificate != null && privateKey != null) {
            sslContextBuilder.withCertificateAndKey(certificate, privateKey);
        }
        if (caCertificatesFile != null) {
            sslContextBuilder.withCaCertificates(caCertificatesFile);
        } else if (caCertificates != null) {
            sslContextBuilder.withCaCertificates(caCertificates);
        }
        return sslContextBuilder.build();
    }

    private void validateConfiguration() {
        if (endpoints == null) {
            throw new IllegalArgumentException("At least one endpoint must be provided");
        }
        if (sslContext != null && (
                certificateFile != null || caCertificatesFile != null || privateKeyFile != null ||
                        certificate != null || caCertificates != null || privateKey != null)) {
            throw new IllegalArgumentException("Cannot set both SSLContext and certificate / CA certificates");
        }
        if (certificate != null && certificateFile != null) {
            throw new IllegalArgumentException("Cannot set both certificate directly and as file");
        }
        if (privateKey != null && privateKeyFile != null) {
            throw new IllegalArgumentException("Cannot set both private key directly and as file");
        }
        if (caCertificates != null && caCertificatesFile != null) {
            throw new IllegalArgumentException("Cannot set both CA certificates directly and as file");
        }
        if (certificate != null && certificate.isEmpty()) {
            throw new IllegalArgumentException("Certificate cannot be empty");
        }
        if (caCertificates != null && caCertificates.isEmpty()) {
            throw new IllegalArgumentException("CA certificates cannot be empty");
        }
    }

}
