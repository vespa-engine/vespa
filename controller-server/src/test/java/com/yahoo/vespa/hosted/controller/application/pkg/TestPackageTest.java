package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterId;
import com.yahoo.vespa.hosted.controller.application.pkg.TestPackage.TestSummary;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig;
import com.yahoo.vespa.hosted.controller.config.ControllerConfig.Steprunner.Testerapp;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static com.yahoo.config.provision.CloudName.AWS;
import static com.yahoo.config.provision.CloudName.DEFAULT;
import static com.yahoo.config.provision.CloudName.GCP;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.production;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.staging;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.staging_setup;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Suite.system;
import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackageTest.unzip;
import static com.yahoo.vespa.hosted.controller.application.pkg.TestPackage.validateTests;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class TestPackageTest {

    static byte[] testsJar(String... suites) throws IOException {
        String manifest = "Manifest-Version: 1.0\n" +
                          "Created-By: vespa container maven plugin\n" +
                          "Build-Jdk-Spec: 17\n" +
                          "Bundle-ManifestVersion: 2\n" +
                          "Bundle-SymbolicName: canary-application-test\n" +
                          "Bundle-Version: 1.0.1\n" +
                          "Bundle-Name: Test & verification application for Vespa\n" +
                          "X-JDisc-Test-Bundle-Version: 1.0\n" +
                          "Bundle-Vendor: Yahoo!\n" +
                          "Bundle-ClassPath: .,dependencies/fest-assert-1.4.jar,dependencies/fest-u\n" +
                          " til-1.1.6.jar\n" +
                          "Import-Package: ai.vespa.feed.client;version=\"[1.0.0,2)\",ai.vespa.hosted\n" +
                          " .cd;version=\"[1.0.0,2)\",com.yahoo.config;version=\"[1.0.0,2)\",com.yahoo.\n" +
                          " container.jdisc;version=\"[1.0.0,2)\",com.yahoo.jdisc.http;version=\"[1.0.\n" +
                          " 0,2)\",com.yahoo.slime;version=\"[1.0.0,2)\",java.awt.image;version=\"[0.0.\n" +
                          " 0,1)\",java.awt;version=\"[0.0.0,1)\",java.beans;version=\"[0.0.0,1)\",java.\n" +
                          " io;version=\"[0.0.0,1)\",java.lang.annotation;version=\"[0.0.0,1)\",java.la\n" +
                          " ng.reflect;version=\"[0.0.0,1)\",java.lang;version=\"[0.0.0,1)\",java.math;\n" +
                          " version=\"[0.0.0,1)\",java.net.http;version=\"[0.0.0,1)\",java.net;version=\n" +
                          " \"[0.0.0,1)\",java.nio.file;version=\"[0.0.0,1)\",java.security;version=\"[0\n" +
                          " .0.0,1)\",java.text;version=\"[0.0.0,1)\",java.time.temporal;version=\"[0.0\n" +
                          " .0,1)\",java.time;version=\"[0.0.0,1)\",java.util.concurrent;version=\"[0.0\n" +
                          " .0,1)\",java.util.function;version=\"[0.0.0,1)\",java.util.stream;version=\n" +
                          " \"[0.0.0,1)\",java.util;version=\"[0.0.0,1)\",javax.imageio;version=\"[0.0.0\n" +
                          " ,1)\",org.junit.jupiter.api;version=\"[5.8.1,6)\"\n" +
                          "X-JDisc-Test-Bundle-Categories: " + String.join(",", suites) + "\n" +
                          "\n";

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(buffer)) {
            write("META-INF/MANIFEST.MF", manifest, out);
            write("dependencies/foo.jar", "bar", out);
            write("META-INF/maven/ai.vespa.test/app/pom.xml", "<project />", out);
            write("ai/vespa/test/Test.class", "baz", out);
        }
        return buffer.toByteArray();
    }

    static void write(String name, String content, JarOutputStream out) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content.getBytes(UTF_8));
        out.closeEntry();
    }

    @Test
    void testBundleValidation() throws IOException {
        byte[] testZip = ApplicationPackage.filesZip(Map.of("components/foo-tests.jar", testsJar("SystemTest", "StagingSetup", "ProductionTest"),
                                                            "artifacts/key", new byte[0]));
        TestSummary summary = validateTests(List.of(system), testZip);

        assertEquals(List.of(system, staging_setup, production), summary.suites());
        assertEquals(List.of("test package contains 'artifacts/key'; this conflicts with credentials used to run tests in Vespa Cloud",
                             "test package has staging setup, so it should also include staging tests",
                             "test package has production tests, but no production tests are declared in deployment.xml",
                             "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"),
                     summary.problems());
    }

    @Test
    void testFatTestsValidation() {
        byte[] testZip = ApplicationPackage.filesZip(Map.of("artifacts/foo-tests.jar", new byte[0]));
        TestSummary summary = validateTests(List.of(staging, production), testZip);

        assertEquals(List.of(staging, production), summary.suites());
        assertEquals(List.of("test package has staging tests, so it should also include staging setup",
                             "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"),
                     summary.problems());
    }

    @Test
    void testBasicTestsValidation() {
        byte[] testZip = ApplicationPackage.filesZip(Map.of("tests/staging-test/foo.json", new byte[0],
                                                            "tests/staging-setup/foo.json", new byte[0]));
        TestSummary summary = validateTests(List.of(system, production), testZip);
        assertEquals(List.of(staging_setup, staging), summary.suites());
        assertEquals(List.of("test package has no system tests, but <test /> is declared in deployment.xml",
                             "test package has no production tests, but production tests are declared in deployment.xml",
                             "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"),
                     summary.problems());
    }

    @Test
    void testTestPackageAssembly() throws IOException {
        byte[] bundleZip = ApplicationPackage.filesZip(Map.of("components/foo-tests.jar", testsJar("SystemTest", "ProductionTest"),
                                                              "artifacts/key", new byte[0]));
        TestPackage bundleTests = new TestPackage(() -> new ByteArrayInputStream(bundleZip),
                                                  false,
                                                  CloudName.DEFAULT,
                                                  new RunId(ApplicationId.defaultId(), JobType.dev("abc"), 123),
                                                  new Testerapp.Builder().tenantCdBundle("foo").runtimeProviderClass("bar").build(),
                                                  DeploymentSpec.fromXml("""
                                                                         <deployment>
                                                                           <test />
                                                                         </deployment>
                                                                         """),
                                                  null,
                                                  null);

        Map<String, String> bundlePackage = unzip(bundleTests.asApplicationPackage().zipStream().readAllBytes());
        bundlePackage.keySet().removeIf(name -> name.startsWith("tests/.ignore") || name.startsWith("artifacts/.ignore"));
        assertEquals(Set.of("deployment.xml",
                            "services.xml",
                            "components/foo-tests.jar",
                            "artifacts/key"),
                     bundlePackage.keySet());
        assertEquals(Map.of(),
                     unzip(bundleTests.asApplicationPackage().truncatedPackage().zippedContent()));
    }

    @Test
    void generates_correct_deployment_spec() {
        DeploymentSpec spec = DeploymentSpec.fromXml("""
                                                     <deployment version='1.0' athenz-domain='domain' athenz-service='service' cloud-account='123123123123,gcp:foobar' empty-host-ttl='1h'>
                                                         <test empty-host-ttl='1d' />
                                                         <staging cloud-account='aws:321321321321'/>
                                                         <prod>
                                                             <region>us-east-3</region>
                                                             <test>us-east-3</test>
                                                             <region>us-west-1</region>
                                                             <test empty-host-ttl='0m'>us-west-1</test>
                                                             <region empty-host-ttl='1d'>us-central-1</region>
                                                             <test>us-central-1</test>
                                                         </prod>
                                                     </deployment>
                                                     """);
        verifyAttributes("", 0, DEFAULT, ZoneId.from("test", "us-east-1"), spec);
        verifyAttributes("", 0, DEFAULT, ZoneId.from("staging", "us-east-2"), spec);
        verifyAttributes("", 0, DEFAULT, ZoneId.from("prod", "us-east-3"), spec);
        verifyAttributes("", 0, DEFAULT, ZoneId.from("prod", "us-west-1"), spec);
        verifyAttributes("", 0, DEFAULT, ZoneId.from("prod", "us-central-1"), spec);

        verifyAttributes("aws:123123123123", 1440, AWS, ZoneId.from("test", "us-east-1"), spec);
        verifyAttributes("aws:321321321321", 60, AWS, ZoneId.from("staging", "us-east-2"), spec);
        verifyAttributes("aws:123123123123", 60, AWS, ZoneId.from("prod", "us-east-3"), spec);
        verifyAttributes("aws:123123123123", 0, AWS, ZoneId.from("prod", "us-west-1"), spec);
        verifyAttributes("aws:123123123123", 60, AWS, ZoneId.from("prod", "us-central-1"), spec);

        verifyAttributes("gcp:foobar", 1440, GCP, ZoneId.from("test", "us-east-1"), spec);
        verifyAttributes("", 0, GCP, ZoneId.from("staging", "us-east-2"), spec);
        verifyAttributes("gcp:foobar", 60, GCP, ZoneId.from("prod", "us-east-3"), spec);
        verifyAttributes("gcp:foobar", 0, GCP, ZoneId.from("prod", "us-west-1"), spec);
        verifyAttributes("gcp:foobar", 60, GCP, ZoneId.from("prod", "us-central-1"), spec);
    }

    private void verifyAttributes(String expectedAccount, int expectedTTL, CloudName cloud, ZoneId zone, DeploymentSpec spec) {
        assertEquals("<?xml version='1.0' encoding='UTF-8'?>\n" +
                     "<deployment version='1.0' athenz-domain='domain' athenz-service='service'" +
                     (expectedAccount.isEmpty() ? "" : " cloud-account='" + expectedAccount + "' empty-host-ttl='" + expectedTTL + "m'") + ">  " +
                     "<instance id='default-t' /></deployment>",
                     new String(TestPackage.deploymentXml(TesterId.of(ApplicationId.defaultId()), InstanceName.defaultName(), cloud, zone, spec)));
    }

    @Test
    void generates_correct_tester_flavor() {
        DeploymentSpec spec = DeploymentSpec.fromXml("""
                                                     <deployment version='1.0' athenz-domain='domain' athenz-service='service'>
                                                         <instance id='first'>
                                                             <test tester-flavor="d-6-16-100" />
                                                             <prod>
                                                                 <region active="true">us-west-1</region>
                                                                 <test>us-west-1</test>
                                                             </prod>
                                                         </instance>
                                                         <instance id='second'>
                                                             <test />
                                                             <staging />
                                                             <prod tester-flavor="d-6-16-100">
                                                                 <parallel>
                                                                     <region active="true">us-east-3</region>
                                                                     <region active="true">us-central-1</region>
                                                                 </parallel>
                                                                 <region active="true">us-west-1</region>
                                                                 <test>us-west-1</test>
                                                             </prod>
                                                         </instance>
                                                     </deployment>
                                                     """);

        NodeResources firstResources = TestPackage.testerResourcesFor(ZoneId.from("prod", "us-west-1"), spec.requireInstance("first"));
        assertEquals(TestPackage.DEFAULT_TESTER_RESOURCES, firstResources);

        NodeResources secondResources = TestPackage.testerResourcesFor(ZoneId.from("prod", "us-west-1"), spec.requireInstance("second"));
        assertEquals(6, secondResources.vcpu(), 1e-9);
        assertEquals(16, secondResources.memoryGb(), 1e-9);
        assertEquals(100, secondResources.diskGb(), 1e-9);
    }

    @Test
    void generates_correct_services_xml() throws IOException {
        assertEquals(Files.readString(Paths.get("src/test/resources/test_runner_services.xml-cd")),
                     new String(TestPackage.servicesXml(true,
                                                        false,
                                                        false,
                                                        new NodeResources(2, 12, 75, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.local),
                                                        new ControllerConfig.Steprunner.Testerapp.Builder().build()),
                                UTF_8));

        assertEquals(Files.readString(Paths.get("src/test/resources/test_runner_services_with_legacy_tests.xml-cd")),
                new String(TestPackage.servicesXml(true,
                                false,
                                true,
                                new NodeResources(2, 12, 75, 1, NodeResources.DiskSpeed.fast, NodeResources.StorageType.local),
                                new ControllerConfig.Steprunner.Testerapp.Builder().build()),
                        UTF_8));
    }

}
