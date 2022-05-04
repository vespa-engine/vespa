// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.hosted.cd.commons;

import ai.vespa.feed.client.impl.FeedClientBuilderImpl;
import ai.vespa.hosted.cd.EndpointAuthenticator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author mortent
 */
public class FeedClientBuilder extends FeedClientBuilderImpl {

    static AtomicReference<EndpointAuthenticator> endpointAuthenticator = new AtomicReference<>();

    public static void setEndpointAuthenticator(EndpointAuthenticator authenticator) {
        endpointAuthenticator.set(authenticator);
    }

    public FeedClientBuilder() {
        super.setSslContext(Objects.requireNonNull(endpointAuthenticator.get(), FeedClientBuilder.class.getName() + " is not initialized").sslContext());
        endpointAuthenticator.get().authorizationHeaders().forEach(super::addRequestHeader);
    }
}
