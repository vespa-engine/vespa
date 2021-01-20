// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.guiceModules.ConnectorFactoryRegistryModule;
import com.yahoo.jdisc.test.ServerProviderConformanceTest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.cthul.matchers.CthulMatchers.containsPattern;
import static org.cthul.matchers.CthulMatchers.matchesPattern;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Simon Thoresen Hult
 */
public class HttpServerConformanceTest extends ServerProviderConformanceTest {

    private static final Logger log = Logger.getLogger(HttpServerConformanceTest.class.getName());

    private static final String REQUEST_CONTENT = "myRequestContent";
    private static final String RESPONSE_CONTENT = "myResponseContent";

    @SuppressWarnings("LoggerInitializedWithForeignClass")
    private static Logger httpRequestDispatchLogger = Logger.getLogger(HttpRequestDispatch.class.getName());
    private static Level httpRequestDispatchLoggerOriginalLevel;

    /*
     * Reduce logging of every stack trace for {@link ServerProviderConformanceTest.ConformanceException} thrown.
     * This makes the log more readable and the test faster as well.
     */
    @BeforeClass
    public static void reduceExcessiveLogging() {
        httpRequestDispatchLoggerOriginalLevel = httpRequestDispatchLogger.getLevel();
        httpRequestDispatchLogger.setLevel(Level.SEVERE);
    }

    @AfterClass
    public static void restoreExcessiveLogging() {
        httpRequestDispatchLogger.setLevel(httpRequestDispatchLoggerOriginalLevel);
    }

    @AfterClass
    public static void reportDiagnostics() {
        System.out.println(
                "After " + HttpServerConformanceTest.class.getSimpleName()
                + ": #threads=" + Thread.getAllStackTraces().size());
    }

    @Override
    @Test
    public void testContainerNotReadyException() throws Throwable {
        new TestRunner().expect(errorWithReason(is(SC_INTERNAL_SERVER_ERROR), containsString("Container not ready.")))
                        .execute();
    }

    @Override
    @Test
    public void testBindingSetNotFoundException() throws Throwable {
        new TestRunner().expect(errorWithReason(is(SC_NOT_FOUND), containsString("No binding set named 'unknown'.")))
                        .execute();
    }

    @Override
    @Test
    public void testNoBindingSetSelectedException() throws Throwable {
        final Pattern reasonPattern = Pattern.compile(".*No binding set selected for URI 'http://.+/status.html'\\.");
        new TestRunner().expect(errorWithReason(is(SC_INTERNAL_SERVER_ERROR), matchesPattern(reasonPattern)))
                        .execute();
    }

