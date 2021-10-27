// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

public class DefaultApiAuthenticator implements ai.vespa.hosted.api.ApiAuthenticator {

    /** Returns a controller client using mTLS if a key and certificate pair is provided, or signed requests otherwise. */
    @Override
    public ControllerHttpClient controller() {
        return Properties.apiCertificateFile()
                         .map(certificateFile -> ControllerHttpClient.withKeyAndCertificate(Properties.apiEndpoint(),
                                                                                            Properties.apiKeyFile(),
                                                                                            certificateFile))
                         .or(() -> Properties.apiKey().map(apiKey -> ControllerHttpClient.withSignatureKey(Properties.apiEndpoint(),
                                                                                                           apiKey,
                                                                                                           Properties.application())))
                         .orElseGet(() -> ControllerHttpClient.withSignatureKey(Properties.apiEndpoint(),
                                                                                Properties.apiKeyFile(),
                                                                                Properties.application()));
    }

}
