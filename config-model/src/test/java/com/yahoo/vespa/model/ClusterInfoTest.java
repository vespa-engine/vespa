package com.yahoo.vespa.model;

import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.TenantName;
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
                                <region>eu-west-1</region>
                              </prod>
                              <bcp deadline='48h'>
                                <group deadline='30m'>
                                  <region fraction='0.5'>us-east-1</region>
                                  <region>us-west-1</region>
                                </group>
                                <group deadline='0m'>
                                  <region fraction='0.5'>us-east-1</region>
                                </group>
                                <group>
                                  <region>eu-west-1</region>
                                </group>
                              </bcp>
                            </deployment>
                            """;

        var requestedInUsEast1 = requestedCapacityIn("default", "us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofMinutes(0), requestedInUsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofMinutes(0), requestedInUsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInUsWest1 = requestedCapacityIn("default", "us-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofMinutes(30), requestedInUsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofMinutes(30), requestedInUsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInEuWest1 = requestedCapacityIn("default", "eu-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInEuWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInEuWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());
    }

    /** A high default deadline only will cause no resources to be set aside for BCP. */
    @Test
    void specifying_only_default_deadline_is_possible() throws Exception {
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
                              <instance id='default'>
                                <prod>
                                  <region>us-east-1</region>
                                  <region>us-west-1</region>
                                </prod>
                              </instance>
                              <bcp deadline='2d'/>
                            </deployment>
                            """;

        var requestedInUsEast1 = requestedCapacityIn("default", "us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInUsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInUsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInUsWest1 = requestedCapacityIn("default", "us-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInUsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInUsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());
    }

    /** This implicitly creates one BCP group (which gives the same result as above). */
    @Test
    void specifying_bcp_without_explicit_groups() throws Exception {
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
                              <instance id='default'>
                                <prod>
                                  <region>us-east-1</region>
                                  <region>us-west-1</region>
                                </prod>
                                <bcp deadline='48h'/>
                              </instance>
                            </deployment>
                            """;

        var requestedInUsEast1 = requestedCapacityIn("default", "us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInUsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInUsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInUsWest1 = requestedCapacityIn("default", "us-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInUsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInUsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());
    }

    @Test
    void default_bcp_with_multiple_instances() throws Exception {
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
                              <instance id='i1'>
                                <prod>
                                  <region>us-east-1</region>
                                  <region>us-west-1</region>
                                </prod>
                              </instance>
                              <instance id='i2'>
                                <prod>
                                  <region>us-east-1</region>
                                  <region>eu-west-1</region>
                                </prod>
                              </instance>
                              <bcp deadline='48h'/>
                            </deployment>
                            """;

        var requestedInI1UsEast1 = requestedCapacityIn("i1", "us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInI1UsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInI1UsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInI1UsWest1 = requestedCapacityIn("i1", "us-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInI1UsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInI1UsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInI2UsEast1 = requestedCapacityIn("i2", "us-east-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInI2UsEast1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInI2UsEast1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());

        var requestedInI2UsWest1 = requestedCapacityIn("i2", "eu-west-1", servicesXml, deploymentXml);
        assertEquals(Duration.ofHours(48), requestedInI2UsWest1.get(new ClusterSpec.Id("testcontainer")).clusterInfo().bcpDeadline());
        assertEquals(Duration.ofHours(48), requestedInI2UsWest1.get(new ClusterSpec.Id("testcontent")).clusterInfo().bcpDeadline());
    }

    private Map<ClusterSpec.Id, Capacity> requestedCapacityIn(String instance, String region, String servicesXml, String deploymentXml) throws Exception {
        var applicationPackage = new MockApplicationPackage.Builder()
                                         .withServices(servicesXml)
                                         .withDeploymentSpec(deploymentXml)
                                         .build();

        var provisioner = new InMemoryProvisioner(10, true);
        var deployState = new DeployState.Builder()
                                  .applicationPackage(applicationPackage)
                                  .zone(new Zone(Environment.prod, RegionName.from(region)))
                                  .properties(new TestProperties().setHostedVespa(true)
                                                                  .setApplicationId(ApplicationId.from(TenantName.defaultName(), ApplicationName.defaultName(), InstanceName.from(instance)))
                                                                  .setZone(new Zone(Environment.prod, RegionName.from(region))))
                                  .modelHostProvisioner(provisioner)
                                  .provisioned(provisioner.provisioned())
                                  .build();
        new VespaModel(new NullConfigModelRegistry(), deployState);
        return deployState.provisioned().all();
    }

}
