// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.ModelBuilderAddingAccessControlFilter;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author mortent
 */
public class AccessControlFilterExcludeValidatorTest {
    private static final String SERVICES_XML = """
            <services version='1.0'>
              <container id='container-cluster-with-access-control' version='1.0'>
                <http>
                  <filtering>
                    <access-control>
                      <exclude>
                        <binding>http://*/foo/</binding>
                      </exclude>
                    </access-control>
                  </filtering>
                </http>
              </container>
            </services>""";


    @Test
    public void validator_rejects_excludes_in_cloud() throws IOException, SAXException {
        DeployState deployState = createDeployState(zone(CloudName.AWS, SystemName.main), new StringBuffer(), false);
        VespaModel model = new VespaModel(
                MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter()),
                deployState);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new AccessControlFilterExcludeValidator().validate(model, deployState));
        String expectedMessage = "Application cluster container-cluster-with-access-control excludes paths from access control, this is not allowed and should be removed.";
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    public void validator_warns_excludes_in_cloud() throws IOException, SAXException {
        StringBuffer logOutput = new StringBuffer();
        DeployState deployState = createDeployState(zone(CloudName.YAHOO, SystemName.main), logOutput, false);
        VespaModel model = new VespaModel(
                MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter()),
                deployState);

        new AccessControlFilterExcludeValidator().validate(model, deployState);
        String expectedMessage = "Application cluster container-cluster-with-access-control excludes paths from access control, this is not allowed and should be removed.";
        assertTrue(logOutput.toString().contains(expectedMessage));
    }

    @Test
    public void validator_accepts_when_allowed_to_exclude() throws IOException, SAXException {
        DeployState deployState = createDeployState(zone(CloudName.AWS, SystemName.main), new StringBuffer(), true);
        VespaModel model = new VespaModel(
                MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter()),
                deployState);
        new AccessControlFilterExcludeValidator().validate(model, deployState);
    }

    @Test
    public void validator_accepts_public_deployments() throws IOException, SAXException {
        DeployState deployState = createDeployState(zone(CloudName.AWS, SystemName.Public), new StringBuffer(), false);
        VespaModel model = new VespaModel(
                MapConfigModelRegistry.createFromList(new ModelBuilderAddingAccessControlFilter()),
                deployState);

        new AccessControlFilterExcludeValidator().validate(model, deployState);
    }

    private static DeployState createDeployState(Zone zone, StringBuffer buffer, boolean allowExcludes) {
        DeployLogger logger = (__, message) -> buffer.append(message).append('\n');
        return new DeployState.Builder()
                .applicationPackage(new MockApplicationPackage.Builder().withServices(SERVICES_XML).build())
                .properties(
                        new TestProperties()
                                .setHostedVespa(true)
                                .setAthenzDomain(AthenzDomain.from("foo.bar"))
                                .allowDisableMtls(allowExcludes))
                .endpoints(Set.of(new ContainerEndpoint("container-cluster-with-access-control", ApplicationClusterEndpoint.Scope.zone, List.of("example.com"))))
                .deployLogger(logger)
                .zone(zone)
                .build();
    }

    private static Zone zone(CloudName cloudName, SystemName systemName) {
        Cloud.Builder cloudBuilder = Cloud.builder().name(cloudName);
        if (cloudName == CloudName.AWS) cloudBuilder.account(CloudAccount.from("123456789012"));
        return new Zone(cloudBuilder.build(), systemName, Environment.prod, RegionName.defaultName());

    }
}
