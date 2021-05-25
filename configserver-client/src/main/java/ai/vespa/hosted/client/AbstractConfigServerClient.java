// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import org.apache.hc.client5.http.classic.methods.ClassicHttpRequests;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * @author jonmv
 */
public abstract class AbstractConfigServerClient implements ConfigServerClient {

    private static final Logger log = Logger.getLogger(AbstractConfigServerClient.class.getName());

    /** Executes the request with the given context. The caller must close the response. */
    abstract ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientContext context) throws IOException;

    /** Executes the given request with response/error handling and retries. */
    private <T> T execute(RequestBuilder builder,
                          BiFunction<ClassicHttpResponse, ClassicHttpRequest, T> handler,
                          ExceptionHandler catcher) {
        HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(builder.config);

        Throwable thrown = null;
        for (URI host : builder.hosts) {
            ClassicHttpRequest request = ClassicHttpRequests.create(builder.method, concat(host, builder.uriBuilder));
            request.setEntity(builder.entity);
            try {
                try {
                    return handler.apply(execute(request, context), request);
                }
                catch (IOException e) {
                    catcher.handle(e, request);
                    throw RetryException.wrap(e, request);
                }
            }
            catch (RetryException e) {
                if (thrown == null)
                    thrown = e.getCause();
                else
                    thrown.addSuppressed(e.getCause());

                if (builder.entity != null && ! builder.entity.isRepeatable()) {
                    log.log(WARNING, "Cannot retry " + request + " as entity is not repeatable");
                    break;
                }
                log.log(FINE, e.getCause(), () -> request + " failed; will retry");
            }
        }
        if (thrown != null) {
            if (thrown instanceof IOException)
                throw new UncheckedIOException((IOException) thrown);
            else if (thrown instanceof RuntimeException)
                throw (RuntimeException) thrown;
            else
                throw new IllegalStateException("Illegal retry cause: " + thrown.getClass(), thrown);
        }

        throw new IllegalStateException("No hosts to perform the request against");
    }

    /** Append path to the given host, which may already contain a root path. */
    static URI concat(URI host, URIBuilder pathAndQuery) {
        URIBuilder builder = new URIBuilder(host);
        List<String> pathSegments = new ArrayList<>(builder.getPathSegments());
        if ( ! pathSegments.isEmpty() && pathSegments.get(pathSegments.size() - 1).isEmpty())
            pathSegments.remove(pathSegments.size() - 1);
        pathSegments.addAll(pathAndQuery.getPathSegments());
        try {
            return builder.setPathSegments(pathSegments)
                          .setParameters(pathAndQuery.getQueryParams())
                          .build();
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("URISyntaxException should not be possible here", e);
        }
    }

    @Override
    public ConfigServerClient.RequestBuilder send(HostStrategy hosts, Method method) {
        return new RequestBuilder(hosts, method);
    }

    /** Builder for a request against a given set of hosts. */
    class RequestBuilder implements ConfigServerClient.RequestBuilder {

        private final Method method;
        private final HostStrategy hosts;
        private final URIBuilder uriBuilder = new URIBuilder();
        private final List<String> pathSegments = new ArrayList<>();
        private HttpEntity entity;
        private RequestConfig config = ConfigServerClient.defaultRequestConfig;
        private ResponseVerifier verifier = ConfigServerClient.throwOnError;
        private ExceptionHandler catcher = ConfigServerClient.retryAll;

        private RequestBuilder(HostStrategy hosts, Method method) {
            if ( ! hosts.iterator().hasNext())
                throw new IllegalArgumentException("Host strategy cannot be empty");

            this.hosts = hosts;
            this.method = requireNonNull(method);
        }

        @Override
        public RequestBuilder at(List<String> pathSegments) {
            this.pathSegments.addAll(pathSegments);
            return this;
        }

        @Override
        public ConfigServerClient.RequestBuilder body(byte[] json) {
            return body(HttpEntities.create(json, ContentType.APPLICATION_JSON));
        }

        @Override
        public RequestBuilder body(HttpEntity entity) {
            this.entity = requireNonNull(entity);
            return this;
        }

        @Override
        public ConfigServerClient.RequestBuilder emptyParameters(List<String> keys) {
            for (String key : keys)
                uriBuilder.setParameter(key, null);

            return this;
        }

        @Override
        public RequestBuilder parameters(List<String> pairs) {
            if (pairs.size() % 2 != 0)
                throw new IllegalArgumentException("Must supply parameter key/values in pairs");

            for (int i = 0; i < pairs.size(); ) {
                String key = pairs.get(i++), value = pairs.get(i++);
                if (value != null)
                    uriBuilder.setParameter(key, value);
            }

            return this;
        }

        @Override
        public RequestBuilder timeout(Duration timeout) {
            return config(RequestConfig.copy(config)
                                       .setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                                       .build());
        }

        @Override
        public RequestBuilder config(RequestConfig config) {
            this.config = requireNonNull(config);
            return this;
        }

        @Override
        public RequestBuilder catching(ExceptionHandler catcher) {
            this.catcher = requireNonNull(catcher);
            return this;
        }

        @Override
        public RequestBuilder throwing(ResponseVerifier verifier) {
            this.verifier = requireNonNull(verifier);
            return this;
        }

        @Override
        public String read() {
            return handle((response, __) -> {
                try (response) {
                    return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                }
                catch (ParseException e) {
                    throw new IllegalStateException(e); // This isn't actually thrown by apache >_<
                }
            });
        }

        @Override
        public <T> T read(Function<byte[], T> mapper) {
            return handle((response, __) -> {
                try (response) {
                    return mapper.apply(response.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(response.getEntity()));
                }
            });
        }

        @Override
        public void discard() throws UncheckedIOException, ResponseException {
            handle((response, __) -> {
                try (response) {
                    return null;
                }
            });
        }

        @Override
        public HttpInputStream stream() throws UncheckedIOException, ResponseException {
            return handle((response, __) -> new HttpInputStream(response));
        }

        @Override
        public <T> T handle(ResponseHandler<T> handler) {
            uriBuilder.setPathSegments(pathSegments);
            return execute(this,
                           (response, request) -> {
                               try {
                                   verifier.verify(response, request); // This throws on unacceptable responses.
                                   return handler.handle(response, request);
                               }
                               catch (IOException | RuntimeException | Error e) {
                                   try {
                                       response.close();
                                   }
                                   catch (IOException f) {
                                       e.addSuppressed(f);
                                   }
                                   if (e instanceof IOException) {
                                       catcher.handle((IOException) e, request);
                                       throw RetryException.wrap((IOException) e, request);
                                   }
                                   else
                                       sneakyThrow(e); // e is a runtime exception or an error, so this is fine.
                                   throw new AssertionError("Should not happen");
                               }
                           },
                           catcher);
        }

    }


    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

}