    @Override
    @Test
    public void testBindingNotFoundException() throws Throwable {
        final Pattern contentPattern = Pattern.compile("No binding for URI 'http://.+/status.html'\\.");
        new TestRunner().expect(errorWithReason(is(NOT_FOUND), containsPattern(contentPattern)))
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncCloseResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncWriteResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncHandleResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestHandlerWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestException() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncCloseResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncWriteResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithSyncHandleResponse() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithSyncHandleResponse() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithSyncHandleResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expect(anyOf(successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseCloseNoContentWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseWriteWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithSyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicException() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError(), successNoContent()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithSyncCompletion() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        new TestRunner()
                .expect(anyOf(success(), successNoContent(), serverError()))
                .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicException() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWrite() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWrite() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncCompletion() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError(), successNoContent()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(anyOf(success(), serverError(), successNoContent()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncFailure() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncFailure() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncFailure() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncFailure() throws Throwable {
        new TestRunner().expect(anyOf(success(), successNoContent(), serverError()))
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncFailure() throws Throwable {
        new TestRunner().expect(serverError())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncFailure() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncFailure() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    @Override
    @Test
    public void testResponseWriteCompletionException() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testResponseCloseCompletionException() throws Throwable {
        new TestRunner().expect(success())
                        .execute();
    }

    @Override
    @Test
    public void testResponseCloseCompletionExceptionNoContent() throws Throwable {
        new TestRunner().expect(successNoContent())
                        .execute();
    }

    private static Matcher<ResponseGist> success() {
        final Matcher<Integer> expectedStatusCode = is(OK);
        final Matcher<String> expectedReasonPhrase = is("OK");
        final Matcher<String> expectedContent = is(RESPONSE_CONTENT);
        return responseMatcher(expectedStatusCode, expectedReasonPhrase, expectedContent);
    }

    private static Matcher<ResponseGist> successNoContent() {
        final Matcher<Integer> expectedStatusCode = is(OK);
        final Matcher<String> expectedReasonPhrase = is("OK");
        final Matcher<String> expectedContent = is("");
        return responseMatcher(expectedStatusCode, expectedReasonPhrase, expectedContent);
    }

    private static Matcher<ResponseGist> serverError() {
        final Matcher<Integer> expectedStatusCode = is(INTERNAL_SERVER_ERROR);
        final Matcher<String> expectedReasonPhrase = any(String.class);
        final Matcher<String> expectedContent = containsString(ConformanceException.class.getSimpleName());
        return responseMatcher(expectedStatusCode, expectedReasonPhrase, expectedContent);
    }

    private static Matcher<ResponseGist> errorWithReason(
            final Matcher<Integer> expectedStatusCode, final Matcher<String> expectedReasonPhrase) {
        final Matcher<String> expectedContent = any(String.class);
        return responseMatcher(expectedStatusCode, expectedReasonPhrase, expectedContent);
    }

    private static Matcher<ResponseGist> responseMatcher(
            final Matcher<Integer> expectedStatusCode,
            final Matcher<String> expectedReasonPhrase,
            final Matcher<String> expectedContent) {
        return new TypeSafeMatcher<ResponseGist>() {
            @Override
            public void describeTo(final Description description) {
                description.appendText("status code ");
                expectedStatusCode.describeTo(description);
                description.appendText(", reason ");
                expectedReasonPhrase.describeTo(description);
                description.appendText(" and content ");
                expectedContent.describeTo(description);
            }

            @Override
            protected void describeMismatchSafely(
                    final ResponseGist response, final Description mismatchDescription) {
                mismatchDescription.appendText(" status code was ").appendValue(response.getStatusCode())
                        .appendText(", reason was ").appendValue(response.getReasonPhrase())
                        .appendText(" and content was ").appendValue(response.getContent());
            }

            @Override
            protected boolean matchesSafely(final ResponseGist response) {
                return expectedStatusCode.matches(response.getStatusCode())
                        && expectedReasonPhrase.matches(response.getReasonPhrase())
                        && expectedContent.matches(response.getContent());
            }
        };
    }

    private static class ResponseGist {
        private final int statusCode;
        private final String content;
        private String reasonPhrase;

        public ResponseGist(int statusCode, String reasonPhrase, String content) {
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
            this.content = content;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getContent() {
            return content;
        }

        public String getReasonPhrase() {
            return reasonPhrase;
        }

        @Override
        public String toString() {
            return "ResponseGist {"
                    + " statusCode=" + statusCode
                    + " reasonPhrase=" + reasonPhrase
                    + " content=" + content
                    + " }";
        }
    }

    private class TestRunner implements Adapter<JettyHttpServer, ClientProxy, Future<HttpResponse>> {

        private Matcher<ResponseGist> expectedResponse = null;
        HttpVersion requestVersion;
        private final ExecutorService executorService = Executors.newSingleThreadExecutor();

        void execute() throws Throwable {
            requestVersion = HttpVersion.HTTP_1_0;
            runTest(this);

            requestVersion = HttpVersion.HTTP_1_1;
            runTest(this);

            executorService.shutdown();
        }

        TestRunner expect(final Matcher<ResponseGist> matcher) {
            expectedResponse = matcher;
            return this;
        }

        @Override
        public Module newConfigModule() {
            return Modules.combine(
                    new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(FilterBindings.class)
                                    .toInstance(new FilterBindings.Builder().build());
                            bind(ServerConfig.class)
                                    .toInstance(new ServerConfig(new ServerConfig.Builder()));
                            bind(ServletPathsConfig.class)
                                    .toInstance(new ServletPathsConfig(new ServletPathsConfig.Builder()));
                            bind(ConnectionLog.class)
                                    .toInstance(new VoidConnectionLog());
                        }
                    },
                    new ConnectorFactoryRegistryModule());
        }

        @Override
        public Class<JettyHttpServer> getServerProviderClass() {
            return JettyHttpServer.class;
        }

        @Override
        public ClientProxy newClient(final JettyHttpServer server) throws Throwable {
            return new ClientProxy(server.getListenPort(), requestVersion);
        }

        @Override
        public Future<HttpResponse> executeRequest(
                final ClientProxy client,
                final boolean withRequestContent) throws Throwable {
            final HttpUriRequest request;
            final URI requestUri = URI.create("http://localhost:" + client.listenPort + "/status.html");
            if (!withRequestContent) {
                HttpGet httpGet = new HttpGet(requestUri);
                httpGet.setProtocolVersion(client.requestVersion);
                request = httpGet;
            } else {
                final HttpPost post = new HttpPost(requestUri);
                post.setEntity(new StringEntity(REQUEST_CONTENT, StandardCharsets.UTF_8));
                post.setProtocolVersion(client.requestVersion);
                request = post;
            }
            log.fine(() -> "executorService:"
                    + " .isShutDown()=" + executorService.isShutdown()
                    + " .isTerminated()=" + executorService.isTerminated());
            return executorService.submit(() -> client.delegate.execute(request));
        }

        @Override
        public Iterable<ByteBuffer> newResponseContent() {
            return Collections.singleton(StandardCharsets.UTF_8.encode(RESPONSE_CONTENT));
        }

        @Override
        public void validateResponse(final Future<HttpResponse> responseFuture) throws Throwable {
            final HttpResponse response = responseFuture.get();
            final ResponseGist responseGist = new ResponseGist(
                    response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase(),
                    EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            assertThat(responseGist, expectedResponse);
        }
    }

    private static class ClientProxy {

        final HttpClient delegate;
        final int listenPort;
        final ProtocolVersion requestVersion;

        ClientProxy(final int listenPort, final HttpVersion requestVersion) {
            this.delegate = HttpClientBuilder.create().build();
            this.requestVersion = requestVersion;
            this.listenPort = listenPort;
        }
    }
}
