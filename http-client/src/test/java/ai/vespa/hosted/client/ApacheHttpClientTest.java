// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import ai.vespa.hosted.client.HttpClient.HostStrategy;
import ai.vespa.hosted.client.HttpClient.ResponseException;
import com.github.tomakehurst.wiremock.http.Fault;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.test.ManualClock;
import com.yahoo.time.TimeBudget;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author jonmv
 */
class ApacheHttpClientTest {

    @RegisterExtension
    final WireMockExtension server = new WireMockExtension();

    HttpClient client;

    @BeforeEach
    void setup() {
        client = AbstractHttpClient.wrapping(HttpClients.createMinimal());
    }

    @Test
    void testRetries() {
        // Two servers in list--two attempts on IOException.
        server.stubFor(get("/root?query=foo"))
              .setResponse(okJson("{}").withFault(Fault.RANDOM_DATA_THEN_CLOSE)
                                       .build());
        assertThrows(UncheckedIOException.class,
                     () -> client.send(HostStrategy.ordered(List.of(URI.create("http://localhost:" + server.port()),
                                                                    URI.create("http://localhost:" + server.port() + "/"))),
                                       Method.GET)
                                 .at("root")
                                 .parameters("query", "foo")
                                 .discard());
        server.verify(2, getRequestedFor(urlEqualTo("/root?query=foo")));
        server.verify(2, anyRequestedFor(anyUrl()));
        server.resetRequests();

        // Two attempts on a different IOException.
        server.stubFor(post("/prefix/root"))
              .setResponse(okJson("{}").withFault(Fault.EMPTY_RESPONSE)
                                       .build());
        assertThrows(UncheckedIOException.class,
                     () -> client.send(HostStrategy.shuffling(List.of(URI.create("http://localhost:" + server.port() + "/prefix"),
                                                                      URI.create("http://localhost:" + server.port() + "/prefix/"))),
                                       Method.POST)
                                 .body("hello".getBytes(UTF_8))
                                 .at("root")
                                 .stream());
        server.verify(2, postRequestedFor(urlEqualTo("/prefix/root")).withRequestBody(equalTo("hello")));
        server.verify(2, anyRequestedFor(anyUrl()));
        server.resetRequests();

        // Successful attempt returns.
        server.stubFor(get("/root/boot/toot"))
              .setResponse(okJson("{}").build());
        assertEquals("{}",
                     client.send(HostStrategy.repeating(URI.create("http://localhost:" + server.port()), 10),
                                 Method.GET)
                           .at("root", "boot")
                           .at("toot")
                           .read(String::new));
        server.verify(1, getRequestedFor(urlEqualTo("/root/boot/toot")));
        server.verify(1, anyRequestedFor(anyUrl()));
        server.resetRequests();

        // ResponseException is not retried.
        server.stubFor(get("/"))
              .setResponse(aResponse().withStatus(409).withBody("hi").build());
        ResponseException thrown = assertThrows(ResponseException.class,
                                                () -> client.send(HostStrategy.repeating(URI.create("http://localhost:" + server.port()), 10),
                                                                  Method.GET)
                                                            .read(String::new));
        assertEquals("GET http://localhost:" + server.port() + "/ failed with status 409 and body 'hi'", thrown.getMessage());
        server.verify(1, getRequestedFor(urlEqualTo("/")));
        server.verify(1, anyRequestedFor(anyUrl()));
        server.resetRequests();

        // Timeout results in UncheckedTimeoutException
        ManualClock clock = new ManualClock();
        TimeBudget budget = TimeBudget.fromNow(clock, Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        UncheckedTimeoutException timeout = assertThrows(UncheckedTimeoutException.class,
                                                         () -> client.send(HostStrategy.repeating(URI.create("http://localhost:" + server.port()), 2),
                                                                           Method.GET)
                                                                     .deadline(budget)
                                                                     .discard());
        assertEquals("Time since start PT1S exceeds timeout PT1S", timeout.getMessage());
        server.verify(0, anyRequestedFor(anyUrl()));
        server.resetRequests();
    }

    @AfterEach
    void teardown() throws IOException {
        client.close();
    }

}
