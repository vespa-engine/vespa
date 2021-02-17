// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.application.api.Notifications.Role.author;
import static com.yahoo.config.application.api.Notifications.When.failing;
import static com.yahoo.config.application.api.Notifications.When.failingCommit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class DeploymentSpecTest {

    @Test
    public void testSpec() {
        String specXml = "<deployment version='1.0'>" +
                         "   <instance id='default'>" +
                         "      <test/>" +
                         "   </instance>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.requireInstance("default").steps().size());
        assertFalse(spec.majorVersion().isPresent());
        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.test));
        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.test, Optional.of(RegionName.from("region1")))); // test steps specify no region
        assertFalse(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.empty()));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
    }

    @Test
    public void testSpecPinningMajorVersion() {
        String specXml = "<deployment version='1.0' major-version='6'>" +
                         "   <instance id='default'>" +
                         "      <test/>" +
                         "   </instance>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.requireInstance("default").steps().size());
        assertTrue(spec.majorVersion().isPresent());
        assertEquals(6, (int)spec.majorVersion().get());
    }

    @Test
    public void stagingSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <instance id='default'>" +
        "      <staging/>" +
        "   </instance>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(1, spec.steps().size());
        assertEquals(1, spec.requireInstance("default").steps().size());
        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.staging));
        assertFalse(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.empty()));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());
    }

    @Test
    public void minimalProductionSpec() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(1, spec.steps().size());
        assertEquals(2, spec.requireInstance("default").steps().size());

        assertTrue(spec.requireInstance("default").steps().get(0).concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(0)).active());

        assertTrue(spec.requireInstance("default").steps().get(1).concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)spec.requireInstance("default").steps().get(1)).active());

        assertFalse(spec.requireInstance("default").concerns(Environment.test, Optional.empty()));
        assertFalse(spec.requireInstance("default").concerns(Environment.staging, Optional.empty()));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(spec.requireInstance("default").concerns(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(spec.requireInstance("default").globalServiceId().isPresent());

        assertEquals(DeploymentSpec.UpgradePolicy.defaultPolicy, spec.requireInstance("default").upgradePolicy());
    }

    @Test
    public void maximalProductionSpec() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" + // The block checked by assertCorrectFirstInstance
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <delay hours='3' minutes='30'/>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertCorrectFirstInstance(spec.requireInstance("default"));
    }

    @Test
    public void productionTests() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='false'>us-east-1</region>" +
                "         <region active='true'>us-west-1</region>" +
                "         <delay hours='1' />" +
                "         <test>us-west-1</test>" +
                "         <test>us-east-1</test>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> instanceSteps = spec.steps().get(0).steps();
        assertEquals(7, instanceSteps.size());
        assertEquals("test", instanceSteps.get(0).toString());
        assertEquals("staging", instanceSteps.get(1).toString());
        assertEquals("prod.us-east-1", instanceSteps.get(2).toString());
        assertEquals("prod.us-west-1", instanceSteps.get(3).toString());
        assertEquals("delay PT1H", instanceSteps.get(4).toString());
        assertEquals("tests for prod.us-west-1", instanceSteps.get(5).toString());
        assertEquals("tests for prod.us-east-1", instanceSteps.get(6).toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateProductionTest() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <region active='true'>us-east1</region>" +
                "         <test>us-east1</test>" +
                "         <test>us-east1</test>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void productionTestBeforeDeployment() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <test>us-east1</test>" +
                "         <region active='true'>us-east1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void productionTestInParallelWithDeployment() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <parallel>" +
                "            <region active='true'>us-east1</region>" +
                "            <test>us-east1</test>" +
                "         </parallel>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
         DeploymentSpec.fromXml(r);
    }

    @Test
    public void maximalProductionSpecMultipleInstances() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='instance1'>" + // The block checked by assertCorrectFirstInstance
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <delay hours='3' minutes='30'/>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "   <instance id='instance2'>" +
                "      <prod>" +
                "         <region active='true'>us-central1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);

        assertCorrectFirstInstance(spec.requireInstance("instance1"));

        DeploymentInstanceSpec instance2 = spec.requireInstance("instance2");
        assertEquals(1, instance2.steps().size());
        assertEquals(1, instance2.zones().size());

        assertTrue(instance2.steps().get(0).concerns(Environment.prod, Optional.of(RegionName.from("us-central1"))));
    }

    @Test
    public void testMultipleInstancesShortForm() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='instance1, instance2'>" + // The block checked by assertCorrectFirstInstance
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <delay hours='3' minutes='30'/>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);

        assertCorrectFirstInstance(spec.requireInstance("instance1"));
        assertCorrectFirstInstance(spec.requireInstance("instance2"));
    }

    private void assertCorrectFirstInstance(DeploymentInstanceSpec instance) {
        assertEquals(5, instance.steps().size());
        assertEquals(4, instance.zones().size());

        assertTrue(instance.steps().get(0).concerns(Environment.test));

        assertTrue(instance.steps().get(1).concerns(Environment.staging));

        assertTrue(instance.steps().get(2).concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)instance.steps().get(2)).active());

        assertTrue(instance.steps().get(3) instanceof DeploymentSpec.Delay);
        assertEquals(3 * 60 * 60 + 30 * 60, instance.steps().get(3).delay().getSeconds());

        assertTrue(instance.steps().get(4).concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)instance.steps().get(4)).active());

        assertTrue(instance.concerns(Environment.test, Optional.empty()));
        assertTrue(instance.concerns(Environment.test, Optional.of(RegionName.from("region1")))); // test steps specify no region
        assertTrue(instance.concerns(Environment.staging, Optional.empty()));
        assertTrue(instance.concerns(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(instance.concerns(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(instance.concerns(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(instance.globalServiceId().isPresent());
    }

    @Test
    public void productionSpecWithGlobalServiceId() {
        StringReader r = new StringReader(
            "<deployment version='1.0'>" +
            "   <instance id='default'>" +
            "      <prod global-service-id='query'>" +
            "         <region active='true'>us-east-1</region>" +
            "         <region active='true'>us-west-1</region>" +
            "      </prod>" +
            "   </instance>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.requireInstance("default").globalServiceId(), Optional.of("query"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInTest() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <test global-service-id='query' />" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInStaging() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='default'>" +
                "      <staging global-service-id='query' />" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void productionSpecWithGlobalServiceIdBeforeStaging() {
        StringReader r = new StringReader(
            "<deployment>" +
            "   <instance id='default'>" +
            "      <test/>" +
            "      <prod global-service-id='qrs'>" +
            "         <region active='true'>us-west-1</region>" +
            "         <region active='true'>us-central-1</region>" +
            "         <region active='true'>us-east-3</region>" +
            "      </prod>" +
            "      <staging/>" +
            "   </instance>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("qrs", spec.requireInstance("default").globalServiceId().get());
    }

    @Test
    public void productionSpecWithUpgradePolicy() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <upgrade policy='canary'/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "         <region active='true'>us-central-1</region>" +
                "         <region active='true'>us-east-3</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.requireInstance("default").upgradePolicy().toString());
    }

    @Test
    public void upgradePolicyDefault() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <upgrade policy='canary'/>" +
                "   <instance id='instance1'>" +
                "   </instance>" +
                "   <instance id='instance2'>" +
                "      <upgrade policy='conservative'/>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.requireInstance("instance1").upgradePolicy().toString());
        assertEquals("conservative", spec.requireInstance("instance2").upgradePolicy().toString());
    }

    @Test
    public void maxDelayExceeded() {
        try {
            StringReader r = new StringReader(
                    "<deployment>" +
                    "   <instance id='default'>" +
                    "      <upgrade policy='canary'/>" +
                    "      <prod>" +
                    "         <region active='true'>us-west-1</region>" +
                    "         <delay hours='47'/>" +
                    "         <region active='true'>us-central-1</region>" +
                    "         <delay minutes='59' seconds='61'/>" +
                    "         <region active='true'>us-east-3</region>" +
                    "      </prod>" +
                    "   </instance>" +
                    "</deployment>"
            );
            DeploymentSpec.fromXml(r);
            fail("Expected exception due to exceeding the max total delay");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("The total delay specified is PT48H1S but max 48 hours is allowed", e.getMessage());
        }
    }

    @Test
    public void testOnlyAthenzServiceDefinedInInstance() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>" +
                "  <instance id='default' athenz-service='service' />" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);

        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals(1, spec.instances().size());

        DeploymentInstanceSpec instance = spec.instances().get(0);
        assertEquals("default", instance.name().value());
        assertEquals("service", instance.athenzService(Environment.prod, RegionName.defaultName()).get().value());
    }

    @Test
    public void productionSpecWithParallelDeployments() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "         <parallel>" +
                "            <region active='true'>us-central-1</region>" +
                "            <region active='true'>us-east-3</region>" +
                "         </parallel>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentSpec.ParallelSteps parallelSteps = ((DeploymentSpec.ParallelSteps) spec.requireInstance("default").steps().get(1));
        assertEquals(2, parallelSteps.zones().size());
        assertEquals(RegionName.from("us-central-1"), parallelSteps.zones().get(0).region().get());
        assertEquals(RegionName.from("us-east-3"), parallelSteps.zones().get(1).region().get());
    }

    @Test
    public void testTestAndStagingOutsideAndInsideInstance() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <test/>" +
                "   <staging/>" +
                "   <instance id='instance0'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "   <instance id='instance1'>" +
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> steps = spec.steps();
        assertEquals(4, steps.size());
        assertEquals("test", steps.get(0).toString());
        assertEquals("staging", steps.get(1).toString());
        assertEquals("instance 'instance0'", steps.get(2).toString());
        assertEquals("instance 'instance1'", steps.get(3).toString());

        List<DeploymentSpec.Step> instance0Steps = ((DeploymentInstanceSpec)steps.get(2)).steps();
        assertEquals(1, instance0Steps.size());
        assertEquals("prod.us-west-1", instance0Steps.get(0).toString());

        List<DeploymentSpec.Step> instance1Steps = ((DeploymentInstanceSpec)steps.get(3)).steps();
        assertEquals(3, instance1Steps.size());
        assertEquals("test", instance1Steps.get(0).toString());
        assertEquals("staging", instance1Steps.get(1).toString());
        assertEquals("prod.us-west-1", instance1Steps.get(2).toString());
    }

    @Test
    public void testNestedParallelAndSteps() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>" +
                "   <staging />" +
                "   <instance id='instance' athenz-service='in-service'>" +
                "      <prod>" +
                "         <parallel>" +
                "            <region active='true'>us-west-1</region>" +
                "            <steps>" +
                "               <region active='true'>us-east-3</region>" +
                "               <delay hours='2' />" +
                "               <region active='true'>eu-west-1</region>" +
                "               <delay hours='2' />" +
                "            </steps>" +
                "            <steps>" +
                "               <delay hours='3' />" +
                "               <region active='true'>aws-us-east-1a</region>" +
                "               <parallel>" +
                "                  <region active='true' athenz-service='no-service'>ap-northeast-1</region>" +
                "                  <region active='true'>ap-southeast-2</region>" +
                "                  <test>aws-us-east-1a</test>" +
                "               </parallel>" +
                "            </steps>" +
                "            <delay hours='3' minutes='30' />" +
                "         </parallel>" +
                "         <region active='true'>us-north-7</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> steps = spec.steps();
        assertEquals(2, steps.size());
        assertEquals("staging", steps.get(0).toString());
        assertEquals("instance 'instance'", steps.get(1).toString());
        assertEquals(Duration.ofHours(4), steps.get(1).delay());

        List<DeploymentSpec.Step> instanceSteps = steps.get(1).steps();
        assertEquals(2, instanceSteps.size());
        assertEquals("4 parallel steps", instanceSteps.get(0).toString());
        assertEquals("prod.us-north-7", instanceSteps.get(1).toString());

        List<DeploymentSpec.Step> parallelSteps = instanceSteps.get(0).steps();
        assertEquals(4, parallelSteps.size());
        assertEquals("prod.us-west-1", parallelSteps.get(0).toString());
        assertEquals("4 steps", parallelSteps.get(1).toString());
        assertEquals("3 steps", parallelSteps.get(2).toString());
        assertEquals("delay PT3H30M", parallelSteps.get(3).toString());

        List<DeploymentSpec.Step> firstSerialSteps = parallelSteps.get(1).steps();
        assertEquals(4, firstSerialSteps.size());
        assertEquals("prod.us-east-3", firstSerialSteps.get(0).toString());
        assertEquals("delay PT2H", firstSerialSteps.get(1).toString());
        assertEquals("prod.eu-west-1", firstSerialSteps.get(2).toString());
        assertEquals("delay PT2H", firstSerialSteps.get(3).toString());

        List<DeploymentSpec.Step> secondSerialSteps = parallelSteps.get(2).steps();
        assertEquals(3, secondSerialSteps.size());
        assertEquals("delay PT3H", secondSerialSteps.get(0).toString());
        assertEquals("prod.aws-us-east-1a", secondSerialSteps.get(1).toString());
        assertEquals("3 parallel steps", secondSerialSteps.get(2).toString());

        List<DeploymentSpec.Step> innerParallelSteps = secondSerialSteps.get(2).steps();
        assertEquals(3, innerParallelSteps.size());
        assertEquals("prod.ap-northeast-1", innerParallelSteps.get(0).toString());
        assertEquals("no-service", spec.requireInstance("instance").athenzService(Environment.prod, RegionName.from("ap-northeast-1")).get().value());
        assertEquals("prod.ap-southeast-2", innerParallelSteps.get(1).toString());
        assertEquals("in-service", spec.requireInstance("instance").athenzService(Environment.prod, RegionName.from("ap-southeast-2")).get().value());
        assertEquals("tests for prod.aws-us-east-1a", innerParallelSteps.get(2).toString());
    }

    @Test
    public void testParallelInstances() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <parallel>" +
                "      <instance id='instance0'>" +
                "         <prod>" +
                "            <region active='true'>us-west-1</region>" +
                "         </prod>" +
                "      </instance>" +
                "      <instance id='instance1'>" +
                "         <prod>" +
                "            <region active='true'>us-east-3</region>" +
                "         </prod>" +
                "      </instance>" +
                "   </parallel>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> steps = spec.steps();
        assertEquals(1, steps.size());
        assertEquals("2 parallel steps", steps.get(0).toString());

        List<DeploymentSpec.Step> parallelSteps = steps.get(0).steps();
        assertEquals("instance 'instance0'", parallelSteps.get(0).toString());
        assertEquals("instance 'instance1'", parallelSteps.get(1).toString());
    }

    @Test
    public void testInstancesWithDelay() {
        StringReader r = new StringReader(
                "<deployment>" +
                "    <instance id='instance0'>" +
                "       <prod>" +
                "          <region active='true'>us-west-1</region>" +
                "       </prod>" +
                "    </instance>" +
                "    <delay hours='12'/>" +
                "    <instance id='instance1'>" +
                "       <prod>" +
                "          <region active='true'>us-east-3</region>" +
                "       </prod>" +
                "    </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        List<DeploymentSpec.Step> steps = spec.steps();
        assertEquals(3, steps.size());
        assertEquals("instance 'instance0'", steps.get(0).toString());
        assertEquals("delay PT12H", steps.get(1).toString());
        assertEquals("instance 'instance1'", steps.get(2).toString());
    }

    @Test
    public void productionSpecWithDuplicateRegions() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "         <parallel>" +
                "            <region active='true'>us-west-1</region>" +
                "            <region active='true'>us-central-1</region>" +
                "            <region active='true'>us-east-3</region>" +
                "         </parallel>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        try {
            DeploymentSpec.fromXml(r);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertEquals("prod.us-west-1 is listed twice in deployment.xml", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIncreasinglyStrictUpgradePolicies() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='instance1'>" +
                "      <upgrade policy='conservative'/>" +
                "   </instance>" +
                "   <instance id='instance2' />" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIncreasinglyStrictUpgradePoliciesInParallel() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "  <instance id='instance0'/>" +
                "  <parallel>" +
                "     <instance id='instance1'>" +
                "        <upgrade policy='conservative'/>" +
                "     </instance>" +
                "     <instance id='instance2'>" +
                "        <upgrade policy='canary'/>" +
                "     </instance>" +
                "  </parallel>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIncreasinglyStrictUpgradePoliciesAfterParallel() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "  <parallel>" +
                "     <instance id='instance1'>" +
                "        <upgrade policy='conservative'/>" +
                "     </instance>" +
                "     <instance id='instance2'>" +
                "        <upgrade policy='canary'/>" +
                "     </instance>" +
                "  </parallel>" +
                "  <instance id='instance3'/>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void deploymentSpecWithDifferentUpgradePoliciesInParallel() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "  <parallel>" +
                "     <instance id='instance1'>" +
                "        <upgrade policy='conservative'/>" +
                "     </instance>" +
                "     <instance id='instance2' />" +
                "  </parallel>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(DeploymentSpec.UpgradePolicy.conservative, spec.requireInstance("instance1").upgradePolicy());
        assertEquals(DeploymentSpec.UpgradePolicy.defaultPolicy, spec.requireInstance("instance2").upgradePolicy());
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec1() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <block-change days='sat' hours='10' time-zone='CET'/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "      <block-change days='mon,tue' hours='15-16'/>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec2() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "   <instance id='default'>" +
                "      <block-change days='sat' hours='10' time-zone='CET'/>" +
                "      <test/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void deploymentSpecWithChangeBlocker() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <block-change revision='false' days='mon,tue' hours='15-16'/>" +
                "      <block-change days='sat' hours='10' time-zone='CET'/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(2, spec.requireInstance("default").changeBlocker().size());
        assertTrue(spec.requireInstance("default").changeBlocker().get(0).blocksVersions());
        assertFalse(spec.requireInstance("default").changeBlocker().get(0).blocksRevisions());
        assertEquals(ZoneId.of("UTC"), spec.requireInstance("default").changeBlocker().get(0).window().zone());

        assertTrue(spec.requireInstance("default").changeBlocker().get(1).blocksVersions());
        assertTrue(spec.requireInstance("default").changeBlocker().get(1).blocksRevisions());
        assertEquals(ZoneId.of("CET"), spec.requireInstance("default").changeBlocker().get(1).window().zone());

        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T14:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T15:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T16:15:30.00Z")));
        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-18T17:15:30.00Z")));

        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T09:15:30.00Z")));
        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T08:15:30.00Z"))); // 10 in CET
        assertTrue(spec.requireInstance("default").canUpgradeAt(Instant.parse("2017-09-23T10:15:30.00Z")));
    }

    @Test
    public void testChangeBlockerInheritance() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <block-change revision='false' days='mon,tue' hours='15-16'/>" +
                "   <instance id='instance1'>" +
                "      <block-change days='sat' hours='10' time-zone='CET'/>" +
                "   </instance>" +
                "   <instance id='instance2'>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);

        String inheritedChangeBlocker = "change blocker revision=false version=true window=time window for hour(s) [15, 16] on [monday, tuesday] in UTC";

        assertEquals(2, spec.requireInstance("instance1").changeBlocker().size());
        assertEquals(inheritedChangeBlocker, spec.requireInstance("instance1").changeBlocker().get(0).toString());
        assertEquals("change blocker revision=true version=true window=time window for hour(s) [10] on [saturday] in CET",
                     spec.requireInstance("instance1").changeBlocker().get(1).toString());

        assertEquals(1, spec.requireInstance("instance2").changeBlocker().size());
        assertEquals(inheritedChangeBlocker, spec.requireInstance("instance2").changeBlocker().get(0).toString());
    }

    @Test
    public void athenz_config_is_read_from_deployment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "   <instance id='instance1'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.athenzService().get().value());
        assertEquals("service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                RegionName.from("us-west-1")).get().value());
    }

    @Test
    public void athenz_config_is_propagated_through_parallel_zones() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "   <instance id='instance1'>" +
                "      <prod athenz-service='prod-service'>" +
                "         <region active='true'>us-central-1</region>" +
                "         <parallel>" +
                "            <region active='true'>us-west-1</region>" +
                "            <region active='true'>us-east-3</region>" +
                "         </parallel>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.athenzService().get().value());

        assertEquals("prod-service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                     RegionName.from("us-central-1")).get().value());
        assertEquals("prod-service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                     RegionName.from("us-west-1")).get().value());
        assertEquals("prod-service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                     RegionName.from("us-east-3")).get().value());
    }

    @Test
    public void athenz_config_is_propagated_through_parallel_zones_and_instances() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>" +
                "   <parallel>" +
                "      <instance id='instance1'>" +
                "         <prod>" +
                "            <parallel>" +
                "               <region active='true'>us-west-1</region>" +
                "               <region active='true'>us-east-3</region>" +
                "            </parallel>" +
                "         </prod>" +
                "      </instance>" +
                "      <instance id='instance2'>" +
                "         <prod>" +
                "            <parallel>" +
                "               <region active='true'>us-west-1</region>" +
                "               <region active='true'>us-east-3</region>" +
                "            </parallel>" +
                "         </prod>" +
                "      </instance>" +
                "   </parallel>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals("service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                RegionName.from("us-west-1")).get().value());
        assertEquals("service", spec.requireInstance("instance1").athenzService(Environment.prod,
                                                                                RegionName.from("us-east-3")).get().value());
        assertEquals("service", spec.requireInstance("instance2").athenzService(Environment.prod,
                                                                                RegionName.from("us-east-3")).get().value());
    }

    @Test
    public void athenz_config_is_read_from_instance() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>" +
                "   <instance id='default' athenz-service='service'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("domain", spec.athenzDomain().get().value());
        assertEquals(Optional.empty(), spec.athenzService());
        assertEquals("service", spec.requireInstance("default").athenzService(Environment.prod, RegionName.from("us-west-1")).get().value());
    }

    @Test
    public void athenz_service_is_overridden_from_environment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='unused-service'>" +
                "   <instance id='default' athenz-service='service'>" +
                "      <test />" +
                "      <staging athenz-service='staging-service' />" +
                "      <prod athenz-service='prod-service'>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("service",
                     spec.requireInstance("default").athenzService(Environment.test,
                                                                   RegionName.from("us-east-1")).get().value());
        assertEquals("staging-service",
                     spec.requireInstance("default").athenzService(Environment.staging,
                                                                   RegionName.from("us-north-1")).get().value());
        assertEquals("prod-service",
                     spec.requireInstance("default").athenzService(Environment.prod,
                                                                   RegionName.from("us-west-1")).get().value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_not_defined() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>" +
                "   <instance id='default'>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_configured_but_not_athenz_domain() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <prod athenz-service='service'>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec.fromXml(r);
    }

    @Test
    public void noNotifications() {
        assertEquals(Notifications.none(),
                     DeploymentSpec.fromXml("<deployment>" +
                                            "   <instance id='default'/>" +
                                            "</deployment>").requireInstance("default").notifications());
    }

    @Test
    public void emptyNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>" +
                                                     "   <instance id='default'>" +
                                                     "      <notifications/>" +
                                                     "   </instance>" +
                                                     "</deployment>");
        assertEquals(Notifications.none(), spec.requireInstance("default").notifications());
    }

    @Test
    public void someNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "   <instance id='default'>" +
                                                     "      <notifications when=\"failing\">" +
                                                     "         <email role=\"author\"/>" +
                                                     "         <email address=\"john@dev\" when=\"failing-commit\"/>" +
                                                     "         <email address=\"jane@dev\"/>" +
                                                     "      </notifications>" +
                                                     "   </instance>" +
                                                     "</deployment>");
        assertEquals(ImmutableSet.of(author), spec.requireInstance("default").notifications().emailRolesFor(failing));
        assertEquals(ImmutableSet.of(author), spec.requireInstance("default").notifications().emailRolesFor(failingCommit));
        assertEquals(ImmutableSet.of("john@dev", "jane@dev"), spec.requireInstance("default").notifications().emailAddressesFor(failingCommit));
        assertEquals(ImmutableSet.of("jane@dev"), spec.requireInstance("default").notifications().emailAddressesFor(failing));
    }

    @Test
    public void notificationsWithMultipleInstances() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='instance1'>" +
                "      <notifications when=\"failing\">" +
                "         <email role=\"author\"/>" +
                "         <email address=\"john@operator\"/>" +
                "      </notifications>" +
                "   </instance>" +
                "   <instance id='instance2'>" +
                "      <notifications when=\"failing-commit\">" +
                "         <email role=\"author\"/>" +
                "         <email address=\"mary@dev\"/>" +
                "      </notifications>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentInstanceSpec instance1 = spec.requireInstance("instance1");
        assertEquals(Set.of(author), instance1.notifications().emailRolesFor(failing));
        assertEquals(Set.of("john@operator"), instance1.notifications().emailAddressesFor(failing));

        DeploymentInstanceSpec instance2 = spec.requireInstance("instance2");
        assertEquals(Set.of(author), instance2.notifications().emailRolesFor(failingCommit));
        assertEquals(Set.of("mary@dev"), instance2.notifications().emailAddressesFor(failingCommit));
    }

    @Test
    public void notificationsDefault() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <notifications>" +
                "      <email role=\"author\" when=\"failing\"/>" +
                "      <email address=\"mary@dev\"/>" +
                "   </notifications>" +
                "   <instance id='instance1'>" +
                "      <notifications when=\"failing\">" +
                "         <email role=\"author\"/>" +
                "         <email address=\"john@operator\" when=\"failing-commit\"/>" +
                "      </notifications>" +
                "   </instance>" +
                "   <instance id='instance2'>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentInstanceSpec instance1 = spec.requireInstance("instance1");
        assertEquals(Set.of(author), instance1.notifications().emailRolesFor(failing));
        assertEquals(Set.of(), instance1.notifications().emailAddressesFor(failing));
        assertEquals(Set.of(author), instance1.notifications().emailRolesFor(failingCommit));
        assertEquals(Set.of("john@operator"), instance1.notifications().emailAddressesFor(failingCommit));

        DeploymentInstanceSpec instance2 = spec.requireInstance("instance2");
        assertEquals(Set.of(author), instance2.notifications().emailRolesFor(failing));
        assertEquals(Set.of(), instance2.notifications().emailAddressesFor(failing));
        assertEquals(Set.of(author), instance2.notifications().emailRolesFor(failingCommit));
        assertEquals(Set.of("mary@dev"), instance2.notifications().emailAddressesFor(failingCommit));
    }

    @Test
    public void customTesterFlavor() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>" +
                                                     "   <instance id='default'>" +
                                                     "      <test tester-flavor=\"d-1-4-20\" />" +
                                                     "      <staging />" +
                                                     "      <prod tester-flavor=\"d-2-8-50\">" +
                                                     "         <region active=\"false\">us-north-7</region>" +
                                                     "      </prod>" +
                                                     "   </instance>" +
                                                     "</deployment>");
        assertEquals(Optional.of("d-1-4-20"), spec.requireInstance("default").steps().get(0).zones().get(0).testerFlavor());
        assertEquals(Optional.empty(), spec.requireInstance("default").steps().get(1).zones().get(0).testerFlavor());
        assertEquals(Optional.of("d-2-8-50"), spec.requireInstance("default").steps().get(2).zones().get(0).testerFlavor());
    }

    @Test
    public void noEndpoints() {
        assertEquals(Collections.emptyList(),
                     DeploymentSpec.fromXml("<deployment>" +
                                            "   <instance id='default'/>" +
                                            "</deployment>").requireInstance("default").endpoints());
    }

    @Test
    public void emptyEndpoints() {
        var spec = DeploymentSpec.fromXml("<deployment>" +
                                          "   <instance id='default'>" +
                                          "      <endpoints/>" +
                                          "   </instance>" +
                                          "</deployment>");
        assertEquals(Collections.emptyList(), spec.requireInstance("default").endpoints());
    }

    @Test
    public void someEndpoints() {
        var spec = DeploymentSpec.fromXml("" +
                                          "<deployment>" +
                                          "   <instance id='default'>" +
                                          "      <prod>" +
                                          "         <region active=\"true\">us-east</region>" +
                                          "      </prod>" +
                                          "      <endpoints>" +
                                          "         <endpoint id=\"foo\" container-id=\"bar\">" +
                                          "            <region>us-east</region>" +
                                          "         </endpoint>" +
                                          "         <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                                          "         <endpoint container-id=\"quux\" />" +
                                          "      </endpoints>" +
                                          "   </instance>" +
                                          "</deployment>");

        assertEquals(
                List.of("foo", "nalle", "default"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::endpointId).collect(Collectors.toList())
        );

        assertEquals(
                List.of("bar", "frosk", "quux"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::containerId).collect(Collectors.toList())
        );

        assertEquals(Set.of(RegionName.from("us-east")), spec.requireInstance("default").endpoints().get(0).regions());
    }

    @Test
    public void invalidEndpoints() {
        assertInvalid("<endpoint id='FOO' container-id='qrs'/>"); // Uppercase
        assertInvalid("<endpoint id='123' container-id='qrs'/>"); // Starting with non-character
        assertInvalid("<endpoint id='foo!' container-id='qrs'/>"); // Non-alphanumeric
        assertInvalid("<endpoint id='foo.bar' container-id='qrs'/>");
        assertInvalid("<endpoint id='foo--bar' container-id='qrs'/>"); // Multiple consecutive dashes
        assertInvalid("<endpoint id='foo-' container-id='qrs'/>"); // Trailing dash
        assertInvalid("<endpoint id='foooooooooooo' container-id='qrs'/>"); // Too long
        assertInvalid("<endpoint id='foo' container-id='qrs'/><endpoint id='foo' container-id='qrs'/>"); // Duplicate
    }

    @Test
    public void validEndpoints() {
        assertEquals(List.of("default"), endpointIds("<endpoint container-id='qrs'/>"));
        assertEquals(List.of("default"), endpointIds("<endpoint id='' container-id='qrs'/>"));
        assertEquals(List.of("f"), endpointIds("<endpoint id='f' container-id='qrs'/>"));
        assertEquals(List.of("foo"), endpointIds("<endpoint id='foo' container-id='qrs'/>"));
        assertEquals(List.of("foo-bar"), endpointIds("<endpoint id='foo-bar' container-id='qrs'/>"));
        assertEquals(List.of("foo", "bar"), endpointIds("<endpoint id='foo' container-id='qrs'/><endpoint id='bar' container-id='qrs'/>"));
        assertEquals(List.of("fooooooooooo"), endpointIds("<endpoint id='fooooooooooo' container-id='qrs'/>"));
    }

    @Test
    public void endpointDefaultRegions() {
        var spec = DeploymentSpec.fromXml("" +
                                          "<deployment>" +
                                          "   <instance id='default'>" +
                                          "      <prod>" +
                                          "         <region active=\"true\">us-east</region>" +
                                          "         <region active=\"true\">us-west</region>" +
                                          "      </prod>" +
                                          "      <endpoints>" +
                                          "         <endpoint id=\"foo\" container-id=\"bar\">" +
                                          "            <region>us-east</region>" +
                                          "         </endpoint>" +
                                          "         <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                                          "         <endpoint container-id=\"quux\" />" +
                                          "      </endpoints>" +
                                          "   </instance>" +
                                          "</deployment>");

        assertEquals(Set.of("us-east"), endpointRegions("foo", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("nalle", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("default", spec));
    }

    private static void assertInvalid(String endpointTag) {
        try {
            endpointIds(endpointTag);
            fail("Expected exception for input '" + endpointTag + "'");
        } catch (IllegalArgumentException ignored) {}
    }

    private static Set<String> endpointRegions(String endpointId, DeploymentSpec spec) {
        return spec.requireInstance("default").endpoints().stream()
                .filter(endpoint -> endpoint.endpointId().equals(endpointId))
                .flatMap(endpoint -> endpoint.regions().stream())
                .map(RegionName::value)
                .collect(Collectors.toSet());
    }

    private static List<String> endpointIds(String endpointTag) {
        var xml = "<deployment>" +
                  "   <instance id='default'>" +
                  "      <prod>" +
                  "         <region active=\"true\">us-east</region>" +
                  "      </prod>" +
                  "      <endpoints>" +
                  endpointTag +
                  "      </endpoints>" +
                  "   </instance>" +
                  "</deployment>";

        return DeploymentSpec.fromXml(xml).requireInstance("default").endpoints().stream()
                             .map(Endpoint::endpointId)
                             .collect(Collectors.toList());
    }

}
