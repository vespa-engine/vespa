// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;

import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

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
                         "   <test/>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.steps().size());
        assertFalse(spec.majorVersion().isPresent());
        assertTrue(spec.steps().get(0).deploysTo(Environment.test));
        assertTrue(spec.includes(Environment.test, Optional.empty()));
        assertFalse(spec.includes(Environment.test, Optional.of(RegionName.from("region1"))));
        assertFalse(spec.includes(Environment.staging, Optional.empty()));
        assertFalse(spec.includes(Environment.prod, Optional.empty()));
        assertFalse(spec.globalServiceId().isPresent());
    }

    @Test
    public void testSpecPinningMajorVersion() {
        String specXml = "<deployment version='1.0' major-version='6'>" +
                         "   <test/>" +
                         "</deployment>";

        StringReader r = new StringReader(specXml);
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(specXml, spec.xmlForm());
        assertEquals(1, spec.steps().size());
        assertTrue(spec.majorVersion().isPresent());
        assertEquals(6, (int)spec.majorVersion().get());
    }

    @Test
    public void stagingSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <staging/>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(2, spec.steps().size());
        assertTrue(spec.steps().get(0).deploysTo(Environment.test));
        assertTrue(spec.steps().get(1).deploysTo(Environment.staging));
        assertTrue(spec.includes(Environment.test, Optional.empty()));
        assertFalse(spec.includes(Environment.test, Optional.of(RegionName.from("region1"))));
        assertTrue(spec.includes(Environment.staging, Optional.empty()));
        assertFalse(spec.includes(Environment.prod, Optional.empty()));
        assertFalse(spec.globalServiceId().isPresent());
    }

    @Test
    public void minimalProductionSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <prod>" +
        "      <region active='false'>us-east1</region>" +
        "      <region active='true'>us-west1</region>" +
        "   </prod>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(4, spec.steps().size());

        assertTrue(spec.steps().get(0).deploysTo(Environment.test));

        assertTrue(spec.steps().get(1).deploysTo(Environment.staging));

        assertTrue(spec.steps().get(2).deploysTo(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)spec.steps().get(2)).active());

        assertTrue(spec.steps().get(3).deploysTo(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)spec.steps().get(3)).active());

        assertTrue(spec.includes(Environment.test, Optional.empty()));
        assertFalse(spec.includes(Environment.test, Optional.of(RegionName.from("region1"))));
        assertTrue(spec.includes(Environment.staging, Optional.empty()));
        assertTrue(spec.includes(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(spec.includes(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(spec.includes(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(spec.globalServiceId().isPresent());
        
        assertEquals(DeploymentSpec.UpgradePolicy.defaultPolicy, spec.upgradePolicy());
    }

    @Test
    public void maximalProductionSpec() {
        StringReader r = new StringReader(
        "<deployment version='1.0'>" +
        "   <test/>" +
        "   <staging/>" +
        "   <prod>" +
        "      <region active='false'>us-east1</region>" +
        "      <delay hours='3' minutes='30'/>" +
        "      <region active='true'>us-west1</region>" +
        "   </prod>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(5, spec.steps().size());
        assertEquals(4, spec.zones().size());

        assertTrue(spec.steps().get(0).deploysTo(Environment.test));

        assertTrue(spec.steps().get(1).deploysTo(Environment.staging));

        assertTrue(spec.steps().get(2).deploysTo(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.DeclaredZone)spec.steps().get(2)).active());

        assertTrue(spec.steps().get(3) instanceof DeploymentSpec.Delay);
        assertEquals(3 * 60 * 60 + 30 * 60, ((DeploymentSpec.Delay)spec.steps().get(3)).duration().getSeconds());

        assertTrue(spec.steps().get(4).deploysTo(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.DeclaredZone)spec.steps().get(4)).active());

        assertTrue(spec.includes(Environment.test, Optional.empty()));
        assertFalse(spec.includes(Environment.test, Optional.of(RegionName.from("region1"))));
        assertTrue(spec.includes(Environment.staging, Optional.empty()));
        assertTrue(spec.includes(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertTrue(spec.includes(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertFalse(spec.includes(Environment.prod, Optional.of(RegionName.from("no-such-region"))));
        assertFalse(spec.globalServiceId().isPresent());
    }

    @Test
    public void productionSpecWithGlobalServiceId() {
        StringReader r = new StringReader(
            "<deployment version='1.0'>" +
            "    <prod global-service-id='query'>" +
            "        <region active='true'>us-east-1</region>" +
            "        <region active='true'>us-west-1</region>" +
            "    </prod>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.globalServiceId(), Optional.of("query"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInTest() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "    <test global-service-id='query' />" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test(expected=IllegalArgumentException.class)
    public void globalServiceIdInStaging() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "    <staging global-service-id='query' />" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test
    public void productionSpecWithGlobalServiceIdBeforeStaging() {
        StringReader r = new StringReader(
            "<deployment>" +
            "  <test/>" +
            "  <prod global-service-id='qrs'>" +
            "    <region active='true'>us-west-1</region>" +
            "    <region active='true'>us-central-1</region>" +
            "    <region active='true'>us-east-3</region>" +
            "  </prod>" +
            "  <staging/>" +
            "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("qrs", spec.globalServiceId().get());
    }

    @Test
    public void productionSpecWithUpgradePolicy() {
        StringReader r = new StringReader(
                "<deployment>" +
                "  <upgrade policy='canary'/>" +
                "  <prod>" +
                "    <region active='true'>us-west-1</region>" +
                "    <region active='true'>us-central-1</region>" +
                "    <region active='true'>us-east-3</region>" +
                "  </prod>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("canary", spec.upgradePolicy().toString());
    }

    @Test
    public void maxDelayExceeded() {
        try {
            StringReader r = new StringReader(
                    "<deployment>" +
                    "  <upgrade policy='canary'/>" +
                    "  <prod>" +
                    "    <region active='true'>us-west-1</region>" +
                    "    <delay hours='23'/>" +
                    "    <region active='true'>us-central-1</region>" +
                    "    <delay minutes='59' seconds='61'/>" +
                    "    <region active='true'>us-east-3</region>" +
                    "  </prod>" +
                    "</deployment>"
            );
            DeploymentSpec.fromXml(r);
            fail("Expected exception due to exceeding the max total delay");
        }
        catch (IllegalArgumentException e) {
            // success
            assertEquals("The total delay specified is PT24H1S but max 24 hours is allowed", e.getMessage());
        }
    }

    @Test
    public void testEmpty() {
        assertFalse(DeploymentSpec.empty.globalServiceId().isPresent());
        assertEquals(DeploymentSpec.UpgradePolicy.defaultPolicy, DeploymentSpec.empty.upgradePolicy());
        assertTrue(DeploymentSpec.empty.steps().isEmpty());
        assertEquals("<deployment version='1.0'/>", DeploymentSpec.empty.xmlForm());
    }

    @Test
    public void productionSpecWithParallelDeployments() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                        "  <prod>    \n" +
                        "    <region active='true'>us-west-1</region>\n" +
                        "    <parallel>\n" +
                        "      <region active='true'>us-central-1</region>\n" +
                        "      <region active='true'>us-east-3</region>\n" +
                        "    </parallel>\n" +
                        "  </prod>\n" +
                        "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        DeploymentSpec.ParallelZones parallelZones = ((DeploymentSpec.ParallelZones) spec.steps().get(3));
        assertEquals(2, parallelZones.zones().size());
        assertEquals(RegionName.from("us-central-1"), parallelZones.zones().get(0).region().get());
        assertEquals(RegionName.from("us-east-3"), parallelZones.zones().get(1).region().get());
    }

    @Test
    public void productionSpecWithDuplicateRegions() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                        "  <prod>\n" +
                        "    <region active='true'>us-west-1</region>\n" +
                        "    <parallel>\n" +
                        "      <region active='true'>us-west-1</region>\n" +
                        "      <region active='true'>us-central-1</region>\n" +
                        "      <region active='true'>us-east-3</region>\n" +
                        "    </parallel>\n" +
                        "  </prod>\n" +
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
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec1() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "  <block-change days='mon,tue' hours='15-16'/>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentSpecWithIllegallyOrderedDeploymentSpec2() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <test/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test
    public void deploymentSpecWithChangeBlocker() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <block-change revision='false' days='mon,tue' hours='15-16'/>\n" +
                "  <block-change days='sat' hours='10' time-zone='CET'/>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(2, spec.changeBlocker().size());
        assertTrue(spec.changeBlocker().get(0).blocksVersions());
        assertFalse(spec.changeBlocker().get(0).blocksRevisions());
        assertEquals(ZoneId.of("UTC"), spec.changeBlocker().get(0).window().zone());

        assertTrue(spec.changeBlocker().get(1).blocksVersions());
        assertTrue(spec.changeBlocker().get(1).blocksRevisions());
        assertEquals(ZoneId.of("CET"), spec.changeBlocker().get(1).window().zone());

        assertTrue(spec.canUpgradeAt(Instant.parse("2017-09-18T14:15:30.00Z")));
        assertFalse(spec.canUpgradeAt(Instant.parse("2017-09-18T15:15:30.00Z")));
        assertFalse(spec.canUpgradeAt(Instant.parse("2017-09-18T16:15:30.00Z")));
        assertTrue(spec.canUpgradeAt(Instant.parse("2017-09-18T17:15:30.00Z")));

        assertTrue(spec.canUpgradeAt(Instant.parse("2017-09-23T09:15:30.00Z")));
        assertFalse(spec.canUpgradeAt(Instant.parse("2017-09-23T08:15:30.00Z"))); // 10 in CET
        assertTrue(spec.canUpgradeAt(Instant.parse("2017-09-23T10:15:30.00Z")));
    }

    @Test
    public void athenz_config_is_read_from_deployment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.athenzDomain().get().value(), "domain");
        assertEquals(spec.athenzService(Environment.prod, RegionName.from("us-west-1")).get().value(), "service");
    }

    @Test
    public void athenz_service_is_overridden_from_environment() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain' athenz-service='service'>\n" +
                "  <test/>\n" +
                "  <prod athenz-service='prod-service'>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(spec.athenzDomain().get().value(), "domain");
        assertEquals(spec.athenzService(Environment.prod, RegionName.from("us-west-1")).get().value(), "prod-service");
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_not_defined() {
        StringReader r = new StringReader(
                "<deployment athenz-domain='domain'>\n" +
                "  <prod>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_fails_when_athenz_service_is_configured_but_not_athenz_domain() {
        StringReader r = new StringReader(
                "<deployment>\n" +
                "  <prod athenz-service='service'>\n" +
                "    <region active='true'>us-west-1</region>\n" +
                "  </prod>\n" +
                "</deployment>"
        );
        DeploymentSpec spec = DeploymentSpec.fromXml(r);
    }

    @Test
    public void noNotifications() {
        assertEquals(Notifications.none(),
                     DeploymentSpec.fromXml("<deployment />").notifications());
    }

    @Test
    public void emptyNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <notifications />" +
                                                     "</deployment>");
        assertEquals(Notifications.none(),
                     spec.notifications());
    }

    @Test
    public void someNotifications() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <notifications when=\"failing\">\n" +
                                                     "    <email role=\"author\"/>\n" +
                                                     "    <email address=\"john@dev\" when=\"failing-commit\"/>\n" +
                                                     "    <email address=\"jane@dev\"/>\n" +
                                                     "  </notifications>\n" +
                                                     "</deployment>");
        assertEquals(ImmutableSet.of(author), spec.notifications().emailRolesFor(failing));
        assertEquals(ImmutableSet.of(author), spec.notifications().emailRolesFor(failingCommit));
        assertEquals(ImmutableSet.of("john@dev", "jane@dev"), spec.notifications().emailAddressesFor(failingCommit));
        assertEquals(ImmutableSet.of("jane@dev"), spec.notifications().emailAddressesFor(failing));
    }

}
