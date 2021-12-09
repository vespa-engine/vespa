// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * Builder for creating a {@link FeedClient} instance.
 *
 * @author bjorncs
 * @author jonmv
 */
public interface FeedClientBuilder {

    String PREFERRED_IMPLEMENTATION_PROPERTY = "vespa.feed.client.builder.implementation";

    /** Creates a builder for a single container endpoint **/
    static FeedClientBuilder create(URI endpoint) { return create(Collections.singletonList(endpoint)); }

    /** Creates a builder for multiple container endpoints **/
    static FeedClientBuilder create(List<URI> endpoints) {
        String defaultImplementation = "ai.vespa.feed.client.impl.FeedClientBuilderImpl";
        String preferredImplementation = System.getProperty(PREFERRED_IMPLEMENTATION_PROPERTY, defaultImplementation);
        Iterator<FeedClientBuilder> iterator = ServiceLoader.load(FeedClientBuilder.class).iterator();
        if (iterator.hasNext()) {
            List<FeedClientBuilder> builders = new ArrayList<>();
            iterator.forEachRemaining(builders::add);
            return builders.stream()
                    .filter(builder -> preferredImplementation.equals(builder.getClass().getName()))
                    .findFirst()
                    .orElse(builders.get(0));
        } else {
            try {
                Class<?> aClass = Class.forName(preferredImplementation);
                for (Constructor<?> constructor : aClass.getConstructors()) {
                    if (constructor.getParameterTypes().length==0) {
                        return ((FeedClientBuilder)constructor.newInstance()).setEndpointUris(endpoints);
                    }
                }
                throw new RuntimeException("Could not find Feed client builder implementation");
            } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Sets the number of connections this client will use per endpoint.
     *
     * A reasonable value here is a value that lets all feed clients (if more than one)
     * collectively have a number of connections which is a small multiple of the numbers
     * of containers in the cluster to feed, so load can be balanced across these containers.
     * In general, this value should be kept as low as possible, but poor connectivity
     * between feeder and cluster may also warrant a higher number of connections.
     */
    FeedClientBuilder setConnectionsPerEndpoint(int max);

    /**
     * Sets the maximum number of streams per HTTP/2 connection for this client.
     *
     * This determines the maximum number of concurrent, inflight requests for this client,
     * which is {@code maxConnections * maxStreamsPerConnection}. Prefer more streams over
     * more connections, when possible.
     * The feed client automatically throttles load to achieve the best throughput, and the
     * actual number of streams per connection is usually lower than the maximum.
     */
    FeedClientBuilder setMaxStreamPerConnection(int max);

    /** Sets {@link SSLContext} instance. */
    FeedClientBuilder setSslContext(SSLContext context);

    /** Sets {@link HostnameVerifier} instance (e.g for disabling default SSL hostname verification). */
    FeedClientBuilder setHostnameVerifier(HostnameVerifier verifier);

    /** Turns off benchmarking. Attempting to get {@link FeedClient#stats()} will result in an exception. */
    FeedClientBuilder noBenchmarking();

    /** Adds HTTP request header to all client requests. */
    FeedClientBuilder addRequestHeader(String name, String value);

    /**
     * Adds HTTP request header to all client requests. Value {@link Supplier} is invoked for each HTTP request,
     * i.e. value can be dynamically updated during a feed.
     */
    FeedClientBuilder addRequestHeader(String name, Supplier<String> valueSupplier);

    /**
     * Overrides default retry strategy.
     * @see FeedClient.RetryStrategy
     */
    FeedClientBuilder setRetryStrategy(FeedClient.RetryStrategy strategy);

    /**
     * Overrides default circuit breaker.
     * @see FeedClient.CircuitBreaker
     */
    FeedClientBuilder setCircuitBreaker(FeedClient.CircuitBreaker breaker);

    /** Sets path to client SSL certificate/key PEM files */
    FeedClientBuilder setCertificate(Path certificatePemFile, Path privateKeyPemFile);

    /** Sets client SSL certificates/key */
    FeedClientBuilder setCertificate(Collection<X509Certificate> certificate, PrivateKey privateKey);

    /** Sets client SSL certificate/key */
    FeedClientBuilder setCertificate(X509Certificate certificate, PrivateKey privateKey);

    FeedClientBuilder setDryrun(boolean enabled);

    /**
     * Overrides JVM default SSL truststore
     * @param caCertificatesFile Path to PEM encoded file containing trusted certificates
     */
    FeedClientBuilder setCaCertificatesFile(Path caCertificatesFile);

    /** Overrides JVM default SSL truststore */
    FeedClientBuilder setCaCertificates(Collection<X509Certificate> caCertificates);

    /** Overrides endpoint URIs for this client */
    FeedClientBuilder setEndpointUris(List<URI> endpoints);

    /** Constructs instance of {@link FeedClient} from builder configuration */
    FeedClient build();

}
