// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.application.api.Endpoint.Level;
import com.yahoo.config.application.api.Endpoint.Target;
import com.yahoo.config.application.api.xml.DeploymentSpecXmlReader;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.io.StringReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.application.api.Notifications.Role.author;
import static com.yahoo.config.application.api.Notifications.When.failing;
import static com.yahoo.config.application.api.Notifications.When.failingCommit;
import static com.yahoo.config.provision.zone.ZoneId.defaultId;
import static com.yahoo.config.provision.zone.ZoneId.from;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class DeploymentSpecTest {

    @Test
    public void simpleSpec() {
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
    public void specPinningMajorVersion() {
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
        StringReader r = new StringReader( """
                <deployment version='1.0'>
                   <instance id='default'>
                      <prod>
                         <region active='false'>us-east1</region>
                         <region active='true'>us-west1</region>
                      </prod>
                   </instance>
                </deployment>
            """);

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
        assertEquals(DeploymentSpec.RevisionTarget.latest, spec.requireInstance("default").revisionTarget());
        assertEquals(DeploymentSpec.RevisionChange.whenFailing, spec.requireInstance("default").revisionChange());
        assertEquals(DeploymentSpec.UpgradeRollout.separate, spec.requireInstance("default").upgradeRollout());
        assertEquals(0, spec.requireInstance("default").minRisk());
        assertEquals(0, spec.requireInstance("default").maxRisk());
        assertEquals(8, spec.requireInstance("default").maxIdleHours());
    }

    @Test
    public void specWithTags() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='a' tags='tag1 tag2'>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "   <instance id='b' tags='tag3'>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(Tags.fromString("tag1 tag2"), spec.requireInstance("a").tags());
        assertEquals(Tags.fromString("tag3"), spec.requireInstance("b").tags());
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
    public void multipleInstancesShortForm() {
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
    public void productionSpecWithUpgradeRevisionSettings() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <upgrade revision-change='when-clear' revision-target='next' min-risk='3' max-risk='12' max-idle-hours='32' />" +
                "   </instance>" +
                "   <instance id='custom'>" +
                "      <upgrade revision-change='always' />" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("next", spec.requireInstance("default").revisionTarget().toString());
        assertEquals("latest", spec.requireInstance("custom").revisionTarget().toString());
        assertEquals("whenClear", spec.requireInstance("default").revisionChange().toString());
        assertEquals("always", spec.requireInstance("custom").revisionChange().toString());
        assertEquals(3, spec.requireInstance("default").minRisk());
        assertEquals(12, spec.requireInstance("default").maxRisk());
        assertEquals(32, spec.requireInstance("default").maxIdleHours());
    }

    @Test
    public void productionSpecsWithIllegalRevisionSettings() {
        assertEquals("revision-change must be 'when-clear' when max-risk is specified, but got: 'always'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("<deployment>" +
                                                               "   <instance id='default'>" +
                                                               "      <upgrade revision-change='always' revision-target='next' min-risk='3' max-risk='12' max-idle-hours='32' />" +
                                                               "   </instance>" +
                                                               "</deployment>"))
                             .getMessage());

        assertEquals("revision-target must be 'next' when max-risk is specified, but got: 'latest'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("<deployment>" +
                                                               "   <instance id='default'>" +
                                                               "      <upgrade revision-change='when-clear' min-risk='3' max-risk='12' max-idle-hours='32' />" +
                                                               "   </instance>" +
                                                               "</deployment>"))
                             .getMessage());

        assertEquals("maximum risk cannot be less than minimum risk score, but got: '12'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("<deployment>" +
                                                               "   <instance id='default'>" +
                                                               "      <upgrade revision-change='when-clear' revision-target='next' min-risk='13' max-risk='12' max-idle-hours='32' />" +
                                                               "   </instance>" +
                                                               "</deployment>"))
                             .getMessage());

        assertEquals("maximum risk cannot be less than minimum risk score, but got: '0'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("<deployment>" +
                                                               "   <instance id='default'>" +
                                                               "      <upgrade min-risk='3' />" +
                                                               "   </instance>" +
                                                               "</deployment>"))
                             .getMessage());
    }

    @Test
    public void productionSpecWithUpgradeRollout() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <upgrade rollout='leading' />" +
                "   </instance>" +
                "   <instance id='aggressive'>" +
                "      <upgrade rollout='simultaneous' />" +
                "   </instance>" +
                "   <instance id='custom'/>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("leading", spec.requireInstance("default").upgradeRollout().toString());
        assertEquals("separate", spec.requireInstance("custom").upgradeRollout().toString());
        assertEquals("simultaneous", spec.requireInstance("aggressive").upgradeRollout().toString());
    }

    @Test
    public void productionSpecWithUpgradePolicy() {
        StringReader r = new StringReader(
                "<deployment>" +
                "   <instance id='default'>" +
                "      <upgrade policy='canary'/>" +
                "   </instance>" +
                "   <instance id='custom'/>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.requireInstance("default").upgradePolicy().toString());
        assertEquals("defaultPolicy", spec.requireInstance("custom").upgradePolicy().toString());
    }

    @Test
    public void upgradePolicyDefault() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <upgrade policy='canary' rollout='leading' revision-target='next' revision-change='when-clear' />" +
                "   <instance id='instance1'/>" +
                "   <instance id='instance2'>" +
                "      <upgrade policy='conservative' rollout='separate' revision-target='latest' revision-change='when-failing' />" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.requireInstance("instance1").upgradePolicy().toString());
        assertEquals("conservative", spec.requireInstance("instance2").upgradePolicy().toString());
        assertEquals("next", spec.requireInstance("instance1").revisionTarget().toString());
        assertEquals("latest", spec.requireInstance("instance2").revisionTarget().toString());
        assertEquals("whenClear", spec.requireInstance("instance1").revisionChange().toString());
        assertEquals("whenFailing", spec.requireInstance("instance2").revisionChange().toString());
        assertEquals("leading", spec.requireInstance("instance1").upgradeRollout().toString());
        assertEquals("separate", spec.requireInstance("instance2").upgradeRollout().toString());
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
    public void onlyAthenzServiceDefinedInInstance() {
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
    public void testAndStagingOutsideAndInsideInstance() {
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
    public void nestedParallelAndSteps() {
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
    public void parallelInstances() {
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
    public void instancesWithDelay() {
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
                "      <block-change days='mon-sun' hours='0-23' time-zone='CET' from-date='2022-01-01' to-date='2022-01-15'/>" +
                "      <prod>" +
                "         <region active='true'>us-west-1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(3, spec.requireInstance("default").changeBlocker().size());
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

        assertFalse(spec.requireInstance("default").canUpgradeAt(Instant.parse("2022-01-15T16:00:00.00Z")));
    }

    @Test
    public void changeBlockerInheritance() {
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

        String inheritedChangeBlocker = "change blocker revision=false version=true window=time window for hour(s) " +
                                        "[15, 16] on [monday, tuesday] in time zone UTC and date range [any date, any date]";

        assertEquals(2, spec.requireInstance("instance1").changeBlocker().size());
        assertEquals(inheritedChangeBlocker, spec.requireInstance("instance1").changeBlocker().get(0).toString());
        assertEquals("change blocker revision=true version=true window=time window for hour(s) [10] on " +
                     "[saturday] in time zone CET and date range [any date, any date]",
                     spec.requireInstance("instance1").changeBlocker().get(1).toString());

        assertEquals(1, spec.requireInstance("instance2").changeBlocker().size());
        assertEquals(inheritedChangeBlocker, spec.requireInstance("instance2").changeBlocker().get(0).toString());
    }

    @Test
    public void athenzConfigIsReadFromDeployment() {
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
    public void athenzConfigPropagatesThroughParallelZones() {
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
    public void athenzConfigPropagatesThroughParallelZonesAndInstances() {
        String r =
                """
                <deployment athenz-domain='domain' athenz-service='service'>
                   <parallel>
                      <instance id='instance1'>
                         <prod>
                            <parallel>
                               <region active='true'>us-west-1</region>
                               <region active='true'>us-east-3</region>
                            </parallel>
                         </prod>
                      </instance>
                      <instance id='instance2'>
                         <prod>
                            <parallel>
                               <region active='true'>us-west-1</region>
                               <region active='true'>us-east-3</region>
                            </parallel>
                         </prod>
                      </instance>
                   </parallel>
                </deployment>
                """;
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
    public void athenzConfigIsReadFromInstance() {
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
    public void athenzServiceIsOverriddenFromEnvironment() {
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
    public void missingAthenzServiceFails() {
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
    public void athenzServiceWithoutDomainFails() {
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
        DeploymentSpec spec = DeploymentSpec.fromXml("""
                                                     <deployment>
                                                        <instance id='default'>
                                                           <test tester-flavor="d-1-4-20" />
                                                           <staging />
                                                           <prod tester-flavor="d-2-8-50">
                                                              <region active="false">us-north-7</region>
                                                           </prod>
                                                        </instance>
                                                     </deployment>""");
        assertEquals(Optional.of("d-1-4-20"), spec.requireInstance("default").steps().get(0).zones().get(0).testerFlavor());
        assertEquals(Optional.empty(), spec.requireInstance("default").steps().get(1).zones().get(0).testerFlavor());
        assertEquals(Optional.of("d-2-8-50"), spec.requireInstance("default").steps().get(2).zones().get(0).testerFlavor());
    }

    @Test
    public void noEndpoints() {
        DeploymentSpec spec = DeploymentSpec.fromXml("""
                                                     <deployment>
                                                        <instance id='default'/>
                                                     </deployment>
                                                     """);
        assertEquals(Collections.emptyList(), spec.requireInstance("default").endpoints());
        assertEquals(ZoneEndpoint.defaultEndpoint, spec.zoneEndpoint(InstanceName.defaultName(),
                                                                     defaultId(),
                                                                     ClusterSpec.Id.from("cluster")));
        assertEquals(ZoneEndpoint.defaultEndpoint, spec.zoneEndpoint(InstanceName.defaultName(),
                                                                     com.yahoo.config.provision.zone.ZoneId.from("test", "us"),
                                                                     ClusterSpec.Id.from("cluster")));
    }

    @Test
    public void emptyEndpoints() {
        var spec = DeploymentSpec.fromXml("""
                                          <deployment>
                                             <instance id='default'>
                                                <endpoints/>
                                             </instance>
                                          </deployment>""");
        assertEquals(List.of(), spec.requireInstance("default").endpoints());
        assertEquals(ZoneEndpoint.defaultEndpoint, spec.zoneEndpoint(InstanceName.defaultName(),
                                                                     defaultId(),
                                                                     ClusterSpec.Id.from("cluster")));
    }

    @Test
    public void someEndpoints() {
        var spec = DeploymentSpec.fromXml("""
                                          <deployment>
                                             <instance id='default'>
                                                <prod>
                                                   <region active="true">us-east</region>
                                                </prod>
                                                <endpoints>
                                                   <endpoint id="foo" container-id="bar">
                                                      <region>us-east</region>
                                                   </endpoint>
                                                   <endpoint id="nalle" container-id="frosk" />
                                                   <endpoint container-id="quux" />
                                                   <endpoint container-id='bax' type='zone' enabled='true' />
                                                   <endpoint container-id='froz' type='zone' enabled='false' />
                                                   <endpoint container-id='froz' type='private'>
                                                     <region>us-east</region>
                                                     <allow with='aws-private-link' arn='barn' />
                                                     <allow with='gcp-service-connect' project='nine' />
                                                   </endpoint>
                                                </endpoints>
                                             </instance>
                                          </deployment>""");

        assertEquals(
                List.of("foo", "nalle", "default"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::endpointId).toList()
        );

        assertEquals(
                List.of("bar", "frosk", "quux"),
                spec.requireInstance("default").endpoints().stream().map(Endpoint::containerId).toList()
        );

        assertEquals(List.of(RegionName.from("us-east")), spec.requireInstance("default").endpoints().get(0).regions());

        var zone = from(Environment.prod, RegionName.from("us-east"));
        var testZone = from(Environment.test, RegionName.from("us-east"));
        assertEquals(ZoneEndpoint.defaultEndpoint,
                     spec.zoneEndpoint(InstanceName.from("custom"), zone, ClusterSpec.Id.from("bax")));
        assertEquals(ZoneEndpoint.defaultEndpoint,
                     spec.zoneEndpoint(InstanceName.from("default"), defaultId(), ClusterSpec.Id.from("bax")));
        assertEquals(ZoneEndpoint.defaultEndpoint,
                     spec.zoneEndpoint(InstanceName.from("default"), zone, ClusterSpec.Id.from("bax")));
        assertEquals(ZoneEndpoint.defaultEndpoint,
                     spec.zoneEndpoint(InstanceName.from("default"), testZone, ClusterSpec.Id.from("bax")));
        assertEquals(ZoneEndpoint.privateEndpoint,
                     spec.zoneEndpoint(InstanceName.from("default"), testZone, ClusterSpec.Id.from("froz")));

        assertEquals(new ZoneEndpoint(false, true, List.of(new AllowedUrn(AccessType.awsPrivateLink, "barn"),
                                                           new AllowedUrn(AccessType.gcpServiceConnect, "nine"))),
                     spec.zoneEndpoint(InstanceName.from("default"), zone, ClusterSpec.Id.from("froz")));
    }

    @Test
    public void invalidEndpoints() {
        assertInvalidEndpoints("<endpoint id='FOO' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'FOO'");
        assertInvalidEndpoints("<endpoint id='123' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got '123'");
        assertInvalidEndpoints("<endpoint id='foo!' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'foo!'");
        assertInvalidEndpoints("<endpoint id='foo.bar' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'foo.bar'");
        assertInvalidEndpoints("<endpoint id='foo--bar' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'foo--bar'");
        assertInvalidEndpoints("<endpoint id='foo-' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'foo-'");
        assertInvalidEndpoints("<endpoint id='foooooooooooo' container-id='qrs'/>",
                               "Endpoint id must be all lowercase, alphanumeric, with no consecutive dashes, of length 1 to 12, and begin with a character; but got 'foooooooooooo'");

        assertInvalidEndpoints("<endpoint id='foo' container-id='qrs'/><endpoint id='foo' container-id='qrs'/>",
                               "Endpoint id 'foo' is specified multiple times");
        assertInvalidEndpoints("<endpoint id='default' type='zone' container-id='foo' />",
                               "Instance-level endpoint 'default': cannot declare 'id' with type 'zone' or 'private'");
        assertInvalidEndpoints("<endpoint id='default' type='private' container-id='foo' />",
                               "Instance-level endpoint 'default': cannot declare 'id' with type 'zone' or 'private'");
        assertInvalidEndpoints("<endpoint type='zone' />",
                               "Missing required attribute 'container-id' in 'endpoint'");
        assertInvalidEndpoints("<endpoint type='private' />",
                               "Missing required attribute 'container-id' in 'endpoint'");
        assertInvalidEndpoints("<endpoint container-id='foo' type='zone'><allow /></endpoint>",
                               "Instance-level endpoint 'default': only endpoints of type 'private' can specify 'allow' children");
        assertInvalidEndpoints("<endpoint type='private' container-id='foo' enabled='true' />",
                               "Instance-level endpoint 'default': only endpoints of type 'zone' can specify 'enabled'");
        assertInvalidEndpoints("<endpoint type='zone' container-id='qrs'/><endpoint type='zone' container-id='qrs'/>",
                               "Multiple zone endpoints (for all regions) declared for container id 'qrs'");
        assertInvalidEndpoints("<endpoint type='private' container-id='qrs'><region>us</region></endpoint>" +
                               "<endpoint type='private' container-id='qrs'><region>us</region></endpoint>",
                               "Multiple private endpoints declared for container id 'qrs' in region 'us'");
        assertInvalidEndpoints("<endpoint type='zone' container-id='qrs' />" +
                               "<endpoint type='zone' container-id='qrs'><region>us</region></endpoint>",
                               "Zone endpoint for container id 'qrs' declared both with region 'us', and for all regions.");
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
        var spec = DeploymentSpec.fromXml("""
                                          <deployment>
                                             <instance id='default'>
                                                <prod>
                                                   <region>us-east</region>
                                                   <region>us-west</region>
                                                </prod>
                                                <endpoints>
                                                   <endpoint id="foo" container-id="bar">
                                                      <region>us-east</region>
                                                   </endpoint>
                                                   <endpoint container-id="bar" type='private'>
                                                      <region>us-east</region>
                                                   </endpoint>
                                                   <endpoint id="nalle" container-id="frosk" />
                                                   <endpoint container-id="quux" />
                                                   <endpoint container-id="quux" type='private' />
                                                </endpoints>
                                             </instance>
                                          </deployment>""");

        assertEquals(Set.of("us-east"), endpointRegions("foo", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("nalle", spec));
        assertEquals(Set.of("us-east", "us-west"), endpointRegions("default", spec));
        assertEquals(new ZoneEndpoint(true, true, List.of()),
                     spec.zoneEndpoint(InstanceName.from("default"), from("prod", "us-east"), ClusterSpec.Id.from("bar")));
        assertEquals(new ZoneEndpoint(true, false, List.of()),
                     spec.zoneEndpoint(InstanceName.from("default"), from("prod", "us-west"), ClusterSpec.Id.from("bar")));
        assertEquals(new ZoneEndpoint(true, true, List.of()),
                     spec.zoneEndpoint(InstanceName.from("default"), from("prod", "us-east"), ClusterSpec.Id.from("quux")));
        assertEquals(new ZoneEndpoint(true, true, List.of()),
                     spec.zoneEndpoint(InstanceName.from("default"), from("prod", "us-west"), ClusterSpec.Id.from("quux")));
        assertEquals(new HashSet<>() {{ add(null); add(from("prod", "us-east")); }},
                     spec.requireInstance("default").zoneEndpoints().get(ClusterSpec.Id.from("bar")).keySet());
        assertEquals(new HashSet<>() {{ add(null); }},
                     spec.requireInstance("default").zoneEndpoints().get(ClusterSpec.Id.from("quux")).keySet());
        assertEquals(Set.of(ClusterSpec.Id.from("bar"), ClusterSpec.Id.from("quux")),
                     spec.requireInstance("default").zoneEndpoints().keySet());
    }

    @Test
    public void instanceEndpointDisallowsRegionAttributeOrInstanceTag() {
        String xmlForm = """
                         <deployment>
                           <instance id='default'>
                             <prod>
                               <region active="true">us-east</region>
                               <region active="true">us-west</region>
                             </prod>
                             <endpoints>
                               <endpoint container-id="bar" %s>
                                 %s
                               </endpoint>
                             </endpoints>
                           </instance>
                         </deployment>""";
        assertInvalid(String.format(xmlForm, "id='foo' region='us-east'", "<region>us-east</region>"), "Instance-level endpoint 'foo': invalid 'region' attribute");
        assertInvalid(String.format(xmlForm, "id='foo'", "<instance>us-east</instance>"), "Instance-level endpoint 'foo': invalid element 'instance'");
        assertInvalid(String.format(xmlForm, "type='zone'", "<instance>us-east</instance>"), "Instance-level endpoint 'default': invalid element 'instance'");
        assertInvalid(String.format(xmlForm, "type='private'", "<instance>us-east</instance>"), "Instance-level endpoint 'default': invalid element 'instance'");
    }

    @Test
    public void applicationLevelEndpointValidation() {
        String xmlForm = """
                         <deployment>
                           <instance id="beta">
                             <prod>
                               <region active='true'>us-west-1</region>
                               <region active='true'>us-east-3</region>
                             </prod>
                           </instance>
                           <instance id="main">
                             <prod>
                               <region active='true'>us-west-1</region>
                               <region active='true'>us-east-3</region>
                             </prod>
                           </instance>
                           <endpoints>
                             <endpoint id="foo" container-id="qrs" %s>
                               <instance %s %s>%s</instance>
                         %s    </endpoint>
                           </endpoints>
                         </deployment>
                         """;
        assertInvalid(String.format(xmlForm, "", "weight='1'", "", "main", ""), "'region' attribute must be declared on either <endpoint> or <instance> tag");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "weight='1'", "region='us-west-1'", "main", ""), "'region' attribute must be declared on either <endpoint> or <instance> tag");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "", "", "main", ""), "Missing required attribute 'weight' in 'instance");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "weight='1'", "", "", ""), "Application-level endpoint 'foo': empty 'instance' element");
        assertInvalid(String.format(xmlForm, "region='invalid'", "weight='1'", "", "main", ""), "Application-level endpoint 'foo': targets undeclared region 'invalid' in instance 'main'");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "weight='foo'", "", "main", ""), "Application-level endpoint 'foo': invalid weight value 'foo'");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "weight='1'", "", "main", "<region>us-east-3</region>"), "Application-level endpoint 'foo': invalid element 'region'");
        assertInvalid(String.format(xmlForm, "region='us-west-1'", "weight='0'", "", "main", ""), "Application-level endpoint 'foo': sum of all weights must be positive, got 0");
        assertInvalid(String.format(xmlForm, "type='zone'", "weight='1'", "", "main", ""), "Endpoints at application level cannot be of type 'zone'");
        assertInvalid(String.format(xmlForm, "type='private'", "weight='1'", "", "main", ""), "Endpoints at application level cannot be of type 'private'");
    }

    @Test
    public void cannotTargetDisabledEndpoints() {
        assertEquals("Instance-level endpoint 'default': all eligible zone endpoints have 'enabled' set to 'false'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("""
                                                               <deployment>
                                                                 <instance id="default">
                                                                   <prod>
                                                                     <region>us</region>
                                                                     <region>eu</region>
                                                                   </prod>
                                                                   <endpoints>
                                                                     <endpoint container-id='id' />
                                                                     <endpoint type='zone' container-id='id' enabled='false' />
                                                                   </endpoints>
                                                                 </instance>
                                                               </deployment>
                                                               """))
                             .getMessage());

        assertEquals("Instance-level endpoint 'default': targets zone endpoint in 'us' with 'enabled' set to 'false'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("""
                                                               <deployment>
                                                                 <instance id="default">
                                                                   <prod>
                                                                     <region>us</region>
                                                                     <region>eu</region>
                                                                   </prod>
                                                                   <endpoints>
                                                                     <endpoint container-id='id'>
                                                                       <region>us</region>
                                                                     </endpoint>
                                                                     <endpoint type='zone' container-id='id' enabled='false' />
                                                                   </endpoints>
                                                                 </instance>
                                                               </deployment>
                                                               """))
                             .getMessage());

        assertEquals("Application-level endpoint 'default': targets 'us' in 'default', but its zone endpoint has 'enabled' set to 'false'",
                     assertThrows(IllegalArgumentException.class,
                                  () -> DeploymentSpec.fromXml("""
                                                               <deployment>
                                                                 <instance id="default">
                                                                   <prod>
                                                                     <region>us</region>
                                                                     <region>eu</region>
                                                                   </prod>
                                                                   <endpoints>
                                                                     <endpoint type='zone' container-id='id' enabled='false'>
                                                                       <region>us</region>
                                                                     </endpoint>
                                                                   </endpoints>
                                                                 </instance>
                                                                 <endpoints>
                                                                   <endpoint container-id='id' region='us'>
                                                                     <instance weight='1'>default</instance>
                                                                   </endpoint>
                                                                 </endpoints>
                                                               </deployment>
                                                               """))
                             .getMessage());
    }

    @Test
    public void applicationLevelEndpoint() {
        DeploymentSpec spec = DeploymentSpec.fromXml("""
                                                     <deployment>
                                                       <instance id="beta">
                                                         <prod>
                                                           <region active='true'>us-west-1</region>
                                                           <region active='true'>us-east-3</region>
                                                         </prod>
                                                       </instance>
                                                       <instance id="main">
                                                         <prod>
                                                           <region active='true'>us-west-1</region>
                                                           <region active='true'>us-east-3</region>
                                                         </prod>
                                                         <endpoints>
                                                           <endpoint id="glob" container-id="music"/>
                                                         </endpoints>
                                                       </instance>
                                                       <endpoints>
                                                         <endpoint id="foo" container-id="movies" region='us-west-1'>
                                                           <instance weight="2">beta</instance>
                                                           <instance weight="8">main</instance>
                                                         </endpoint>
                                                         <endpoint id="bar" container-id="music" region='us-east-3'>
                                                           <instance weight="10">main</instance>
                                                         </endpoint>
                                                         <endpoint id="baz" container-id="moose">
                                                           <instance weight="1" region='us-west-1'>main</instance>
                                                           <instance weight="2" region='us-east-3'>main</instance>
                                                           <instance weight="3" region='us-west-1'>beta</instance>
                                                         </endpoint>
                                                       </endpoints>
                                                     </deployment>
                                                     """);
        assertEquals(List.of(new Endpoint("foo", "movies", Level.application,
                                          List.of(new Target(RegionName.from("us-west-1"), InstanceName.from("beta"), 2),
                                                  new Target(RegionName.from("us-west-1"), InstanceName.from("main"), 8))),
                             new Endpoint("bar", "music", Level.application,
                                          List.of(new Target(RegionName.from("us-east-3"), InstanceName.from("main"), 10))),
                             new Endpoint("baz", "moose", Level.application,
                                          List.of(new Target(RegionName.from("us-west-1"), InstanceName.from("main"), 1),
                                                  new Target(RegionName.from("us-east-3"), InstanceName.from("main"), 2),
                                                  new Target(RegionName.from("us-west-1"), InstanceName.from("beta"), 3)))),
                             spec.endpoints());
        assertEquals(List.of(new Endpoint("glob", "music", Level.instance,
                                          List.of(new Target(RegionName.from("us-west-1"), InstanceName.from("main"), 1),
                                                  new Target(RegionName.from("us-east-3"), InstanceName.from("main"), 1)))),
                     spec.requireInstance("main").endpoints());
    }

    @Test
    public void disallowExcessiveUpgradeBlocking() {
        List<String> specs = List.of(
                """
                <deployment>
                  <block-change/>
                </deployment>""",

                """
                <deployment>
                  <block-change days="mon-wed"/>
                  <block-change days="tue-sun"/>
                </deployment>""",

                """
                <deployment>
                  <block-change to-date="2023-01-01"/>
                </deployment>""",

                // Convoluted example of blocking too long
                """
                <deployment>
                  <block-change days="sat-sun"/>
                  <block-change days="mon-fri" hours="0-10" from-date="2023-01-01" to-date="2023-01-15"/>
                  <block-change days="mon-fri" hours="11-23" from-date="2023-01-01" to-date="2023-01-15"/>
                  <block-change from-date="2023-01-14" to-date="2023-01-31"/></deployment>"""
        );
        ManualClock clock = new ManualClock();
        clock.setInstant(Instant.parse("2022-01-05T15:00:00.00Z"));
        for (var spec : specs) {
            assertInvalid(spec, "Cannot block Vespa upgrades for longer than 21 consecutive days", clock);
        }
    }

    @Test
    public void testDeployableHash() {
        assertEquals(DeploymentSpec.fromXml("""
                                            <deployment>
                                              <instance id='default' />
                                            </deployment>""").deployableHashCode(),
                     DeploymentSpec.fromXml("""
                                            <deployment>
                                              <instance id='default' tags='  '>
                                                <test />
                                                <staging tester-flavor='2-8-50' />
                                                <block-change days='mon' />
                                                <upgrade policy='canary' revision-target='next' revision-change='when-clear' rollout='simultaneous' />
                                                <prod />
                                                <notifications>
                                                  <email role='author' />
                                                  <email address='dev@duff' />
                                                </notifications>
                                              </instance>
                                            </deployment>""").deployableHashCode());

        assertEquals(DeploymentSpec.fromXml("""
                                            <deployment>
                                              <parallel>
                                                <instance id='one'>
                                                  <prod>
                                                    <region>name</region>
                                                  </prod>
                                                </instance>
                                                <instance id='two' />
                                              </parallel>
                                            </deployment>""").deployableHashCode(),
                     DeploymentSpec.fromXml("""
                                            <deployment>
                                              <instance id='one'>
                                                <prod>
                                                  <steps>
                                                    <region>name</region>
                                                    <delay hours='3' />
                                                    <test>name</test>
                                                  </steps>
                                                </prod>
                                              </instance>
                                              <instance id='two' /></deployment>""").deployableHashCode());

        String referenceSpec = """
                               <deployment>
                                 <instance id='default'>
                                   <prod>
                                     <region>name</region>
                                   </prod>
                                 </instance>
                               </deployment>""";

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("<deployment />").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='default' />
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='default' tags='tag1'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='custom'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='custom'>
                                                   <prod>
                                                     <region>other</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment major-version='9'>
                                                 <instance id='default'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment athenz-domain='domain' athenz-service='service'>
                                                 <instance id='default'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment athenz-domain='domain'>
                                                 <instance id='default' athenz-service='service'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment athenz-domain='domain'>
                                                 <instance id='default'>
                                                   <prod athenz-service='prod'>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='default'>
                                                   <prod global-service-id='service'>
                                                     <region>name</region>
                                                   </prod>
                                                 </instance>
                                               </deployment>""").deployableHashCode());

        assertNotEquals(DeploymentSpec.fromXml(referenceSpec).deployableHashCode(),
                        DeploymentSpec.fromXml("""
                                               <deployment>
                                                 <instance id='default'>
                                                   <prod>
                                                     <region>name</region>
                                                   </prod>
                                                   <endpoints>
                                                     <endpoint container-id="quux" />    </endpoints>
                                                 </instance>
                                               </deployment>""").deployableHashCode());
    }

    @Test
    public void cloudAccount() {
        String r =
                """
                <deployment version='1.0' cloud-account='100000000000'>
                    <instance id='alpha'>
                      <prod cloud-account='800000000000'>
                          <region>us-east-1</region>
                      </prod>
                    </instance>
                    <instance id='beta' cloud-account='200000000000'>
                      <staging cloud-account='600000000000'/>
                      <perf cloud-account='700000000000'/>
                      <prod>
                          <region>us-west-1</region>
                          <region cloud-account='default'>us-west-2</region>
                      </prod>
                    </instance>
                    <instance id='main'>
                      <test cloud-account='500000000000'/>
                      <dev cloud-account='400000000000'/>
                      <prod>
                          <region cloud-account='300000000000'>us-east-1</region>
                          <region>eu-west-1</region>
                      </prod>
                    </instance>
                </deployment>
                """;
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(Optional.of(CloudAccount.from("100000000000")), spec.cloudAccount());
        assertCloudAccount("800000000000", spec.requireInstance("alpha"), Environment.prod, "us-east-1");
        assertCloudAccount("200000000000", spec.requireInstance("beta"), Environment.prod, "us-west-1");
        assertCloudAccount("600000000000", spec.requireInstance("beta"), Environment.staging, "");
        assertCloudAccount("700000000000", spec.requireInstance("beta"), Environment.perf, "");
        assertCloudAccount("200000000000", spec.requireInstance("beta"), Environment.dev, "");
        assertCloudAccount("300000000000", spec.requireInstance("main"), Environment.prod, "us-east-1");
        assertCloudAccount("100000000000", spec.requireInstance("main"), Environment.prod, "eu-west-1");
        assertCloudAccount("400000000000", spec.requireInstance("main"), Environment.dev, "");
        assertCloudAccount("500000000000", spec.requireInstance("main"), Environment.test, "");
        assertCloudAccount("100000000000", spec.requireInstance("main"), Environment.staging, "");
        assertCloudAccount("default", spec.requireInstance("beta"), Environment.prod, "us-west-2");
    }

    private void assertCloudAccount(String expected, DeploymentInstanceSpec instance, Environment environment, String region) {
        assertEquals(Optional.of(expected).map(CloudAccount::from), instance.cloudAccount(environment, Optional.of(region).filter(s -> !s.isEmpty()).map(RegionName::from)));
    }

    private static void assertInvalid(String deploymentSpec, String errorMessagePart) {
        assertInvalid(deploymentSpec, errorMessagePart, new ManualClock());
    }

    private static void assertInvalid(String deploymentSpec, String errorMessagePart, Clock clock) {
        if (errorMessagePart.isEmpty()) throw new IllegalArgumentException("Message part must be non-empty");
        try {
            new DeploymentSpecXmlReader(true, clock).read(deploymentSpec);
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            assertTrue("\"" + e.getMessage() + "\" contains \"" + errorMessagePart + "\"",
                       e.getMessage().contains(errorMessagePart));
        }
    }

    private static void assertInvalidEndpoints(String endpointsBody, String error) {
        assertEquals(error,
                     assertThrows(IllegalArgumentException.class,
                                  () -> endpointIds(endpointsBody))
                             .getMessage());
    }

    private static Set<String> endpointRegions(String endpointId, DeploymentSpec spec) {
        return spec.requireInstance("default").endpoints().stream()
                .filter(endpoint -> endpoint.endpointId().equals(endpointId))
                .flatMap(endpoint -> endpoint.regions().stream())
                .map(RegionName::value)
                .collect(Collectors.toSet());
    }

    private static List<String> endpointIds(String endpointsBody) {
        var xml = "<deployment>" +
                  "   <instance id='default'>" +
                  "      <prod>" +
                  "         <region active=\"true\">us-east</region>" +
                  "      </prod>" +
                  "      <endpoints>" +
                  endpointsBody +
                  "      </endpoints>" +
                  "   </instance>" +
                  "</deployment>";

        return DeploymentSpec.fromXml(xml).requireInstance("default").endpoints().stream()
                             .map(Endpoint::endpointId)
                             .toList();
    }

}
