// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.Optional;

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
        assertTrue(spec.steps().get(0).deploysTo(Environment.test));
        assertTrue(spec.includes(Environment.test, Optional.empty()));
        assertFalse(spec.includes(Environment.test, Optional.of(RegionName.from("region1"))));
        assertFalse(spec.includes(Environment.staging, Optional.empty()));
        assertFalse(spec.includes(Environment.prod, Optional.empty()));
        assertFalse(spec.globalServiceId().isPresent());
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
        assertFalse(((DeploymentSpec.ZoneDeployment)spec.steps().get(2)).active());

        assertTrue(spec.steps().get(3).deploysTo(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.ZoneDeployment)spec.steps().get(3)).active());

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
        "   <test/>" +
        "   <staging/>" +
        "   <prod>" +
        "      <region active='false'>us-east1</region>" +
        "      <region active='false'>us-east1</region>" +
        "      <delay hours='3' minutes='30'/>" + 
        "      <region active='true'>us-west1</region>" +
        "   </prod>" +
        "</deployment>"
        );

        DeploymentSpec spec = DeploymentSpec.fromXml(r);
        assertEquals(5, spec.steps().size());

        assertTrue(spec.steps().get(0).deploysTo(Environment.test));

        assertTrue(spec.steps().get(1).deploysTo(Environment.staging));

        assertTrue(spec.steps().get(2).deploysTo(Environment.prod, Optional.of(RegionName.from("us-east1"))));
        assertFalse(((DeploymentSpec.ZoneDeployment)spec.steps().get(2)).active());

        assertTrue(spec.steps().get(3) instanceof DeploymentSpec.Delay);
        assertEquals(3 * 60 * 60 + 30 * 60, ((DeploymentSpec.Delay)spec.steps().get(3)).duration().getSeconds());

        assertTrue(spec.steps().get(4).deploysTo(Environment.prod, Optional.of(RegionName.from("us-west1"))));
        assertTrue(((DeploymentSpec.ZoneDeployment)spec.steps().get(4)).active());

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

}
