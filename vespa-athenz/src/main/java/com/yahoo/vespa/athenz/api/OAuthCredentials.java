// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author freva
 */
public class OAuthCredentials {

    private final String accessTokenCookieName;
    private final String accessToken;
    private final String idTokenCookieName;
    private final String idToken;

    private OAuthCredentials(String accessTokenCookieName, String accessToken, String idTokenCookieName, String idToken) {
        this.accessTokenCookieName = Objects.requireNonNull(accessTokenCookieName);
        this.accessToken = Objects.requireNonNull(accessToken);
        this.idTokenCookieName = Objects.requireNonNull(idTokenCookieName);
        this.idToken = Objects.requireNonNull(idToken);
    }

    public String accessToken() { return accessToken; }
    public String idToken() { return idToken; }

    public String asCookie() {
        return String.format("%s=%s; %s=%s", accessTokenCookieName, accessToken, idTokenCookieName, idToken);
    }

    public static OAuthCredentials fromOktaRequestContext(Map<String, Object> requestContext) {
        return new OAuthCredentials("okta_at", requireToken(requestContext, "okta.access-token", "No Okta Access Token provided"),
                                    "okta_it", requireToken(requestContext, "okta.identity-token", "No Okta Identity Token provided"));
    }

    public static OAuthCredentials fromAuth0RequestContext(Map<String, Object> requestContext) {
        return new OAuthCredentials("access_token", requireToken(requestContext, "auth0.access-token", "No Auth0 Access Token provided"),
                                    "id_token", requireToken(requestContext, "auth0.identity-token", "No Auth0 Identity Token provided"));
    }

    public static OAuthCredentials createForTesting(String accessToken, String idToken) {
        return new OAuthCredentials("accessToken", accessToken, "idToken", idToken);
    }

    private static String requireToken(Map<String, Object> context, String attribute, String errorMessage) {
        return Optional.ofNullable(context.get(attribute))
                .map(String.class::cast)
                .orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

}
