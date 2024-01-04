// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class CloudUserFilterValidatorTest {

    @Test
    void fails_on_user_configured_filter_chain() {
        var exception = assertThrows(IllegalArgumentException.class, () -> runValidatorOnApp(true, ""));
        var expected = "HTTP filter chains are currently not supported in Vespa Cloud ([chain 'myChain' in cluster 'container'])";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    void allows_user_configured_filter_chain_for_infrastructure_app() {
        assertDoesNotThrow(() -> runValidatorOnApp(true, " application-type='hosted-infrastructure'"));
    }

    @Test
    void allows_user_configured_filter_chain_for_self_hosted() {
        assertDoesNotThrow(() -> runValidatorOnApp(false, ""));
    }

    private static void runValidatorOnApp(boolean isHosted, String applicationTypeAttribute) throws IOException, SAXException {
        String servicesXml = """
                        <services version='1.0'%s>
                          <container version='1.0'>
                            <http>
                              <filtering>
                                <request-chain id='myChain'>
                                  <filter id='myFilter'/>
                                  <binding>http://*/search/</binding>
                                </request-chain>
                              </filtering>
                            </http>
                          </container>
                        </services>
                """.formatted(applicationTypeAttribute);
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(app)
                .endpoints(Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("container.example.com"))))
                .properties(new TestProperties().setHostedVespa(isHosted).setAllowUserFilters(false))
                .build();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);
        new CloudUserFilterValidator().validate(model, deployState);
    }

}
