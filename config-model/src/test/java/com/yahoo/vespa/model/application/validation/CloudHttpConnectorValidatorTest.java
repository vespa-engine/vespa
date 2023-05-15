// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class CloudHttpConnectorValidatorTest {

    private static final String CUSTOM_SSL_ON_8080 =
            """
            <server port='8080' id='default'>
                <ssl>
                  <private-key-file>/foo/key</private-key-file>
                  <certificate-file>/foo/cert</certificate-file>
                </ssl>
            </server>
            """;

    private static final String DEFAULT_SSL_ON_8080 =
            """
            <server port='8080' id='default'/>
            """;

    private static final String ADDITIONAL_CONNECTOR =
            """
            <server port='8080' id='default'/>
            <server port='1234' id='custom'/>
            """;

    @Test
    void fails_on_custom_ssl_for_cloud_application() {
        var exception = assertThrows(IllegalArgumentException.class, () -> runValidatorOnApp(true, "", CUSTOM_SSL_ON_8080));
        var expected = "Adding additional or modifying existing HTTPS connectors is not allowed for Vespa Cloud applications. " +
                "Violating connectors: [default@8080]. See https://cloud.vespa.ai/en/security/whitepaper, " +
                "https://cloud.vespa.ai/en/security/guide#data-plane.";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void allows_custom_ssl_for_infra() {
        assertDoesNotThrow(() -> runValidatorOnApp(true, " application-type='hosted-infrastructure'", CUSTOM_SSL_ON_8080));
    }

    @Test
    void allows_custom_ssl_for_self_hosted() {
        assertDoesNotThrow(() -> runValidatorOnApp(false, "", CUSTOM_SSL_ON_8080));
    }

    @Test
    void fails_on_additional_connectors_for_cloud_application() {
        var exception = assertThrows(IllegalArgumentException.class, () -> runValidatorOnApp(true, "", ADDITIONAL_CONNECTOR));
        var expected = "Illegal port 1234 in http server 'custom': Port must be set to 8080"; // Currently fails earlier in model construction
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void allows_additional_connectors_for_self_hosted() {
        assertDoesNotThrow(() -> runValidatorOnApp(false, "", ADDITIONAL_CONNECTOR));
    }

    @Test
    void allows_default_ssl_for_cloud_application() {
        assertDoesNotThrow(() -> runValidatorOnApp(true, "", DEFAULT_SSL_ON_8080));
    }

    @Test
    void allows_default_ssl_for_self_hosted() {
        assertDoesNotThrow(() -> runValidatorOnApp(false, "", DEFAULT_SSL_ON_8080));
    }

    private static void runValidatorOnApp(boolean hosted, String appTypeAttribute, String serverXml) throws Exception {
        String servicesXml = """
                        <services version='1.0'%s>
                          <container version='1.0'>
                            <http>
                              %s
                            </http>
                          </container>
                        </services>
                """.formatted(appTypeAttribute, serverXml);
        var state = new DeployState.Builder()
                .applicationPackage(
                        new MockApplicationPackage.Builder()
                                .withServices(servicesXml)
                                .build())
                .properties(new TestProperties().setHostedVespa(hosted))
                .build();
        var model = new VespaModel(new NullConfigModelRegistry(), state);
        new CloudHttpConnectorValidator().validate(model, state);
    }

}