// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.filter.security.cloud;

import com.yahoo.container.jdisc.AclMapping.Action;
import com.yahoo.container.jdisc.HttpMethodAclMapping;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.RequestHandlerTestDriver.MockResponseHandler;
import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.jdisc.http.filter.util.FilterTestUtils;
import com.yahoo.security.token.Token;
import com.yahoo.security.token.TokenCheckHash;
import com.yahoo.security.token.TokenDomain;
import com.yahoo.security.token.TokenGenerator;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.yahoo.jdisc.Response.Status.FORBIDDEN;
import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static com.yahoo.jdisc.http.filter.security.cloud.CloudTokenDataPlaneFilter.CHECK_HASH_BYTES;
import static com.yahoo.jdisc.http.filter.security.cloud.Permission.READ;
import static com.yahoo.jdisc.http.filter.security.cloud.Permission.WRITE;
import static java.time.Instant.EPOCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author bjorncs
 */
class CloudTokenDataPlaneFilterTest {

    private static final String TOKEN_SEARCH_CLIENT = "token-search-client";
    private static final String TOKEN_FEED_CLIENT = "token-feed-client";
    private static final String TOKEN_CONTEXT = "my-token-context";
    private static final String READ_TOKEN_ID = "my-read-token-id";
    private static final String WRITE_TOKEN_ID = "my-write-token-id";
    private static final Instant TOKEN_EXPIRATION = EPOCH.plus(Duration.ofDays(1));
    private static final Token READ_TOKEN =
            TokenGenerator.generateToken(TokenDomain.of(TOKEN_CONTEXT), "vespa_token_", CHECK_HASH_BYTES);
    private static final Token WRITE_TOKEN =
            TokenGenerator.generateToken(TokenDomain.of(TOKEN_CONTEXT), "vespa_token_", CHECK_HASH_BYTES);
    private static final Token UNKNOWN_TOKEN =
            TokenGenerator.generateToken(TokenDomain.of(TOKEN_CONTEXT), "vespa_token_", CHECK_HASH_BYTES);
    private ManualClock clock;

    @BeforeEach void resetClock() { clock = new ManualClock(EPOCH); }

    @Test
    void supports_handler_with_custom_request_spec() {
        // Spec that maps POST as action 'read'
        var spec = RequestHandlerSpec.builder()
                .withAclMapping(HttpMethodAclMapping.standard()
                                        .override(Method.POST, Action.READ).build())
                .build();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.POST)
                .withHeader("Authorization", "Bearer " + READ_TOKEN.secretTokenString())
                .withAttribute(RequestHandlerSpec.ATTRIBUTE_NAME, spec)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNull(responseHandler.getResponse());
        assertEquals(new ClientPrincipal(Set.of(TOKEN_SEARCH_CLIENT), Set.of(READ)), req.getUserPrincipal());
    }

    @Test
    void fails_on_handler_with_custom_request_spec_with_invalid_action() {
        var spec = RequestHandlerSpec.builder()
                .withAclMapping(HttpMethodAclMapping.standard()
                                        .override(Method.GET, Action.custom("custom")).build())
                .build();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withHeader("Authorization", "Bearer " + READ_TOKEN.secretTokenString())
                .withAttribute(RequestHandlerSpec.ATTRIBUTE_NAME, spec)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    @Test
    void accepts_valid_token() {
        var entry = new AccessLogEntry();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withAccessLogEntry(entry)
                .withHeader("Authorization", "Bearer " + READ_TOKEN.secretTokenString())
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNull(responseHandler.getResponse());
        assertEquals(new ClientPrincipal(Set.of(TOKEN_SEARCH_CLIENT), Set.of(READ)), req.getUserPrincipal());
        assertEquals(READ_TOKEN_ID, entry.getKeyValues().get("token.id").get(0));
        assertEquals(READ_TOKEN.fingerprint().toDelimitedHexString(), entry.getKeyValues().get("token.hash").get(0));
        assertEquals(TOKEN_EXPIRATION.toString(), entry.getKeyValues().get("token.exp").get(0));
    }

    @Test
    void fails_for_token_with_invalid_permission() {
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withHeader("Authorization", "Bearer " + WRITE_TOKEN.secretTokenString())
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    @Test
    void fails_for_missing_token() {
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(UNAUTHORIZED, responseHandler.getResponse().getStatus());
    }

    @Test
    void fails_for_unknown_token() {
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withHeader("Authorization", "Bearer " + UNKNOWN_TOKEN.secretTokenString())
                .build();
        var responseHandler = new MockResponseHandler();
        newFilterWithClientsConfig().filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    @Test
    void fails_for_expired_token() {
        var entry = new AccessLogEntry();
        var req = FilterTestUtils.newRequestBuilder()
                .withMethod(Method.GET)
                .withAccessLogEntry(entry)
                .withHeader("Authorization", "Bearer " + READ_TOKEN.secretTokenString())
                .build();
        var filter = newFilterWithClientsConfig();

        var responseHandler = new MockResponseHandler();
        filter.filter(req, responseHandler);
        assertNull(responseHandler.getResponse());

        clock.advance(Duration.ofDays(1));
        responseHandler = new MockResponseHandler();
        filter.filter(req, responseHandler);
        assertNull(responseHandler.getResponse());

        clock.advance(Duration.ofMillis(1));
        responseHandler = new MockResponseHandler();
        filter.filter(req, responseHandler);
        assertNotNull(responseHandler.getResponse());
        assertEquals(FORBIDDEN, responseHandler.getResponse().getStatus());
    }

    private CloudTokenDataPlaneFilter newFilterWithClientsConfig() {
        return new CloudTokenDataPlaneFilter(
                new CloudTokenDataPlaneFilterConfig.Builder()
                        .tokenContext(TOKEN_CONTEXT)
                        .clients(List.of(
                                new CloudTokenDataPlaneFilterConfig.Clients.Builder()
                                        .tokens(new CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder()
                                                        .id(READ_TOKEN_ID)
                                                        .checkAccessHashes(TokenCheckHash.of(READ_TOKEN, 32).toHexString())
                                                        .fingerprints(READ_TOKEN.fingerprint().toDelimitedHexString())
                                                        .expirations(TOKEN_EXPIRATION.toString()))
                                        .permissions(READ.asString())
                                        .id(TOKEN_SEARCH_CLIENT),
                                new CloudTokenDataPlaneFilterConfig.Clients.Builder()
                                        .tokens(new CloudTokenDataPlaneFilterConfig.Clients.Tokens.Builder()
                                                        .id(WRITE_TOKEN_ID)
                                                        .checkAccessHashes(TokenCheckHash.of(WRITE_TOKEN, 32).toHexString())
                                                        .fingerprints(WRITE_TOKEN.fingerprint().toDelimitedHexString())
                                                        .expirations(TOKEN_EXPIRATION.toString()))
                                        .permissions(WRITE.asString())
                                        .id(TOKEN_FEED_CLIENT)))
                        .build(),
                clock);
    }

}
