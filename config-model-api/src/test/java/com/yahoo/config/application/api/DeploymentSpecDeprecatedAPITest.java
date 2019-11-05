// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;

import java.io.StringReader;
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
// TODO: Remove after November 2019
public class DeploymentSpecDeprecatedAPITest {

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
    }

    @Test
    public void deploymentSpecWithTest() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <test/>" +
                "   <staging/>" +
                "   <prod>" +
                "      <region active='false'>us-east1</region>" +
                "      <region active='true'>us-west1</region>" +
                "   </prod>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("[test, staging, prod.us-east1, prod.us-west1]", spec.steps().toString());
    }

    @Test
    public void deploymentSpecWithTestInsideInstance() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <instance id='instance1'>" +
                "      <test/>" +
                "      <staging/>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("[test, staging, prod.us-east1, prod.us-west1]", spec.steps().toString());
    }

    @Test
    public void deploymentSpecWithTestOutsideInstance() {
        StringReader r = new StringReader(
                "<deployment version='1.0'>" +
                "   <test/>" +
                "   <staging/>" +
                "   <instance id='instance1'>" +
                "      <prod>" +
                "         <region active='false'>us-east1</region>" +
                "         <region active='true'>us-west1</region>" +
                "      </prod>" +
                "   </instance>" +
                "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals("[test, staging, prod.us-east1, prod.us-west1]", spec.steps().toString());
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
        DeploymentSpec.fromXml(r);
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
        DeploymentSpec.fromXml(r);
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

    @Test
    public void customTesterFlavor() {
        DeploymentSpec spec = DeploymentSpec.fromXml("<deployment>\n" +
                                                     "  <test tester-flavor=\"d-1-4-20\" />\n" +
                                                     "  <prod tester-flavor=\"d-2-8-50\">\n" +
                                                     "    <region active=\"false\">us-north-7</region>\n" +
                                                     "  </prod>\n" +
                                                     "</deployment>");
        assertEquals(Optional.of("d-1-4-20"), spec.steps().get(0).zones().get(0).testerFlavor());
        assertEquals(Optional.empty(), spec.steps().get(1).zones().get(0).testerFlavor());
        assertEquals(Optional.of("d-2-8-50"), spec.steps().get(2).zones().get(0).testerFlavor());
    }

    @Test
    public void noEndpoints() {
        assertEquals(Collections.emptyList(), DeploymentSpec.fromXml("<deployment />").endpoints());
    }

    @Test
    public void emptyEndpoints() {
        final var spec = DeploymentSpec.fromXml("<deployment><endpoints/></deployment>");
        assertEquals(Collections.emptyList(), spec.endpoints());
    }

    @Test
    public void someEndpoints() {
        final var spec = DeploymentSpec.fromXml("" +
                "<deployment>" +
                "  <prod>" +
                "    <region active=\"true\">us-east</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint id=\"foo\" container-id=\"bar\">" +
                "      <region>us-east</region>" +
                "    </endpoint>" +
                "    <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                "    <endpoint container-id=\"quux\" />" +
                "  </endpoints>" +
                "</deployment>");

        assertEquals(
                List.of("foo", "nalle", "default"),
                spec.endpoints().stream().map(Endpoint::endpointId).collect(Collectors.toList())
        );

        assertEquals(
                List.of("bar", "frosk", "quux"),
                spec.endpoints().stream().map(Endpoint::containerId).collect(Collectors.toList())
        );

        assertEquals(Set.of(RegionName.from("us-east")), spec.endpoints().get(0).regions());
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
                "  <prod>" +
                "    <region active=\"true\">us-east</region>" +
                "    <region active=\"true\">us-west</region>" +
                "  </prod>" +
                "  <endpoints>" +
                "    <endpoint id=\"foo\" container-id=\"bar\">" +
                "      <region>us-east</region>" +
                "    </endpoint>" +
                "    <endpoint id=\"nalle\" container-id=\"frosk\" />" +
                "    <endpoint container-id=\"quux\" />" +
                "  </endpoints>" +
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
        return spec.endpoints().stream()
                .filter(endpoint -> endpoint.endpointId().equals(endpointId))
                .flatMap(endpoint -> endpoint.regions().stream())
                .map(RegionName::value)
                .collect(Collectors.toSet());
    }

    private static List<String> endpointIds(String endpointTag) {
        var xml = "<deployment>" +
                  "  <prod>" +
                  "    <region active=\"true\">us-east</region>" +
                  "  </prod>" +
                  "  <endpoints>" +
                  endpointTag +
                  "  </endpoints>" +
                  "</deployment>";

        return DeploymentSpec.fromXml(xml).endpoints().stream()
                             .map(Endpoint::endpointId)
                             .collect(Collectors.toList());
    }

}
