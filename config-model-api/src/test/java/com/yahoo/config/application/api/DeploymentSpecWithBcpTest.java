package com.yahoo.config.application.api;

import com.yahoo.config.provision.RegionName;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.StringReader;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class DeploymentSpecWithBcpTest {

    @Test
    public void minimalProductionSpecWithExplicitBcp() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <instance id='default'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-west1</region>
                      </prod>
                   </instance>
                   <bcp>
                     <group>
                       <region>us-east1</region>
                       <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
        assertTwoRegions(DeploymentSpec.fromXml(r));
    }

    @Test
    public void specWithoutInstanceWithBcp() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-east1</region>
                      <region>us-west1</region>
                   </prod>
                   <bcp>
                     <group>
                       <region>us-east1</region>
                       <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
        assertTwoRegions(DeploymentSpec.fromXml(r));
    }

    @Test
    public void complexBcpSetup() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <instance id='beta'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                      </prod>
                      <bcp>
                         <group deadline="60m">
                            <region>us-east1</region>
                            <region>us-east2</region>
                         </group>
                      </bcp>
                   </instance>
                   <instance id='main'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                         <region>us-central1</region>
                         <region>us-west1</region>
                         <region>us-west2</region>
                         <region>eu-east1</region>
                         <region>eu-west1</region>
                      </prod>
                   </instance>
                   <bcp>
                     <group>
                        <region>us-east1</region>
                        <region>us-east2</region>
                        <region fraction="0.3">us-central1</region>
                     </group>
                     <group>
                        <region>us-west1</region>
                        <region>us-west2</region>
                        <region fraction="0.7">us-central1</region>
                     </group>
                     <group deadline="30m">
                        <region>eu-east1</region>
                        <region>eu-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);

        var spec = DeploymentSpec.fromXml(r);

        var betaBcp = spec.requireInstance("beta").bcp();
        assertEquals(1, betaBcp.groups().size());
        var betaGroup = betaBcp.groups().get(0);
        assertEquals(2, betaGroup.members().size());
        assertEquals(Duration.ofMinutes(60), betaGroup.deadline());
        assertEquals(new Bcp.RegionMember(RegionName.from("us-east1"), 1.0), betaGroup.members().get(0));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-east2"), 1.0), betaGroup.members().get(1));

        var mainBcp = spec.requireInstance("main").bcp();
        assertEquals(7, mainBcp.regions().size());
        assertEquals(3, mainBcp.groups().size());

        var usEast = mainBcp.groups().get(0);
        assertEquals(3, usEast.members().size());
        assertEquals(Duration.ofMinutes(0), usEast.deadline());
        assertEquals(new Bcp.RegionMember(RegionName.from("us-east1"), 1.0), usEast.members().get(0));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-east2"), 1.0), usEast.members().get(1));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-central1"), 0.3), usEast.members().get(2));

        var usWest = mainBcp.groups().get(1);
        assertEquals(3, usWest.members().size());
        assertEquals(Duration.ofMinutes(0), usWest.deadline());
        assertEquals(new Bcp.RegionMember(RegionName.from("us-west1"), 1.0), usWest.members().get(0));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-west2"), 1.0), usWest.members().get(1));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-central1"), 0.7), usWest.members().get(2));

        var eu = mainBcp.groups().get(2);
        assertEquals(2, eu.members().size());
        assertEquals(Duration.ofMinutes(30), eu.deadline());
        assertEquals(new Bcp.RegionMember(RegionName.from("eu-east1"), 1.0), eu.members().get(0));
        assertEquals(new Bcp.RegionMember(RegionName.from("eu-west1"), 1.0), eu.members().get(1));
    }

    @Test
    public void regionMembershipMatchValidation1() {
        try {
            StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-east1</region>
                      <region>us-west1</region>
                   </prod>
                   <bcp>
                     <group>
                       <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
            DeploymentSpec.fromXml(r);
            fail();
        }
        catch (IllegalArgumentException e) {
            assertEquals("BCP and deployment mismatch in instance 'default': " +
                         "A <bcp> element must place all deployed production regions in at least one group, " +
                         "and declare no extra regions. " +
                         "Deployed regions: [us-east1, us-west1]. BCP regions: [us-west1]",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void regionMembershipMatchValidation2() {
        try {
            StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-west1</region>
                   </prod>
                   <bcp>
                     <group>
                        <region>us-east1</region>
                        <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
            DeploymentSpec.fromXml(r);
            fail();
        }
        catch (IllegalArgumentException e) {
            assertEquals("BCP and deployment mismatch in instance 'default': " +
                         "A <bcp> element must place all deployed production regions in at least one group, " +
                         "and declare no extra regions. " +
                         "Deployed regions: [us-west1]. BCP regions: [us-east1, us-west1]",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void deadlineValidation() {
        try {
            StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-east1</region>
                      <region>us-west1</region>
                   </prod>
                   <bcp>
                     <group deadline="fast">
                        <region>us-east1</region>
                        <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
            DeploymentSpec.fromXml(r);
            fail();
        }
        catch (IllegalArgumentException e) {
            assertEquals("Illegal deadline 'fast': Must be an integer followed by 'm', 'h' or 'd'", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void fractionalMembershipValidation() {
        try {
            StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-east1</region>
                      <region>us-west1</region>
                   </prod>
                   <bcp>
                     <group>
                        <region fraction="0.9">us-east1</region>
                        <region>us-west1</region>
                     </group>
                   </bcp>
                </deployment>
            """);
            DeploymentSpec.fromXml(r);
            fail();
        }
        catch (IllegalArgumentException e) {
            assertEquals("Illegal BCP spec: All regions must have total membership fractions summing to 1.0, but us-east1 sums to 0.9",
                         Exceptions.toMessageString(e));
        }
    }

    @Test
    public void endpointsDefinedInBcp() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <instance id='beta'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                      </prod>
                      <bcp>
                         <group>
                            <endpoint id="foo" container-id="bar"/>
                            <region>us-east1</region>
                            <region>us-east2</region>
                         </group>
                      </bcp>
                   </instance>
                   <instance id='main'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                         <region>us-central1</region>
                         <region>us-west1</region>
                         <region>us-west2</region>
                      </prod>
                     <bcp>
                       <group>
                          <endpoint id="east" container-id="bar"/>
                          <region>us-east1</region>
                          <region>us-east2</region>
                          <region fraction="0.3">us-central1</region>
                       </group>
                       <group>
                          <endpoint id="west" container-id="bar"/>
                          <region>us-west1</region>
                          <region>us-west2</region>
                          <region fraction="0.7">us-central1</region>
                       </group>
                     </bcp>
                   </instance>
                </deployment>
            """);

        var spec = DeploymentSpec.fromXml(r);

        var betaEndpoints = spec.requireInstance("beta").endpoints();
        assertEquals(1, betaEndpoints.size());
        assertEquals("foo", betaEndpoints.get(0).endpointId());
        assertEquals("bar", betaEndpoints.get(0).containerId());
        assertEquals(List.of(RegionName.from("us-east1"), RegionName.from("us-east2")),
                     betaEndpoints.get(0).regions());

        var mainEndpoints = spec.requireInstance("main").endpoints();
        assertEquals(2, mainEndpoints.size());
        assertEquals("east", mainEndpoints.get(0).endpointId());
        assertEquals(List.of(RegionName.from("us-east1"), RegionName.from("us-east2"), RegionName.from("us-central1")),
                     mainEndpoints.get(0).regions());
        assertEquals("west", mainEndpoints.get(1).endpointId());
        assertEquals(List.of(RegionName.from("us-west1"), RegionName.from("us-west2"), RegionName.from("us-central1")),
                     mainEndpoints.get(1).regions());
    }

    @Test
    public void endpointsDefinedInBcpImplicitInstance() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <prod>
                      <region>us-east1</region>
                      <region>us-east2</region>
                      <region>us-central1</region>
                      <region>us-west1</region>
                      <region>us-west2</region>
                   </prod>
                  <bcp>
                    <group>
                       <endpoint id="east" container-id="bar"/>
                       <region>us-east1</region>
                       <region>us-east2</region>
                       <region fraction="0.3">us-central1</region>
                    </group>
                    <group>
                       <endpoint id="west" container-id="bar"/>
                       <region>us-west1</region>
                       <region>us-west2</region>
                       <region fraction="0.7">us-central1</region>
                    </group>
                  </bcp>
                </deployment>
            """);

        var spec = DeploymentSpec.fromXml(r);

        var mainEndpoints = spec.requireInstance("default").endpoints();
        assertEquals(2, mainEndpoints.size());
        assertEquals("east", mainEndpoints.get(0).endpointId());
        assertEquals(List.of(RegionName.from("us-east1"), RegionName.from("us-east2"), RegionName.from("us-central1")),
                     mainEndpoints.get(0).regions());
        assertEquals("west", mainEndpoints.get(1).endpointId());
        assertEquals(List.of(RegionName.from("us-west1"), RegionName.from("us-west2"), RegionName.from("us-central1")),
                     mainEndpoints.get(1).regions());
    }

    @Test
    public void endpointsDefinedInBcpValidation1() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <instance id='beta'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                      </prod>
                   </instance>
                   <bcp>
                      <group>
                         <endpoint id="foo" container-id="bar"/>
                         <region>us-east1</region>
                         <region>us-east2</region>
                      </group>
                   </bcp>
                </deployment>
            """);
        try {
            DeploymentSpec.fromXml(r);
        }
        catch (IllegalArgumentException e) {
            assertEquals("The default <bcp> element at the root cannot define endpoints", Exceptions.toMessageString(e));
        }
    }

    @Test
    public void endpointsDefinedInBcpValidation2() {
        StringReader r = new StringReader("""
                <deployment version='1.0'>
                   <instance id='beta'>
                      <prod>
                         <region>us-east1</region>
                         <region>us-east2</region>
                      </prod>
                      <bcp>
                         <group>
                            <region>us-east1</region>
                            <region>us-east2</region>
                            <endpoint id="foo" container-id="bar">
                               <region>us-east1</region>
                            </endpoint>
                         </group>
                      </bcp>
                   </instance>
                </deployment>
            """);
        try {
            DeploymentSpec.fromXml(r);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Endpoints in <group> cannot contain <region> children", Exceptions.toMessageString(e));
        }

    }
    private void assertTwoRegions(DeploymentSpec spec) {
        var bcp = spec.requireInstance("default").bcp();
        assertEquals(1, bcp.groups().size());
        var group = bcp.groups().get(0);
        assertEquals(2, group.members().size());
        assertEquals(Duration.ZERO, group.deadline());
        assertEquals(new Bcp.RegionMember(RegionName.from("us-east1"), 1.0), group.members().get(0));
        assertEquals(new Bcp.RegionMember(RegionName.from("us-west1"), 1.0), group.members().get(1));
    }

}
