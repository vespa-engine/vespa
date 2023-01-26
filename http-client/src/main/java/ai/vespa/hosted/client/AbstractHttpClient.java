// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import ai.vespa.http.HttpURL;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Query;
import com.yahoo.time.TimeBudget;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

/**
 * @author jonmv
 */
public abstract class AbstractHttpClient implements HttpClient {

    private static final Logger log = Logger.getLogger(AbstractHttpClient.class.getName());

    public static HttpClient wrapping(CloseableHttpClient client) {
        return new AbstractHttpClient() {
            @Override
            protected ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientContext context) throws IOException {
                return client.execute(request, context);
            }
            @Override
            public void close() throws IOException {
                client.close();
            }
        };
    }

    /** Executes the request with the given context. The caller must close the response. */
    protected abstract ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientContext context) throws IOException;

    /** Executes the given request with response/error handling and retries. */
    private <T> T execute(RequestBuilder builder,
                          BiFunction<ClassicHttpResponse, ClassicHttpRequest, T> handler,
                          ExceptionHandler catcher) {

        Throwable thrown = null;
        for (URI host : builder.hosts) {
            Query query = builder.query;
            for (Supplier<Query> dynamic : builder.dynamicQuery)
                query = query.set(dynamic.get().lastEntries());

            ClassicHttpRequest request = ClassicRequestBuilder.create(builder.method.name())
                                                              .setUri(HttpURL.from(host)
                                                                             .appendPath(builder.path)
                                                                             .appendQuery(query)
                                                                             .asURI())
                                                              .build();
            builder.headers.forEach((name, values) -> values.forEach(value -> request.setHeader(name, value)));

            try (HttpEntity entity = builder.entity.get()) {
                request.setEntity(entity);
                try {
                    return handler.apply(execute(request, contextWithTimeout(builder)), request);
                }
                catch (IOException e) {
                    catcher.handle(e, request);
                    throw RetryException.wrap(e, request);
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException("failed closing request entity", e);
            }
            catch (RetryException e) {
                if (thrown == null)
                    thrown = e.getCause();
                else
                    thrown.addSuppressed(e.getCause());

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

    private HttpClientContext contextWithTimeout(RequestBuilder builder) {
        HttpClientContext context = HttpClientContext.create();
        RequestConfig config = builder.config;
        if (builder.deadline != null) {
            Optional<Duration> remaining = builder.deadline.timeLeftOrThrow();
            if (remaining.isPresent()) {
                config = RequestConfig.copy(config)
                                      .setConnectTimeout(min(config.getConnectTimeout(), remaining.get()))
                                      .setConnectionRequestTimeout(min(config.getConnectionRequestTimeout(), remaining.get()))
                                      .setResponseTimeout(min(config.getResponseTimeout(), remaining.get()))
                                      .build();
            }
        }
        context.setRequestConfig(config);
        return context;
    }

    // TimeBudget guarantees remaining duration is positive.
    static Timeout min(Timeout first, Duration second) {
        long firstMillis = first == null || first.isDisabled() ? second.toMillis() : first.toMilliseconds();
        return Timeout.ofMilliseconds(Math.min(firstMillis, second.toMillis()));
    }

    @Override
    public HttpClient.RequestBuilder send(HostStrategy hosts, Method method) {
        return new RequestBuilder(hosts, method);
    }

    /** Builder for a request against a given set of hosts. */
    class RequestBuilder implements HttpClient.RequestBuilder {

        private final Method method;
        private final HostStrategy hosts;
        private final List<Supplier<Query>> dynamicQuery = new ArrayList<>();
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private HttpURL.Path path = Path.empty();
        private HttpURL.Query query = Query.empty();
        private Supplier<HttpEntity> entity = () -> null;
        private RequestConfig config = HttpClient.defaultRequestConfig;
        private ResponseVerifier verifier = HttpClient.throwOnError;
        private ExceptionHandler catcher = HttpClient.retryAll;
        private TimeBudget deadline;

        private RequestBuilder(HostStrategy hosts, Method method) {
            if ( ! hosts.iterator().hasNext())
                throw new IllegalArgumentException("Host strategy cannot be empty");

            this.hosts = hosts;
            this.method = requireNonNull(method);
        }

        @Override
        public RequestBuilder at(Path subPath) {
            path = path.append(subPath);
            return this;
        }

        @Override
        public HttpClient.RequestBuilder body(byte[] json) {
            return body(() -> HttpEntities.create(json, ContentType.APPLICATION_JSON));
        }

        @Override
        public RequestBuilder body(Supplier<HttpEntity> entity) {
            this.entity = requireNonNull(entity);
            return this;
        }

        @Override
        public HttpClient.RequestBuilder emptyParameters(List<String> keys) {
            for (String key : keys)
                query = query.add(key);

            return this;
        }

        @Override
        public RequestBuilder parameters(List<String> pairs) {
            if (pairs.size() % 2 != 0)
                throw new IllegalArgumentException("Must supply parameter key/values in pairs");

            for (int i = 0; i < pairs.size(); ) {
                String key = pairs.get(i++), value = pairs.get(i++);
                if (value != null)
                    query = query.add(key, value);
            }

            return this;
        }

        @Override
        public HttpClient.RequestBuilder parameters(Query query) {
            this.query = this.query.add(query.entries());
            return this;
        }

        @Override
        public HttpClient.RequestBuilder parameters(Supplier<Query> query) {
            dynamicQuery.add(query);
            return this;
        }

        @Override
        public RequestBuilder timeout(Duration timeout) {
            return config(RequestConfig.copy(config)
                                       .setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                                       .build());
        }

        @Override
        public HttpClient.RequestBuilder deadline(TimeBudget deadline) {
            this.deadline = requireNonNull(deadline);
            return this;
        }

        @Override
        public HttpClient.RequestBuilder addHeader(String name, String value) {
            this.headers.computeIfAbsent(name, __ -> new ArrayList<>()).add(value);
            return this;
        }

        @Override
        public HttpClient.RequestBuilder setHeader(String name, String value) {
            this.headers.put(name, new ArrayList<>(List.of(value)));
            return this;
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