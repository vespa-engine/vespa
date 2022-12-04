// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.xml;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.ContainerCluster;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author baldersheim
 * @author gjoranv
 */
public class JvmOptionsTest extends ContainerModelBuilderTestBase {

    @Test
    void verify_jvm_tag_with_attributes() throws IOException, SAXException {
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
    void honours_jvm_gc_options() {
        Element clusterElem = DomBuilderTest.parse(
                "<container version='1.0'>",
                "  <search/>",
                "  <nodes jvm-gc-options='-XX:+UseG1GC'>",
                "    <node hostalias='mockhost'/>",
                "  </nodes>",
                "</container>");
        createModel(root, clusterElem);
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        root.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals("-XX:+UseG1GC", qrStartConfig.jvm().gcopts());
    }

    private static void verifyIgnoreJvmGCOptions(boolean isHosted) throws IOException, SAXException {
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes jvm-gc-options='-XX:+UseG1GC' jvm-options='-XX:+UseParNewGC'>" +
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
        assertEquals("-XX:+UseG1GC", qrStartConfig.jvm().gcopts());
    }

    @Test
    void ignores_jvmgcoptions_on_conflicting_jvmoptions() throws IOException, SAXException {
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
    void requireThatJvmGCOptionsIsHonoured()  throws IOException, SAXException {
        verifyJvmGCOptions(false, null, null, ContainerCluster.G1GC);
        verifyJvmGCOptions(true, null, null, ContainerCluster.PARALLEL_GC);
        verifyJvmGCOptions(true, "", null, ContainerCluster.PARALLEL_GC);
        verifyJvmGCOptions(false, "-XX:+UseG1GC", null, "-XX:+UseG1GC");
        verifyJvmGCOptions(true, "-XX:+UseG1GC", null, "-XX:+UseG1GC");
        verifyJvmGCOptions(false, null, "-XX:+UseG1GC", "-XX:+UseG1GC");
        verifyJvmGCOptions(false, "-XX:+UseParallelGC", "-XX:+UseG1GC", "-XX:+UseG1GC");
        verifyJvmGCOptions(false, null, "-XX:+UseParallelGC", "-XX:+UseParallelGC");
    }

    @Test
    void requireThatValidJvmGcOptionsAreNotLogged() throws IOException, SAXException {
        // Valid options, should not log anything
        verifyLoggingOfJvmGcOptions(true, "-XX:+ParallelGCThreads=8");
        verifyLoggingOfJvmGcOptions(true, "-XX:MaxTenuringThreshold=15"); // No + or - after colon
        verifyLoggingOfJvmGcOptions(false, "-XX:+UseConcMarkSweepGC");
    }

    @Test
    void requireThatInvalidJvmGcOptionsFailDeployment() throws IOException, SAXException {
        try {
            buildModelWithJvmOptions(new TestProperties().setHostedVespa(true),
                    new TestLogger(),
                    "gc-options",
                    "-XX:+ParallelGCThreads=8 foo     bar");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("Invalid or misplaced JVM GC options in services.xml: bar,foo"));
        }
    }

    private void verifyLoggingOfJvmGcOptions(boolean isHosted, String override, String... invalidOptions) throws IOException, SAXException  {
        verifyLoggingOfJvmOptions(isHosted, "gc-options", override, invalidOptions);
    }

    private void verifyLoggingOfJvmOptions(boolean isHosted, String optionName, String override, String... invalidOptions) throws IOException, SAXException  {
        TestLogger logger = new TestLogger();
        buildModelWithJvmOptions(isHosted, logger, optionName, override);

        List<String> strings = Arrays.asList(invalidOptions.clone());
        // Verify that nothing is logged if there are no invalid options
        if (strings.isEmpty()) {
            assertEquals(0, logger.msgs.size(), logger.msgs.size() > 0 ? logger.msgs.get(0).getSecond() : "");
            return;
        }

        assertTrue(logger.msgs.size() > 0, "Expected 1 or more log messages for invalid JM options, got none");
        Pair<Level, String> firstOption = logger.msgs.get(0);
        assertEquals(Level.WARNING, firstOption.getFirst());

        Collections.sort(strings);
        assertEquals("Invalid or misplaced JVM" + (optionName.equals("gc-options") ? " GC" : "") +
                             " options in services.xml: " + String.join(",", strings) + "." +
                             " See https://docs.vespa.ai/en/reference/services-container.html#jvm"
                             , firstOption.getSecond());
    }

    private void buildModelWithJvmOptions(boolean isHosted, TestLogger logger, String optionName, String override) throws IOException, SAXException {
        buildModelWithJvmOptions(new TestProperties().setHostedVespa(isHosted), logger, optionName, override);
    }

    private void buildModelWithJvmOptions(TestProperties properties, TestLogger logger, String optionName, String override) throws IOException, SAXException {
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes>" +
                        "    <jvm " + optionName + "='" + override + "'/>" +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        ApplicationPackage app = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(app)
                .deployLogger(logger)
                .properties(properties)
                .build());
    }

    @Test
    void requireThatValidJvmOptionsAreNotLogged() throws IOException, SAXException {
        // Valid options, should not log anything
        verifyLoggingOfJvmOptions(true, "options", "-Xms2G");
        verifyLoggingOfJvmOptions(true, "options", "-Xlog:gc");
        verifyLoggingOfJvmOptions(true, "options", "-Djava.library.path=/opt/vespa/lib64:/home/y/lib64");
        verifyLoggingOfJvmOptions(true, "options", "-XX:-OmitStackTraceInFastThrow");
        verifyLoggingOfJvmOptions(false, "options", "-Xms2G");
    }

    @Test
    void requireThatInvalidJvmOptionsFailDeployment() throws IOException, SAXException {
        try {
            buildModelWithJvmOptions(new TestProperties().setHostedVespa(true),
                    new TestLogger(),
                    "options",
                    "-Xms2G foo     bar");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid or misplaced JVM options in services.xml: bar,foo"));
        }
    }

}
