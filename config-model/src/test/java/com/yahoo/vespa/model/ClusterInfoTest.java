package com.yahoo.vespa.model;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ClusterInfoTest {

    @Test
    void bcp_deadline_is_passed_in_cluster_info() throws Exception {
        var servicesXml = """
                          <services version='1.0'>
                            <container id='testcontainer' version='1.0'>
                              <nodes count='3'/>
                            </container>
                            <content id='testcontent' version='1.0'>
                              <redundancy>2</redundancy>
                              <documents/>
                            </content>
                          </services>
                          """;

        var deploymentXml = """
                            <deployment version='1.0'>
                              <prod>
                                <region>us-west-1</region>
                                <region>us-east-1</region>
                              </prod>
                              <bcp>
                                <group deadline='30m'>
                                  <region fraction='0.5'>us-east-1</region>
                                  <region>us-west-1</region>
                                </group>
                                <group>
                                  <region fraction='0.5'>us-east-1</region>
                                </group>
                              </bcp>
                            </deployment>
                            """;

        var requestedInUsEast1 = requestedCapacityIn("us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofMinutes(0), requestedInUsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofMinutes(0), requestedInUsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInUsWest1 = requestedCapacityIn("us-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofMinutes(30), requestedInUsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofMinutes(30), requestedInUsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());
    }

    private Map<ClusterSpec.Id, Capacity> requestedCapacityIn(String region, String servicesXml, String deploymentXml) throws Exception {
        var applicationPackage = new MockApplicationPackage.Builder()
                                         .withServices(servicesXml)
                                         .withDeploymentSpec(deploymentXml)
                                         .build();

        var provisioner = new InMemoryProvisioner(10, true);
        var deployState = new DeployState.Builder()
                                  .applicationPackage(applicationPackage)
                                  .zone(new Zone(Environment.prod, RegionName.from(region)))
                                  .properties(new TestProperties().setHostedVespa(true)
                                                                  .setZone(new Zone(Environment.prod, RegionName.from(region))))
                                  .modelHostProvisioner(provisioner)
                                  .provisioned(provisioner.provisioned())
                                  .build();
        new VespaModel(new NullConfigModelRegistry(), deployState);
        return deployState.provisioned().all();
    }

}
