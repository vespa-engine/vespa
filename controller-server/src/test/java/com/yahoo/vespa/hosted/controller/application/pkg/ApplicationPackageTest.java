// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author valerijf
 * @author jonmv
 */
public class ApplicationPackageTest {

    static final String deploymentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                        "<deployment version=\"1.0\">\n" +
                                        "    <test />\n" +
                                        "    <prod>\n" +
                                        "        <parallel>\n" +
                                        "            <region active=\"true\">us-central-1</region>\n" +
                                        "        </parallel>\n" +
                                        "    </prod>\n" +
                                        "</deployment>\n";

    static final String servicesXml = "<services version='1.0' xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\">\n" +
                                      "    <preprocess:include file='jdisc.xml' />\n" +
                                      "    <content version='1.0' if='foo' />\n" +
                                      "    <content version='1.0' id='foo' deploy:environment='staging prod' deploy:region='us-east-3 us-central-1'>\n" +
                                      "        <preprocess:include file='content/content.xml' />\n" +
                                      "    </content>\n" +
                                      "    <preprocess:include file='not_found.xml' required='false' />\n" +
                                      "</services>\n";

    private static final String jdiscXml = "<container id='stateless' version='1.0' />\n";

    private static final String contentXml = "<documents>\n" +
                                             "    <document type=\"music.sd\" mode=\"index\" />\n" +
                                             "</documents>\n" +
                                             "<preprocess:include file=\"nodes.xml\" />";

    private static final String nodesXml = "<nodes>\n" +
                                           "    <node hostalias=\"node0\" distribution-key=\"0\" />\n" +
                                           "</nodes>";

    @Test
    public void test_createEmptyForDeploymentRemoval() {
        ApplicationPackage app = ApplicationPackage.deploymentRemoval();
        assertEquals(DeploymentSpec.empty, app.deploymentSpec());
        assertEquals(List.of(), app.trustedCertificates());

        for (ValidationId validationId : ValidationId.values()) {
            assertTrue(app.validationOverrides().allows(validationId, Instant.now()));
        }
    }

    @Test
    public void testMetaData() {
        byte[] zip = ApplicationPackage.filesZip(Map.of("services.xml", servicesXml.getBytes(UTF_8),
                                                        "jdisc.xml", jdiscXml.getBytes(UTF_8),
                                                        "content/content.xml", contentXml.getBytes(UTF_8),
                                                        "content/nodes.xml", nodesXml.getBytes(UTF_8),
                                                        "gurba", "gurba".getBytes(UTF_8)));

        assertEquals(Map.of("services.xml", servicesXml,
                            "jdisc.xml", jdiscXml,
                            "content/content.xml", contentXml,
                            "content/nodes.xml", nodesXml),
                     unzip(new ApplicationPackage(zip, false).metaDataZip()));
    }

    @Test
    public void testMetaDataWithLegacyApplicationDirectory() {
        byte[] zip = ApplicationPackage.filesZip(Map.of("application/deployment.xml", deploymentXml.getBytes(UTF_8),
                                                        "application/services.xml", servicesXml.getBytes(UTF_8),
                                                        "application/jdisc.xml", jdiscXml.getBytes(UTF_8),
                                                        "application/content/content.xml", contentXml.getBytes(UTF_8),
                                                        "application/content/nodes.xml", nodesXml.getBytes(UTF_8),
                                                        "application/gurba", "gurba".getBytes(UTF_8)));

        assertEquals(Map.of("deployment.xml", deploymentXml,
                            "services.xml", servicesXml,
                            "jdisc.xml", jdiscXml,
                            "content/content.xml", contentXml,
                            "content/nodes.xml", nodesXml),
                     unzip(new ApplicationPackage(zip, false).metaDataZip()));
    }

    @Test
    public void testMetaDataWithMissingFiles() {
        byte[] zip = ApplicationPackage.filesZip(Map.of("services.xml", servicesXml.getBytes(UTF_8)));

        try {
            new ApplicationPackage(zip, false).metaDataZip();
            fail("Should fail on missing include file");
        }
        catch (RuntimeException e) {
            assertEquals("./jdisc.xml", e.getCause().getMessage());
        }
    }

    @Test
    public void testAbsoluteInclude() throws Exception {
        try {
            getApplicationZip("include-absolute.zip");
            fail("Should fail on include file outside zip");
        }
        catch (RuntimeException e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    @Test
    public void testParentInclude() throws Exception {
        try {
            getApplicationZip("include-parent.zip");
            fail("Should fail on include file outside zip");
        }
        catch (RuntimeException e) {
            assertEquals("./../not_found.xml is not a descendant of .", e.getMessage());
        }
    }

    @Test
    public void testBundleHashesAreSameWithDifferentDeploymentXml() throws Exception {
        var originalPackage = getApplicationZip("original.zip");
        var changedServices = getApplicationZip("changed-services-xml.zip");
        var changedDeploymentXml = getApplicationZip("changed-deployment-xml.zip");
        var similarDeploymentXml = getApplicationZip("similar-deployment-xml.zip");

        // services.xml is changed -> different bundle hash
        assertNotEquals(originalPackage.bundleHash(), changedServices.bundleHash());
        assertNotEquals(originalPackage.hash(), changedServices.hash());

        // deployment.xml is changed, with real changes -> different bundle hash
        assertNotEquals(originalPackage.bundleHash(), changedDeploymentXml.bundleHash());
        assertNotEquals(originalPackage.hash(), changedDeploymentXml.hash());

        // deployment.xml is changed, but only deployment orchestration settings -> same bundle hash
        assertEquals(originalPackage.bundleHash(), similarDeploymentXml.bundleHash());
        assertNotEquals(originalPackage.hash(), similarDeploymentXml.hash());
    }

    private static Map<String, String> unzip(byte[] zip) {
        return ZipEntries.from(zip, __ -> true, 1 << 10, true)
                         .asList().stream()
                         .collect(Collectors.toMap(ZipEntries.ZipEntryWithContent::name,
                                          entry -> new String(entry.contentOrThrow(), UTF_8)));
    }

    private ApplicationPackage getApplicationZip(String path) throws Exception {
        return new ApplicationPackage(Files.readAllBytes(Path.of("src/test/resources/application-packages/" + path)), true);
    }

}
