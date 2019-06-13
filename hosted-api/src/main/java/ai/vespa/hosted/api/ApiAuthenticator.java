package ai.vespa.hosted.api;

public interface ApiAuthenticator {

    /** Returns a client authenticated to talk to the hosted Vespa API. */
    ControllerHttpClient controller();

}
