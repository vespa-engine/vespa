// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;

import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import org.junit.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author baldersheim
 * @author gjoranv
 */
public class JvmOptionsTest extends ContainerModelBuilderTestBase {

    @Test
    public void verify_jvm_tag_with_attributes() throws IOException, SAXException {
        String servicesXml =
                "<container version='1.0'>" +
                        "  <search/>" +
                        "  <nodes>" +
                        "    <jvm options='-XX:SoftRefLRUPolicyMSPerMB=2500' gc-options='-XX:+UseParNewGC' allocated-memory='45%'/>" +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        // Need to create VespaModel to make deploy properties have effect
        final TestLogger logger = new TestLogger();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .build());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        model.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals("-XX:+UseParNewGC", qrStartConfig.jvm().gcopts());
        assertEquals(45, qrStartConfig.jvm().heapSizeAsPercentageOfPhysicalMemory());
        assertEquals("-XX:SoftRefLRUPolicyMSPerMB=2500", model.getContainerClusters().values().iterator().next().getContainers().get(0).getJvmOptions());
    }
    @Test
    public void detect_conflicting_jvmgcoptions_in_jvmargs() {
        assertFalse(ContainerModelBuilder.incompatibleGCOptions(""));
        assertFalse(ContainerModelBuilder.incompatibleGCOptions("UseG1GC"));
        assertTrue(ContainerModelBuilder.incompatibleGCOptions("-XX:+UseG1GC"));
        assertTrue(ContainerModelBuilder.incompatibleGCOptions("abc -XX:+UseParNewGC xyz"));
        assertTrue(ContainerModelBuilder.incompatibleGCOptions("-XX:CMSInitiatingOccupancyFraction=19"));
    }

    @Test
    public void honours_jvm_gc_options() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <search/>",
                "  <nodes jvm-gc-options='-XX:+UseG1GC'>",
                "    <node hostalias='mockhost'/>",
                "  </nodes>",
                "</container>" );
        createModel(root, clusterElem);
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        root.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals("-XX:+UseG1GC", qrStartConfig.jvm().gcopts());
    }

    private static void verifyIgnoreJvmGCOptions(boolean isHosted) throws IOException, SAXException {
        verifyIgnoreJvmGCOptionsIfJvmArgs("jvmargs", ContainerCluster.G1GC, isHosted);
        verifyIgnoreJvmGCOptionsIfJvmArgs( "jvm-options", "-XX:+UseG1GC", isHosted);

    }
    private static void verifyIgnoreJvmGCOptionsIfJvmArgs(String jvmOptionsName, String expectedGC, boolean isHosted) throws IOException, SAXException {
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes jvm-gc-options='-XX:+UseG1GC' " + jvmOptionsName + "='-XX:+UseParNewGC'>" +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        // Need to create VespaModel to make deploy properties have effect
        final TestLogger logger = new TestLogger();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .properties(new TestProperties().setHostedVespa(isHosted))
                .build());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        model.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals(expectedGC, qrStartConfig.jvm().gcopts());
    }

    @Test
    public void ignores_jvmgcoptions_on_conflicting_jvmargs() throws IOException, SAXException {
        verifyIgnoreJvmGCOptions(false);
        verifyIgnoreJvmGCOptions(true);
    }

    private void verifyJvmGCOptions(boolean isHosted, String featureFlagDefault, String override, String expected) throws IOException, SAXException  {
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes " + ((override == null) ? ">" : ("jvm-gc-options='" + override + "'>")) +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        // Need to create VespaModel to make deploy properties have effect
        final TestLogger logger = new TestLogger();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .properties(new TestProperties().setJvmGCOptions(featureFlagDefault).setHostedVespa(isHosted))
                .build());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        model.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals(expected, qrStartConfig.jvm().gcopts());
    }

    @Test
    public void requireThatJvmGCOptionsIsHonoured()  throws IOException, SAXException {
        verifyJvmGCOptions(false, null,null, ContainerCluster.G1GC);
        verifyJvmGCOptions(true, null,null, ContainerCluster.CMS);
        verifyJvmGCOptions(true, "",null, ContainerCluster.CMS);
        verifyJvmGCOptions(false, "-XX:+UseConcMarkSweepGC",null, "-XX:+UseConcMarkSweepGC");
        verifyJvmGCOptions(true, "-XX:+UseConcMarkSweepGC",null, "-XX:+UseConcMarkSweepGC");
        verifyJvmGCOptions(false, null,"-XX:+UseG1GC", "-XX:+UseG1GC");
        verifyJvmGCOptions(false, "-XX:+UseConcMarkSweepGC","-XX:+UseG1GC", "-XX:+UseG1GC");
        verifyJvmGCOptions(false, null,"-XX:+UseConcMarkSweepGC", "-XX:+UseConcMarkSweepGC");
    }

}
