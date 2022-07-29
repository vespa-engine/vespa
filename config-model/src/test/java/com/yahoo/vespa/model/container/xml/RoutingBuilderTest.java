// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ApplicationContainer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mortent
 */
public class RoutingBuilderTest extends ContainerModelBuilderTestBase {

    @Test
    void setsRotationActiveAccordingToDeploymentSpec() throws IOException, SAXException {
        Element clusterElem = DomBuilderTest.parse(
                "<container id='default' version='1.0'><search /></container>");

        String deploymentSpec = "<deployment>\n" +
                "  <prod>    \n" +
                "    <region active='true'>us-north-1</region>\n" +
                "    <parallel>\n" +
                "      <region active='false'>us-north-2</region>\n" +
                "      <region active='true'>us-north-3</region>\n" +
                "    </parallel>\n" +
                "    <region active='false'>us-north-4</region>\n" +
                "  </prod>\n" +
                "</deployment>";

        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder()
                .withDeploymentSpec(deploymentSpec)
                .build();
        //root = new MockRoot("root", applicationPackage);
        for (String region : Arrays.asList("us-north-1", "us-north-3")) {
            ApplicationContainer container = getContainer(applicationPackage, region, clusterElem);

            assertEquals("true",
                    container.getServicePropertyString("activeRotation"),
                    "Region " + region + " is active");
        }
        for (String region : Arrays.asList("us-north-2", "us-north-4")) {
            ApplicationContainer container = getContainer(applicationPackage, region, clusterElem);

            assertEquals("false",
                    container.getServicePropertyString("activeRotation"),
                    "Region " + region + " is inactive");
        }
        ApplicationContainer container = getContainer(applicationPackage, "unknown", clusterElem);
        assertEquals("false",
                container.getServicePropertyString("activeRotation"),
                "Unknown region is inactive");
    }


    private ApplicationContainer getContainer(ApplicationPackage applicationPackage, String region, Element clusterElem) {
        DeployState deployState = new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .zone(new Zone(Environment.prod, RegionName.from(region)))
                .properties(new TestProperties().setHostedVespa(true))
                .build();

        root = new MockRoot("root", deployState);
        createModel(root, deployState, null, clusterElem);
        ApplicationContainerCluster cluster = getContainerCluster("default");
        return cluster.getContainers().get(0);

    }
}
