// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import ai.vespa.utils.BytesQuantity;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogEntry;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.container.logging.RequestLogEntry;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.application.BindingSetSelector;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.NullContent;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ConnectorConfig.Throttling;
import com.yahoo.jdisc.http.Cookie;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.server.jetty.JettyTestDriver.TlsClientAuth;
import com.yahoo.jdisc.service.BindingSetNotFoundException;
import com.yahoo.security.SslContextBuilder;
import com.yahoo.security.tls.TlsContext;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.entity.mime.FormBodyPart;
import org.apache.hc.client5.http.entity.mime.FormBodyPartBuilder;
import org.apache.hc.client5.http.entity.mime.StringBody;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.GATEWAY_TIMEOUT;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.Response.Status.REQUEST_TOO_LONG;
import static com.yahoo.jdisc.Response.Status.REQUEST_URI_TOO_LONG;
import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.jdisc.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static com.yahoo.jdisc.http.HttpHeaders.Names.CONNECTION;
import static com.yahoo.jdisc.http.HttpHeaders.Names.CONTENT_TYPE;
import static com.yahoo.jdisc.http.HttpHeaders.Names.COOKIE;
import static com.yahoo.jdisc.http.HttpHeaders.Names.X_DISABLE_CHUNKING;
import static com.yahoo.jdisc.http.HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED;
import static com.yahoo.jdisc.http.HttpHeaders.Values.CLOSE;
import static com.yahoo.jdisc.http.server.jetty.SimpleHttpClient.ResponseValidator;
import static com.yahoo.jdisc.http.server.jetty.Utils.createHttp2Client;
import static com.yahoo.jdisc.http.server.jetty.Utils.createSslTestDriver;
import static com.yahoo.jdisc.http.server.jetty.Utils.generatePrivateKeyAndCertificate;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oyvind Bakksjo
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class HttpServerTest {

    @TempDir
    public File tmpFolder;

    @Test
    void requireThatServerCanListenToRandomPort() {
        final JettyTestDriver driver = JettyTestDriver.newInstance(mockRequestHandler());
        assertNotEquals(0, driver.server().getListenPort());
        assertTrue(driver.close());
    }

    @Test
    void requireThatServerCanNotListenToBoundPort() {
        final JettyTestDriver driver = JettyTestDriver.newInstance(mockRequestHandler());
        try {
            JettyTestDriver.newConfiguredInstance(
                    mockRequestHandler(),
                    new ServerConfig.Builder(),
                    new ConnectorConfig.Builder()
                            .listenPort(driver.server().getListenPort())
            );
        } catch (final Throwable t) {
            assertTrue(t.getCause() instanceof BindException);
        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatBindingSetNotFoundReturns404() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                mockRequestHandler(),
                new ServerConfig.Builder()
                        .developerMode(true),
                new ConnectorConfig.Builder(),
                newBindingSetSelector("unknown"));
        driver.client().get("/status.html")
                .expectStatusCode(is(NOT_FOUND))
                .expectContent(matchesPattern(Pattern.compile(".*" +
                        Pattern.quote(BindingSetNotFoundException.class.getName()) +
                                ": No binding set named &apos;unknown&apos;\\.\n\tat .+",
                        Pattern.DOTALL | Pattern.MULTILINE)));
        assertTrue(driver.close());
    }

    @Test
    void requireThatTooLongInitLineReturns414() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                mockRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder()
                        .requestHeaderSize(1));
        driver.client().get("/status.html")
                .expectStatusCode(is(REQUEST_URI_TOO_LONG));
        assertTrue(driver.close());
    }

    @Test
    void requireThatTooLargePayloadFailsWith413() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder()
                        .maxContentSize(100));
        driver.client().newPost("/status.html")
                .setBinaryContent(new byte[200])
                .execute()
                .expectStatusCode(is(REQUEST_TOO_LONG));
        assertTrue(driver.close());
    }

    @Test
    void requireThatMultipleHostHeadersReturns400() throws Exception {
        var metricConsumer = new MetricConsumerMock();
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                mockRequestHandler(),
                new ServerConfig.Builder().metric(new ServerConfig.Metric.Builder().reporterEnabled(false)),
                new ConnectorConfig.Builder(),
                binder -> binder.bind(MetricConsumer.class).toInstance(metricConsumer.mockitoMock()));
        driver.client()
                .newGet("/status.html").addHeader("Host", "localhost").addHeader("Host", "vespa.ai").execute()
                .expectStatusCode(is(BAD_REQUEST)).expectContent(containsString("reason: Duplicate Host Header"));
        var aggregator = ResponseMetricAggregator.getBean(driver.server());
        var metric = waitForStatistics(aggregator);
        assertEquals(400, metric.dimensions.statusCode);
        assertEquals("GET", metric.dimensions.method);
        assertTrue(driver.close());
    }

    @Test
    void requireThatAccessLogIsCalledForRequestRejectedByJetty() throws Exception {
        BlockingQueueRequestLog requestLogMock = new BlockingQueueRequestLog();
        final JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                mockRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder().requestHeaderSize(1),
                binder -> binder.bind(RequestLog.class).toInstance(requestLogMock));
        driver.client().get("/status.html")
                .expectStatusCode(is(REQUEST_URI_TOO_LONG));
        RequestLogEntry entry = requestLogMock.poll(Duration.ofSeconds(5));
        assertEquals(414, entry.statusCode().getAsInt());
        assertTrue(driver.close());
    }

    @Test
    void requireThatServerCanEcho() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler());
        driver.client().get("/status.html")
                .expectStatusCode(is(OK));
        assertTrue(driver.close());
    }

    @Test
    void requireThatServerCanEchoCompressed() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler());
        try (SimpleHttpClient client = driver.newClient(true)) {
            client.get("/status.html")
                    .expectStatusCode(is(OK));
        }
        assertTrue(driver.close());
    }

    @Test
    void requireThatServerCanHandleMultipleRequests() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler());
        driver.client().get("/status.html")
                .expectStatusCode(is(OK));
        driver.client().get("/status.html")
                .expectStatusCode(is(OK));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostWorks() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final String requestContent = generateContent('a', 30);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent(requestContent)
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith('{' + requestContent + "=[]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostDoesNotRemoveContentByDefault() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("foo=bar")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{foo=[bar]}foo=bar"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostKeepsContentWhenConfiguredTo() throws Exception {
        final JettyTestDriver driver = newDriverWithFormPostContentRemoved(new ParameterPrinterRequestHandler(), false);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("foo=bar")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{foo=[bar]}foo=bar"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostRemovesContentWhenConfiguredTo() throws Exception {
        final JettyTestDriver driver = newDriverWithFormPostContentRemoved(new ParameterPrinterRequestHandler(), true);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("foo=bar")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{foo=[bar]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostWithInvalidDataFailsWith400() throws Exception {
        final JettyTestDriver driver = newDriverWithFormPostContentRemoved(new ParameterPrinterRequestHandler(), true);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("%!Foo=bar")
                        .execute();
        response.expectStatusCode(is(BAD_REQUEST))
                .expectContent(containsString("Failed to parse form parameters"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostWithCharsetSpecifiedWorks() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final String requestContent = generateContent('a', 30);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(X_DISABLE_CHUNKING, "true")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED + ";charset=UTF-8")
                        .setContent(requestContent)
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith('{' + requestContent + "=[]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatEmptyFormPostWorks() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormParametersAreParsed() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("a=b&c=d")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith("{a=[b], c=[d]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatUriParametersAreParsed() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html?a=b&c=d")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{a=[b], c=[d]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormAndUriParametersAreMerged() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html?a=b&c=d1")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("c=d2&e=f")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith("{a=[b], c=[d1, d2], e=[f]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormCharsetIsHonored() throws Exception {
        final JettyTestDriver driver = newDriverWithFormPostContentRemoved(new ParameterPrinterRequestHandler(), true);
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED + ";charset=ISO-8859-1")
                        .setBinaryContent(new byte[]{66, (byte) 230, 114, 61, 98, 108, (byte) 229})
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(is("{B\u00e6r=[bl\u00e5]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatUnknownFormCharsetIsTreatedAsBadRequest() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED + ";charset=FLARBA-GARBA-7")
                        .setContent("a=b")
                        .execute();
        response.expectStatusCode(is(UNSUPPORTED_MEDIA_TYPE));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostWithPercentEncodedContentIsDecoded() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("%20%3D%C3%98=%22%25+")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith("{ =\u00d8=[\"% ]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatFormPostWithThrowingHandlerIsExceptionSafe() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ThrowingHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                        .setContent("a=b")
                        .execute();
        response.expectStatusCode(is(INTERNAL_SERVER_ERROR));
        assertTrue(driver.close());
    }

    @Test
    void requireThatMultiPostWorks() throws Exception {
        // This is taken from tcpdump of bug 5433352 and reassembled here to see that httpserver passes things on.
        final String startTxtContent = "this is a test for POST.";
        final String updaterConfContent
                = "identifier                      = updater\n"
                + "server_type                     = gds\n";
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .setMultipartContent(
                                newFileBody("start.txt", startTxtContent),
                                newFileBody("updater.conf", updaterConfContent))
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(containsString(startTxtContent))
                .expectContent(containsString(updaterConfContent));
    }

    @Test
    void requireThatRequestCookiesAreReceived() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new CookiePrinterRequestHandler());
        final ResponseValidator response =
                driver.client().newPost("/status.html")
                        .addHeader(COOKIE, "foo=bar")
                        .execute();
        response.expectStatusCode(is(OK))
                .expectContent(containsString("[foo=bar]"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatSetCookieHeaderIsCorrect() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new CookieSetterRequestHandler(
                new Cookie("foo", "bar")
                        .setDomain(".localhost")
                        .setHttpOnly(true)
                        .setPath("/foopath")
                        .setSecure(true)));
        driver.client().get("/status.html")
                .expectStatusCode(is(OK))
                .expectHeader("Set-Cookie",
                        is("foo=bar; Path=/foopath; Domain=.localhost; Secure; HttpOnly"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatTimeoutWorks() throws Exception {
        final UnresponsiveHandler requestHandler = new UnresponsiveHandler();
        final JettyTestDriver driver = JettyTestDriver.newInstance(requestHandler);
        driver.client().get("/status.html")
                .expectStatusCode(is(GATEWAY_TIMEOUT));
        ResponseDispatch.newInstance(OK).dispatch(requestHandler.responseHandler);
        assertTrue(driver.close());
    }

    // Header with no value is disallowed by https://tools.ietf.org/html/rfc7230#section-3.2
    // Details in https://github.com/eclipse/jetty.project/issues/1116
    @Test
    void requireThatHeaderWithNullValueIsOmitted() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoWithHeaderRequestHandler("X-Foo", null));
        driver.client().get("/status.html")
                .expectStatusCode(is(OK))
                .expectNoHeader("X-Foo");
        assertTrue(driver.close());
    }

    // Header with empty value is allowed by https://tools.ietf.org/html/rfc7230#section-3.2
    // Details in https://github.com/eclipse/jetty.project/issues/1116
    @Test
    void requireThatHeaderWithEmptyValueIsAllowed() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoWithHeaderRequestHandler("X-Foo", ""));
        driver.client().get("/status.html")
                .expectStatusCode(is(OK))
                .expectHeader("X-Foo", is(""));
        assertTrue(driver.close());
    }

    @Test
    void requireThatNoConnectionHeaderMeansKeepAliveInHttp11KeepAliveDisabled() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new EchoWithHeaderRequestHandler(CONNECTION, CLOSE));
        driver.client().get("/status.html")
                .expectHeader(CONNECTION, is(CLOSE));
        assertTrue(driver.close());
    }

    @Test
    @Disabled("Temporarily ignore until stabilized")
    void requireThatConnectionIsClosedAfterXRequests() throws Exception {
        final int MAX_REQUESTS = 10;
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        ConnectorConfig.Builder connectorConfig = new ConnectorConfig.Builder()
                .maxRequestsPerConnection(MAX_REQUESTS)
                .ssl(new ConnectorConfig.Ssl.Builder()
                        .enabled(true)
                        .clientAuth(ConnectorConfig.Ssl.ClientAuth.Enum.NEED_AUTH)
                        .privateKeyFile(privateKeyFile.toString())
                        .certificateFile(certificateFile.toString())
                        .caCertificateFile(certificateFile.toString()));
        ServerConfig.Builder serverConfig = new ServerConfig.Builder()
                .connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true));
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        Module overrideModule = binder -> binder.bind(ConnectionLog.class).toInstance(connectionLog);
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(), serverConfig, connectorConfig, overrideModule);

        // HTTP/1.1
        for (int i = 0; i < MAX_REQUESTS - 1; i++) {
            driver.client().get("/status.html")
                    .expectStatusCode(is(OK))
                    .expectNoHeader(CONNECTION);
        }
        driver.client().get("/status.html")
                .expectStatusCode(is(OK))
                .expectHeader(CONNECTION, is(CLOSE));

        // HTTP/2
        try (CloseableHttpAsyncClient client = createHttp2Client(driver)) {
            String uri = "https://localhost:" + driver.server().getListenPort() + "/status.html";
            for (int i = 0; i < 2 * MAX_REQUESTS; i++) {
                try {
                    client.execute(SimpleRequestBuilder.get(uri).build(), null).get();
                } catch (ExecutionException e) {
                    // Client sometimes throws ExecutionException with ConnectionClosedException as cause
                    // on the last request.
                    if (!(e.getCause() instanceof ConnectionClosedException)) throw e;
                }
            }
        }
        assertTrue(driver.close());
        long http2Connections = connectionLog.logEntries().stream()
                .filter(e -> e.httpProtocol().orElseThrow().equals("HTTP/2.0"))
                .count();
        assertEquals(2, http2Connections);
    }

    @Test
    void requireThatServerCanRespondToSslRequest() throws Exception {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);

        final JettyTestDriver driver = JettyTestDriver.newInstanceWithSsl(new EchoRequestHandler(), certificateFile, privateKeyFile, TlsClientAuth.WANT);
        driver.client().get("/status.html")
                .expectStatusCode(is(OK));
        assertTrue(driver.close());
    }


    @Test
    void requireThatTlsClientAuthenticationEnforcerRejectsRequestsForNonWhitelistedPaths() throws IOException {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        JettyTestDriver driver = createSslWithTlsClientAuthenticationEnforcer(certificateFile, privateKeyFile);

        SSLContext trustStoreOnlyCtx = new SslContextBuilder()
                .withTrustStore(certificateFile)
                .build();

        try (var c = new SimpleHttpClient(trustStoreOnlyCtx, driver.server().getListenPort(), false)) {
            c.get("/dummy.html").expectStatusCode(is(UNAUTHORIZED));
        }

        assertTrue(driver.close());
    }

    @Test
    void requireThatTlsClientAuthenticationEnforcerAllowsRequestForWhitelistedPaths() throws IOException {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        JettyTestDriver driver = JettyTestDriver.newInstanceWithSsl(new EchoRequestHandler(), certificateFile, privateKeyFile, TlsClientAuth.WANT);

        SSLContext trustStoreOnlyCtx = new SslContextBuilder()
                .withTrustStore(certificateFile)
                .build();

        try (var c = new SimpleHttpClient(trustStoreOnlyCtx, driver.server().getListenPort(), false)) {
            c.get("/status.html").expectStatusCode(is(OK));
        }

        assertTrue(driver.close());
    }

    @Test
    void requireThatConnectedAtReturnsNonZero() throws Exception {
        final JettyTestDriver driver = JettyTestDriver.newInstance(new ConnectedAtRequestHandler());
        driver.client().get("/status.html")
                .expectStatusCode(is(OK))
                .expectContent(matchesPattern("\\d{13,}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatGzipEncodingRequestsAreAutomaticallyDecompressed() throws Exception {
        JettyTestDriver driver = JettyTestDriver.newInstance(new ParameterPrinterRequestHandler());
        String requestContent = generateContent('a', 30);
        ResponseValidator response = driver.client().newPost("/status.html")
                .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .setGzipContent(requestContent)
                .execute();
        response.expectStatusCode(is(OK))
                .expectContent(startsWith('{' + requestContent + "=[]}"));
        assertTrue(driver.close());
    }

    @Test
    void requireThatResponseStatsAreCollected() throws Exception {
        RequestTypeHandler handler = new RequestTypeHandler();
        var cfg = new ServerConfig.Builder().metric(new ServerConfig.Metric.Builder().reporterEnabled(false));
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(handler, cfg, new ConnectorConfig.Builder());
        var statisticsCollector = ResponseMetricAggregator.getBean(driver.server());;
        {
            List<ResponseMetricAggregator.StatisticsEntry> stats = statisticsCollector.takeStatistics();
            assertEquals(0, stats.size());
        }

        {
            driver.client().newPost("/status.html").execute();
            var entry = waitForStatistics(statisticsCollector);
            assertEquals("http", entry.dimensions.scheme);
            assertEquals("POST", entry.dimensions.method);
            assertEquals("http.status.2xx", entry.name);
            assertEquals("write", entry.dimensions.requestType);
            assertEquals(1, entry.value);
        }

        {
            driver.client().newGet("/status.html").execute();
            var entry = waitForStatistics(statisticsCollector);
            assertEquals("http", entry.dimensions.scheme);
            assertEquals("GET", entry.dimensions.method);
            assertEquals("http.status.2xx", entry.name);
            assertEquals("read", entry.dimensions.requestType);
            assertEquals(1, entry.value);
        }

        {
            handler.setRequestType(Request.RequestType.READ);
            driver.client().newPost("/status.html").execute();
            var entry = waitForStatistics(statisticsCollector);
            assertEquals("read", entry.dimensions.requestType, "Handler overrides request type");
        }

        assertTrue(driver.close());
    }

    private ResponseMetricAggregator.StatisticsEntry waitForStatistics(ResponseMetricAggregator
                                                                                      statisticsCollector) {
        List<ResponseMetricAggregator.StatisticsEntry> entries = List.of();
        int tries = 0;
        // Wait up to 30 seconds before giving up
        while (entries.isEmpty() && tries < 300) {
            entries = statisticsCollector.takeStatistics();
            if (entries.isEmpty())
                try {Thread.sleep(100); } catch (InterruptedException e) {}
            tries++;
        }
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    @Test
    void requireThatConnectionThrottleDoesNotBlockConnectionsBelowThreshold() throws Exception {
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder()
                        .throttling(new Throttling.Builder()
                                .enabled(true)
                                .maxAcceptRate(10)
                                .maxHeapUtilization(1.0)
                                .maxConnections(10)));
        driver.client().get("/status.html")
                .expectStatusCode(is(OK));
        assertTrue(driver.close());
    }

    @Test
    void requireThatConnectionIsTrackedInConnectionLog() throws Exception {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        Module overrideModule = binder -> binder.bind(ConnectionLog.class).toInstance(connectionLog);
        JettyTestDriver driver = JettyTestDriver.newInstanceWithSsl(new OkRequestHandler(), certificateFile, privateKeyFile, TlsClientAuth.NEED, overrideModule);
        int listenPort = driver.server().getListenPort();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            builder.append(i);
        }
        byte[] content = builder.toString().getBytes();
        for (int i = 0; i < 100; i++) {
            driver.client().newPost("/status.html").setBinaryContent(content).execute()
                    .expectStatusCode(is(OK));
        }
        assertTrue(driver.close());
        List<ConnectionLogEntry> logEntries = connectionLog.logEntries();
        assertEquals(1, logEntries.size());
        ConnectionLogEntry logEntry = logEntries.get(0);
        assertEquals(4, UUID.fromString(logEntry.id()).version());
        assertTrue(logEntry.timestamp().isAfter(Instant.EPOCH));
        assertEquals(100L, logEntry.requests().get());
        assertEquals(100L, logEntry.responses().get());
        assertEquals("127.0.0.1", logEntry.peerAddress().get());
        assertEquals("127.0.0.1", logEntry.localAddress().get());
        assertEquals(listenPort, logEntry.localPort().get());
        assertTrue(logEntry.httpBytesReceived().get() > 100000L);
        assertTrue(logEntry.httpBytesSent().get() > 10000L);
        assertTrue(TlsContext.ALLOWED_PROTOCOLS.contains(logEntry.sslProtocol().get()));
        assertEquals("CN=localhost", logEntry.sslPeerSubject().get());
        assertFalse(logEntry.sslCipherSuite().get().isBlank());
        assertEquals(64, logEntry.sslSessionId().get().length());
        assertEquals(Instant.EPOCH, logEntry.sslPeerNotBefore().get());
        assertEquals(Instant.EPOCH.plus(100_000, ChronoUnit.DAYS), logEntry.sslPeerNotAfter().get());
        assertTrue(logEntry.sslBytesReceived().get() > 100000L);
        assertTrue(logEntry.sslBytesSent().get() > 10000L);
    }

    @Test
    void requireThatRequestIsTrackedInAccessLog() throws IOException, InterruptedException {
        BlockingQueueRequestLog requestLogMock = new BlockingQueueRequestLog();
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder().accessLog(
                        new ConnectorConfig.AccessLog.Builder()
                                .content(List.of(
                                        new ConnectorConfig.AccessLog.Content.Builder()
                                                .pathPrefix("/search")
                                                .maxSize(BytesQuantity.ofKB(1).toBytes())
                                                .sampleRate(1),
                                        new ConnectorConfig.AccessLog.Content.Builder()
                                                .pathPrefix("/document/v1")
                                                .maxSize(BytesQuantity.ofMB(1).toBytes())
                                                .sampleRate(0.001)
                                ))),
                binder -> binder.bind(RequestLog.class).toInstance(requestLogMock));
        driver.client().newPost("/search/").setContent("abcdef").execute().expectStatusCode(is(OK));
        RequestLogEntry entry = requestLogMock.poll(Duration.ofSeconds(5));
        assertEquals(200, entry.statusCode().getAsInt());
        assertEquals(6, entry.requestSize().getAsLong());
        assertEquals("text/plain; charset=UTF-8", entry.content().get().type());
        assertEquals(6, entry.content().get().length());
        assertEquals("abcdef", new String(entry.content().get().body(), UTF_8));
        assertTrue(driver.close());
    }

    @Test
    void requireThatRequestsPerConnectionMetricIsAggregated() throws IOException {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        var metricConsumer = new MetricConsumerMock();
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        JettyTestDriver driver = createSslTestDriver(certificateFile, privateKeyFile, metricConsumer, connectionLog);
        driver.client().get("/").expectStatusCode(is(OK));
        assertTrue(driver.close());
        verify(metricConsumer.mockitoMock(), atLeast(1))
                .set(MetricDefinitions.REQUESTS_PER_CONNECTION, 1L, MetricConsumerMock.STATIC_CONTEXT);
    }

    @Test
    void uriWithEmptyPathSegmentIsAllowed() throws Exception {
        Path privateKeyFile = File.createTempFile("junit", null, tmpFolder).toPath();
        Path certificateFile = File.createTempFile("junit", null, tmpFolder).toPath();
        generatePrivateKeyAndCertificate(privateKeyFile, certificateFile);
        MetricConsumerMock metricConsumer = new MetricConsumerMock();
        InMemoryConnectionLog connectionLog = new InMemoryConnectionLog();
        JettyTestDriver driver = createSslTestDriver(certificateFile, privateKeyFile, metricConsumer, connectionLog);
        String uriPath = "/path/with/empty//segment";

        // HTTP/1.1
        driver.client().get(uriPath).expectStatusCode(is(OK));

        // HTTP/2
        try (CloseableHttpAsyncClient client = createHttp2Client(driver)) {
            String uri = "https://localhost:" + driver.server().getListenPort() + uriPath;
            SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get(uri).build(), null).get();
            assertEquals(OK, response.getCode());
        }

        assertTrue(driver.close());
    }

    @Test
    void fallbackServerNameCanBeOverridden() throws Exception {
        String fallbackHostname = "myhostname";
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new UriRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder()
                        .serverName(new ConnectorConfig.ServerName.Builder().fallback(fallbackHostname)));
        int listenPort = driver.server().getListenPort();
        HttpGet req = new HttpGet("http://localhost:" + listenPort + "/");
        req.setHeader("Host", null);
        driver.client().execute(req)
                .expectStatusCode(is(OK))
                .expectContent(containsString("http://" + fallbackHostname + ":" + listenPort + "/"));
        assertTrue(driver.close());
    }

    @Test
    void acceptedServerNamesCanBeRestricted() throws Exception {
        String requiredServerName = "myhostname";
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder()
                        .serverName(new ConnectorConfig.ServerName.Builder().allowed(requiredServerName)));
        int listenPort = driver.server().getListenPort();
        HttpGet req = new HttpGet("http://localhost:" + listenPort + "/");
        req.setHeader("Host", requiredServerName);
        driver.client().execute(req).expectStatusCode(is(OK));
        driver.client().get("/").expectStatusCode(is(NOT_FOUND));
        assertTrue(driver.close());
    }

    @Test
    void exceedingMaxContentSizeReturns413() throws IOException {
        JettyTestDriver driver = JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder(),
                new ConnectorConfig.Builder().maxContentSize(4));
        driver.client().newPost("/").setBinaryContent(new byte[4]).execute().expectStatusCode(is(OK));
        driver.client().newPost("/").setBinaryContent(new byte[5]).execute().expectStatusCode(is(REQUEST_TOO_LONG));
        assertTrue(driver.close());
    }

    private static JettyTestDriver createSslWithTlsClientAuthenticationEnforcer(Path certificateFile, Path privateKeyFile) {
        ConnectorConfig.Builder connectorConfig = new ConnectorConfig.Builder()
                .tlsClientAuthEnforcer(
                        new ConnectorConfig.TlsClientAuthEnforcer.Builder()
                                .enable(true)
                                .pathWhitelist("/status.html"))
                .ssl(new ConnectorConfig.Ssl.Builder()
                        .enabled(true)
                        .clientAuth(ConnectorConfig.Ssl.ClientAuth.Enum.WANT_AUTH)
                        .privateKeyFile(privateKeyFile.toString())
                        .certificateFile(certificateFile.toString())
                        .caCertificateFile(certificateFile.toString()));
        return JettyTestDriver.newConfiguredInstance(
                new EchoRequestHandler(),
                new ServerConfig.Builder().connectionLog(new ServerConfig.ConnectionLog.Builder().enabled(true)),
                connectorConfig,
                binder -> {});
    }

    private static RequestHandler mockRequestHandler() {
        final RequestHandler mockRequestHandler = mock(RequestHandler.class);
        when(mockRequestHandler.refer()).thenReturn(References.NOOP_REFERENCE);
        when(mockRequestHandler.refer(any())).thenReturn(References.NOOP_REFERENCE);
        return mockRequestHandler;
    }

    private static String generateContent(final char c, final int len) {
        final StringBuilder ret = new StringBuilder(len);
        for (int i = 0; i < len; ++i) {
            ret.append(c);
        }
        return ret.toString();
    }

    private static JettyTestDriver newDriverWithFormPostContentRemoved(RequestHandler requestHandler,
                                                                       boolean removeFormPostBody) {
        return JettyTestDriver.newConfiguredInstance(
                requestHandler,
                new ServerConfig.Builder()
                        .removeRawPostBodyForWwwUrlEncodedPost(removeFormPostBody),
                new ConnectorConfig.Builder());
    }

    private static FormBodyPart newFileBody(final String fileName, final String fileContent) {
        return FormBodyPartBuilder.create()
                .setBody(
                        new StringBody(fileContent, ContentType.TEXT_PLAIN) {
                            @Override public String getFilename() { return fileName; }
                            @Override public String getMimeType() { return ""; }
                            @Override public String getCharset() { return null; }
                        })
                .setName(fileName)
                .build();
    }

    private static class ConnectedAtRequestHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            final HttpRequest httpRequest = (HttpRequest)request;
            final String connectedAt = String.valueOf(httpRequest.getConnectedAt(TimeUnit.MILLISECONDS));
            final ContentChannel ch = handler.handleResponse(new Response(OK));
            ch.write(ByteBuffer.wrap(connectedAt.getBytes(UTF_8)), null);
            ch.close(null);
            return null;
        }
    }

    private static class CookieSetterRequestHandler extends AbstractRequestHandler {

        final Cookie cookie;

        CookieSetterRequestHandler(final Cookie cookie) {
            this.cookie = cookie;
        }

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            final HttpResponse response = HttpResponse.newInstance(OK);
            response.encodeSetCookieHeader(List.of(cookie));
            ResponseDispatch.newInstance(response).dispatch(handler);
            return null;
        }
    }

    private static class CookiePrinterRequestHandler extends AbstractRequestHandler {

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            List<Cookie> cookies = new ArrayList<>(((HttpRequest)request).decodeCookieHeader());
            cookies.sort(new CookieComparator());
            final ContentChannel out = ResponseDispatch.newInstance(Response.Status.OK).connect(handler);
            out.write(UTF_8.encode(cookies.toString()), null);
            out.close(null);
            return null;
        }
    }

    private static class ParameterPrinterRequestHandler extends AbstractRequestHandler {

        private static final CompletionHandler NULL_COMPLETION_HANDLER = null;

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            Map<String, List<String>> parameters = new TreeMap<>(((HttpRequest)request).parameters());
            ContentChannel responseContentChannel = ResponseDispatch.newInstance(Response.Status.OK).connect(handler);
            responseContentChannel.write(ByteBuffer.wrap(parameters.toString().getBytes(UTF_8)),
                                         NULL_COMPLETION_HANDLER);

            // Have the request content written back to the response.
            return responseContentChannel;
        }
    }

    private static class RequestTypeHandler extends AbstractRequestHandler {

        private Request.RequestType requestType = null;

        public void setRequestType(Request.RequestType requestType) {
            this.requestType = requestType;
        }

        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            Response response = new Response(OK);
            response.setRequestType(requestType);
            return handler.handleResponse(response);
        }
    }

    private static class ThrowingHandler extends AbstractRequestHandler {
        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            throw new RuntimeException("Deliberately thrown exception");
        }
    }

    private static class UnresponsiveHandler extends AbstractRequestHandler {

        ResponseHandler responseHandler;

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            request.setTimeout(100, TimeUnit.MILLISECONDS);
            responseHandler = handler;
            return null;
        }
    }

    private static class OkRequestHandler extends AbstractRequestHandler {
        @Override
        public ContentChannel handleRequest(Request request, ResponseHandler handler) {
            Response response = new Response(OK);
            handler.handleResponse(response).close(null);
            return NullContent.INSTANCE;
        }
    }

    private static class EchoWithHeaderRequestHandler extends AbstractRequestHandler {

        final String headerName;
        final String headerValue;

        EchoWithHeaderRequestHandler(final String headerName, final String headerValue) {
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        @Override
        public ContentChannel handleRequest(final Request request, final ResponseHandler handler) {
            final Response response = new Response(OK);
            response.headers().add(headerName, headerValue);
            return handler.handleResponse(response);
        }
    }

    private static class UriRequestHandler extends AbstractRequestHandler {
        @Override
        public ContentChannel handleRequest(Request req, ResponseHandler handler) {
            final ContentChannel ch = handler.handleResponse(new Response(OK));
            ch.write(ByteBuffer.wrap(req.getUri().toString().getBytes(UTF_8)), null);
            ch.close(null);
            return null;
        }
    }

    private static Module newBindingSetSelector(final String setName) {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(BindingSetSelector.class).toInstance(new BindingSetSelector() {

                    @Override
                    public String select(final URI uri) {
                        return setName;
                    }
                });
            }
        };
    }

    private static class CookieComparator implements Comparator<Cookie> {

        @Override
        public int compare(final Cookie lhs, final Cookie rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    }

}
