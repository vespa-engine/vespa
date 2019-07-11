package ai.vespa.hosted.auth;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;

public class ApiAuthenticator implements ai.vespa.hosted.api.ApiAuthenticator {

    /** Returns an authenticating controller client, using private key signatures for authentication. */
    @Override
    public ControllerHttpClient controller() {
        return ControllerHttpClient.withSignatureKey(Properties.endpoint(),
                                                     Properties.privateKeyFile(),
                                                     Properties.application());
    }

}
