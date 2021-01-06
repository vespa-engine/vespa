package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author valerijf
 * @author jonmv
 */
public class ApplicationPackageTest {

    private static final String deploymentXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                                "<deployment version=\"1.0\">\n" +
                                                "    <test />\n" +
                                                "    <prod>\n" +
                                                "        <parallel>\n" +
                                                "            <region active=\"true\">us-central-1</region>\n" +
                                                "        </parallel>\n" +
                                                "    </prod>\n" +
                                                "</deployment>\n";

    private static final String servicesXml = "<services version='1.0' xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\">\n" +
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
            Assert.fail("Should fail on missing include file");
        }
        catch (RuntimeException e) {
            assertEquals("./jdisc.xml", e.getCause().getMessage());
        }
    }

    private static Map<String, String> unzip(byte[] zip) {
        return new ZipStreamReader(new ByteArrayInputStream(zip), __ -> true, 1 << 10)
                .entries().stream()
                .collect(Collectors.toMap(entry -> entry.zipEntry().getName(),
                                          entry -> new String(entry.content(), UTF_8)));
    }

}
