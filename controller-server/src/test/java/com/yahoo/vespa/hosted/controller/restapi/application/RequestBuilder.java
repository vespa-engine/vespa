package com.yahoo.vespa.hosted.controller.restapi.application;

import ai.vespa.hosted.api.MultiPartStreamer;
import com.yahoo.application.container.handler.Request;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.OktaAccessToken;
import com.yahoo.vespa.athenz.api.OktaIdentityToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.ScrewdriverId;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.athenz.HostedAthenzIdentities;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerTest;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.IDENTITY_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_ACCESS_TOKEN_HEADER_NAME;
import static com.yahoo.vespa.hosted.controller.integration.AthenzFilterMock.OKTA_IDENTITY_TOKEN_HEADER_NAME;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

class RequestBuilder implements Supplier<Request> {

    private final String path;
    private final Request.Method method;
    private byte[] data = new byte[0];
    private AthenzIdentity identity;
    private OktaIdentityToken oktaIdentityToken;
    private OktaAccessToken oktaAccessToken;
    private String contentType = "application/json";
    private final Map<String, List<String>> headers = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();

    RequestBuilder(String path, Request.Method method) {
        this.path = path;
        this.method = method;
    }

    /** Make a request with (athens) user domain1.mytenant */
    static RequestBuilder request(String path, Request.Method method) {
        return new RequestBuilder(path, method);
    }

    RequestBuilder data(byte[] data) { this.data = data; return this; }
    RequestBuilder data(String data) { return data(data.getBytes(UTF_8)); }
    RequestBuilder data(MultiPartStreamer streamer) {
        return Exceptions.uncheck(() -> data(streamer.data().readAllBytes()).contentType(streamer.contentType()));
    }

    RequestBuilder userIdentity(UserId userId) { this.identity = HostedAthenzIdentities.from(userId); return this; }
    RequestBuilder screwdriverIdentity(ScrewdriverId screwdriverId) { this.identity = HostedAthenzIdentities.from(screwdriverId); return this; }
    RequestBuilder oktaIdentityToken(OktaIdentityToken oktaIdentityToken) { this.oktaIdentityToken = oktaIdentityToken; return this; }
    RequestBuilder oktaAccessToken(OktaAccessToken oktaAccessToken) { this.oktaAccessToken = oktaAccessToken; return this; }
    RequestBuilder contentType(String contentType) { this.contentType = contentType; return this; }
    RequestBuilder recursive(String recursive) {return properties(Map.of("recursive", recursive)); }
    RequestBuilder properties(Map<String, String> properties) { this.properties.putAll(properties); return this; }
    RequestBuilder header(String name, String value) {
        this.headers.putIfAbsent(name, new ArrayList<>());
        this.headers.get(name).add(value);
        return this;
    }

    static Request addIdentityToRequest(Request request, AthenzIdentity identity) {
        request.getHeaders().put(IDENTITY_HEADER_NAME, identity.getFullName());
        return request;
    }

    protected static Request addOktaIdentityToken(Request request, OktaIdentityToken token) {
        request.getHeaders().put(OKTA_IDENTITY_TOKEN_HEADER_NAME, token.token());
        return request;
    }

    protected static Request addOktaAccessToken(Request request, OktaAccessToken token) {
        request.getHeaders().put(OKTA_ACCESS_TOKEN_HEADER_NAME, token.token());
        return request;
    }

    @Override
    public Request get() {
        Request request = new Request("http://localhost:8080" + path +
                properties.entrySet().stream()
                        .map(entry -> encode(entry.getKey(), UTF_8) + "=" + encode(entry.getValue(), UTF_8))
                        .collect(joining("&", "?", "")),
                data, method);
        request.getHeaders().addAll(headers);
        request.getHeaders().put("Content-Type", contentType);
        // user and domain parameters are translated to a Principal by MockAuthorizer as we do not run HTTP filters
        if (identity != null) {
            addIdentityToRequest(request, identity);
        }
        if (oktaIdentityToken != null) {
            addOktaIdentityToken(request, oktaIdentityToken);
        }
        if (oktaAccessToken != null) {
            addOktaAccessToken(request, oktaAccessToken);
        }
        return request;
    }
}