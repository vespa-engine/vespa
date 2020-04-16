package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xml.sax.SAXException;

import java.io.IOException;

import static com.yahoo.config.provision.Environment.prod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author gjoranv
 */
public class CloudWatchValidatorTest {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void cloudwatch_in_public_zones_passes_validation() throws IOException, SAXException {
        DeployState deployState = deployState(servicesWithCloudwatch(), true, true);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new CloudWatchValidator().validate(model, deployState);
    }

    @Test
    public void cloudwatch_passes_validation_for_self_hosted_vespa() throws IOException, SAXException {
        DeployState deployState = deployState(servicesWithCloudwatch(), false, false);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new CloudWatchValidator().validate(model, deployState);
    }

    @Test
    public void cloudwatch_in_non_public_zones_fails_validation() throws IOException, SAXException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "CloudWatch cannot be set up for non-public hosted Vespa and must be removed for consumers: [cloudwatch-consumer]");

        DeployState deployState = deployState(servicesWithCloudwatch(), true, false);
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), deployState);

        new CloudWatchValidator().validate(model, deployState);
    }

    private static DeployState deployState(String servicesXml, boolean isHosted, boolean isPublic) {
        ApplicationPackage app = new MockApplicationPackage.Builder()
                .withServices(servicesXml)
                .build();

        DeployState.Builder builder = new DeployState.Builder()
                .applicationPackage(app)
                .properties(new TestProperties().setHostedVespa(isHosted));
        if (isHosted) {
            var system = isPublic ? SystemName.Public : SystemName.main;
            builder.zone(new Zone(system, Environment.prod, RegionName.from("foo")));
        }
        final DeployState deployState = builder.build();

        if (isHosted) {
            assertTrue("Test must emulate a hosted deployment.", deployState.isHosted());
            assertEquals("Test must emulate a prod environment.", prod, deployState.zone().environment());
        }
        return deployState;
    }

    private String servicesWithCloudwatch() {
        return String.join("\n",
                           "<services>",
                           "    <admin version='2.0'>",
                           "        <adminserver hostalias='node1'/>",
                           "        <metrics>",
                           "            <consumer id='cloudwatch-consumer'>",
                           "                <metric id='my-metric'/>",
                           "                <cloudwatch region='us-east-1' namespace='my-namespace' >",
                           "                    <credentials access-key-name='my-access-key' ",
                           "                                 secret-key-name='my-secret-key' />",
                           "                </cloudwatch>",
                           "            </consumer>",
                           "        </metrics>",
                           "    </admin>",
                           "</services>"
        );
    }

}
