package ai.vespa.hosted.auth;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;

public class ApiAuthenticator implements ai.vespa.hosted.api.ApiAuthenticator {

    /** Returns a controller client using mTLS if a key and certificate pair is provided, or signed requests otherwise. */
    @Override
    public ControllerHttpClient controller() {
        return Properties.apiCertificateFile()
                         .map(certificateFile -> ControllerHttpClient.withKeyAndCertificate(Properties.apiEndpoint(),
                                                                                            Properties.apiKeyFile(),
                                                                                            certificateFile))
                         .orElseGet(() ->
                                            ControllerHttpClient.withSignatureKey(Properties.apiEndpoint(),
                                                                                  Properties.apiKeyFile(),
                                                                                  Properties.application()));
    }

}
