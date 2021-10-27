// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

public interface ApiAuthenticator {

    /** Returns a client authenticated to talk to the hosted Vespa API. */
    ControllerHttpClient controller();

}
