// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import javax.net.ssl.SSLContext;
import java.net.http.HttpRequest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Adds environment dependent authentication to HTTP request against Vespa deployments.
 *
 * An implementation typically needs to override either of the methods in this interface,
 * and needs to run in different environments, e.g., local user testing and automatic testing
 * in a deployment pipeline.
 *
 * @author jonmv
 */
public interface EndpointAuthenticator {

    /** Returns an SSLContext which provides authentication against a Vespa endpoint. */
    default SSLContext sslContext() {
        try {
            return SSLContext.getDefault();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /** Adds necessary authentication data to the given HTTP request builder, to pass the data plane of a Vespa endpoint. */
    default HttpRequest.Builder authenticated(HttpRequest.Builder request) {
        return request;
    }

}
