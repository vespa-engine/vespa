// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.xml;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ContainerEndpoint;
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
import java.util.Set;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        Set<ContainerEndpoint> endpoints = isHosted ? Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))) : Set.of();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .endpoints(endpoints)
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
        Set<ContainerEndpoint> endpoints = isHosted ? Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))) : Set.of();
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .deployLogger(logger)
                .endpoints(endpoints)
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
    }

    @Test
    void requireThatDeprecatedJvmOptionsAreLogged() throws IOException, SAXException {
        String optionName = "jvm-options";
        verifyLoggingOfLegacyJvmOptions(true, optionName, "-XX:+ParallelGCThreads=8", optionName);
        verifyLoggingOfLegacyJvmOptions(false, optionName, "-XX:+ParallelGCThreads=8", optionName);
    }

    @Test
    void requireThatDeprecatedJvmOptionsAreLogged_2() throws IOException, SAXException {
        String optionName = "allocated-memory";
        verifyLoggingOfLegacyJvmOptions(true, optionName, "50%", optionName);
        verifyLoggingOfLegacyJvmOptions(false, optionName, "50%", optionName);
    }

    @Test
    void requireThatDeprecatedJvmGcOptionsAreLogged() throws IOException, SAXException {
        String optionName = "jvm-gc-options";
        verifyLoggingOfLegacyJvmOptions(true, optionName, "-XX:+ParallelGCThreads=8", optionName);
        verifyLoggingOfLegacyJvmOptions(false, optionName, "-XX:+ParallelGCThreads=8", optionName);
    }

    private void verifyThatInvalidJvmGcOptionsFailDeployment(String options, String expected) throws IOException, SAXException {
        try {
            buildModelWithJvmOptions(new TestProperties().setHostedVespa(true), "gc-options", options);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith(expected));
        }
    }

    @Test
    void requireThatInvalidJvmGcOptionsFailDeployment() throws IOException, SAXException {
        verifyThatInvalidJvmGcOptionsFailDeployment(
                "-XX:+ParallelGCThreads=8 foo     bar",
                "Invalid or misplaced JVM GC options in services.xml: bar,foo");
        verifyThatInvalidJvmGcOptionsFailDeployment(
                "-XX:+UseConcMarkSweepGC",
                "Invalid or misplaced JVM GC options in services.xml: -XX:+UseConcMarkSweepGC");
    }

    @Test
    void verify_no_option_no_nodes_element_gives_value_from_feature_flag() throws IOException, SAXException {
        String servicesXml = """
                <container version='1.0'>
                  <search/>
                </container>
                """;
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        // Need to create VespaModel to make deploy properties have effect
        VespaModel model = new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(applicationPackage)
                .properties(new TestProperties().setJvmGCOptions("-XX:+UseParNewGC"))
                .build());
        QrStartConfig.Builder qrStartBuilder = new QrStartConfig.Builder();
        model.getConfig(qrStartBuilder, "container/container.0");
        QrStartConfig qrStartConfig = new QrStartConfig(qrStartBuilder);
        assertEquals("-XX:+UseParNewGC", qrStartConfig.jvm().gcopts());
    }

    private void verifyLoggingOfJvmGcOptions(boolean isHosted, String override, String... invalidOptions) throws IOException, SAXException  {
        verifyLogMessage(isHosted, "gc-options", override, invalidOptions);
    }

    private void verifyLogMessage(boolean isHosted, String optionName, String override, String... invalidOptions) throws IOException, SAXException  {
        var logger = buildModelWithJvmOptions(isHosted, optionName, override);
        var message = verifyLogMessage(logger, invalidOptions);
        if (message != null)
            assertTrue(message.contains("Invalid or misplaced JVM"), message);
    }

    private String verifyLogMessage(TestLogger logger, String... invalidOptions) {
        List<String> strings = Arrays.asList(invalidOptions.clone());
        // Verify that nothing is logged if there are no invalid options
        if (strings.isEmpty()) {
            assertEquals(0, logger.msgs.size(), logger.msgs.size() > 0 ? logger.msgs.get(0).getSecond() : "");
            return null;
        }

        assertTrue(logger.msgs.size() > 0, "Expected 1 or more log messages for invalid JM options, got none");
        Pair<Level, String> firstOption = logger.msgs.get(0);
        assertEquals(Level.WARNING, firstOption.getFirst());

        Collections.sort(strings);
        return firstOption.getSecond();
    }

    private TestLogger buildModelWithJvmOptions(boolean isHosted, String optionName, String override) throws IOException, SAXException {
        return buildModelWithJvmOptions(new TestProperties().setHostedVespa(isHosted), optionName, override);
    }

    private TestLogger buildModelWithJvmOptions(TestProperties properties, String optionName, String override) throws IOException, SAXException {
        TestLogger logger = new TestLogger();
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes>" +
                        "    <jvm " + optionName + "='" + override + "'/>" +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        buildModel(properties, logger, servicesXml);
        return logger;
    }

    private void verifyLoggingOfLegacyJvmOptions(boolean isHosted, String optionName, String override, String... invalidOptions) throws IOException, SAXException  {
        var logger = buildModelWithLegacyJvmOptions(isHosted, optionName, override);

        var message = verifyLogMessage(logger, invalidOptions);
        if (message != null)
            assertTrue(message.contains("'" + optionName + "' is deprecated and will be removed"), message);
    }

    private TestLogger buildModelWithLegacyJvmOptions(boolean isHosted, String optionName, String override) throws IOException, SAXException {
        TestProperties properties = new TestProperties().setHostedVespa(isHosted);
        TestLogger logger = new TestLogger();
        String servicesXml =
                "<container version='1.0'>" +
                        "  <nodes " + optionName + "='" + override + "'>" +
                        "    <node hostalias='mockhost'/>" +
                        "  </nodes>" +
                        "</container>";
        buildModel(properties, logger, servicesXml);
        return logger;
    }

    private void buildModel(TestProperties properties, TestLogger logger, String servicesXml) throws IOException, SAXException {
        ApplicationPackage app = new MockApplicationPackage.Builder().withServices(servicesXml).build();
        new VespaModel(new NullConfigModelRegistry(), new DeployState.Builder()
                .applicationPackage(app)
                .deployLogger(logger)
                .endpoints(properties.hostedVespa() ? Set.of(new ContainerEndpoint("container", ApplicationClusterEndpoint.Scope.zone, List.of("default.example.com"))) : Set.of())
                .properties(properties)
                .build());
    }

    @Test
    void requireThatValidJvmOptionsAreNotLogged() throws IOException, SAXException {
        // Valid options, should not log anything
        verifyLogMessage(true, "options", "-Xms2G");
        verifyLogMessage(true, "options", "-Xlog:gc");
        verifyLogMessage(true, "options", "-Djava.library.path=/opt/vespa/lib64:/home/y/lib64");
        verifyLogMessage(true, "options", "-XX:-OmitStackTraceInFastThrow");
        verifyLogMessage(false, "options", "-Xms2G");
    }

    @Test
    void requireThatInvalidJvmOptionsFailDeployment() throws IOException, SAXException {
        try {
            buildModelWithJvmOptions(new TestProperties().setHostedVespa(true),
                    "options",
                    "-Xms2G foo     bar");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid or misplaced JVM options in services.xml: bar,foo"));
        }
    }

}
