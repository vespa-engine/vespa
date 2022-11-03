// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SignatureAlgorithm;
import com.yahoo.security.X509CertificateBuilder;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage.filesZip;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author valerijf
 * @author jonmv
 */
public class ApplicationPackageTest {

    static final String deploymentXml = """
                                        <?xml version="1.0" encoding="UTF-8"?>
                                        <deployment version="1.0">
                                            <test />
                                            <prod>
                                                <parallel>
                                                    <region active="true">us-central-1</region>
                                                </parallel>
                                            </prod>
                                        </deployment>
                                        """;

    static final String servicesXml = """
                                      <services version='1.0' xmlns:deploy="vespa" xmlns:preprocess="properties">
                                          <preprocess:include file='jdisc.xml' />
                                          <content version='1.0' if='foo' />
                                          <content version='1.0' id='foo' deploy:environment='staging prod' deploy:region='us-east-3 us-central-1'>
                                              <preprocess:include file='content/content.xml' />
                                          </content>
                                          <preprocess:include file='not_found.xml' required='false' />
                                      </services>
                                      """;

    private static final String jdiscXml = "<container id='stateless' version='1.0' />\n";

    private static final String contentXml = """
                                             <documents>
                                                 <document type="music.sd" mode="index" />
                                             </documents>
                                             <preprocess:include file="nodes.xml" />""";

    private static final String nodesXml = """
                                           <nodes>
                                               <node hostalias="node0" distribution-key="0" />
                                           </nodes>""";

    @Test
    void test_createEmptyForDeploymentRemoval() {
        ApplicationPackage app = ApplicationPackage.deploymentRemoval();
        assertEquals(DeploymentSpec.empty, app.deploymentSpec());
        assertEquals(List.of(), app.trustedCertificates());

        for (ValidationId validationId : ValidationId.values()) {
            assertTrue(app.validationOverrides().allows(validationId, Instant.now()));
        }
    }

    @Test
    void testMetaData() {
        byte[] zip = filesZip(Map.of("services.xml", servicesXml.getBytes(UTF_8),
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
    void testMetaDataWithMissingFiles() {
        byte[] zip = filesZip(Map.of("services.xml", servicesXml.getBytes(UTF_8)));

        try {
            new ApplicationPackage(zip, false).metaDataZip();
            fail("Should fail on missing include file");
        }
        catch (RuntimeException e) {
            assertEquals("./jdisc.xml", e.getCause().getMessage());
        }
    }

    @Test
    void testAbsoluteInclude() throws Exception {
        try {
            getApplicationZip("include-absolute.zip");
            fail("Should fail on include file outside zip");
        }
        catch (RuntimeException e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    @Test
    void testParentInclude() throws Exception {
        try {
            getApplicationZip("include-parent.zip");
            fail("Should fail on include file outside zip");
        }
        catch (RuntimeException e) {
            assertEquals("./../not_found.xml is not a descendant of .", e.getMessage());
        }
    }

    @Test
    void testBundleHashesAreSameWithDifferentDeploymentXml() throws Exception {
        var originalPackage = getApplicationZip("original.zip");
        var changedServices = getApplicationZip("changed-services-xml.zip");
        var changedDeploymentXml = getApplicationZip("changed-deployment-xml.zip");
        var similarDeploymentXml = getApplicationZip("similar-deployment-xml.zip");

        // services.xml is changed -> different bundle hash
        assertNotEquals(originalPackage.bundleHash(), changedServices.bundleHash());

        // deployment.xml is changed, with real changes -> different bundle hash
        assertNotEquals(originalPackage.bundleHash(), changedDeploymentXml.bundleHash());

        // deployment.xml is changed, but only deployment orchestration settings -> same bundle hash
        assertEquals(originalPackage.bundleHash(), similarDeploymentXml.bundleHash());
    }

    private static Map<String, String> unzip(byte[] zip) {
        return ZipEntries.from(zip, __ -> true, 1 << 10, true)
                         .asList().stream()
                         .collect(Collectors.toMap(ZipEntries.ZipEntryWithContent::name,
                                          entry -> new String(entry.contentOrThrow(), UTF_8)));
    }

    private ApplicationPackage getApplicationZip(String path) throws IOException {
        return new ApplicationPackage(Files.readAllBytes(Path.of("src/test/resources/application-packages/" + path)), true);
    }

    @Test
    void test_replacement() {
        ApplicationPackage applicationPackage = new ApplicationPackage(new byte[0]);
        List<X509Certificate> certificates = IntStream.range(0, 3)
                                                      .mapToObj(i -> {
                                                          KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
                                                          X500Principal subject = new X500Principal("CN=subject" + i);
                                                          return X509CertificateBuilder.fromKeypair(keyPair,
                                                                                                    subject,
                                                                                                    Instant.now(),
                                                                                                    Instant.now().plusSeconds(1),
                                                                                                    SignatureAlgorithm.SHA512_WITH_ECDSA,
                                                                                                    BigInteger.valueOf(1))
                                                                                       .build();
                                                      }).toList();

        assertEquals(List.of(), applicationPackage.trustedCertificates());
        for (int i = 0; i < certificates.size(); i++) {
            applicationPackage = applicationPackage.withTrustedCertificate(certificates.get(i));
            assertEquals(certificates.subList(0, i + 1), applicationPackage.trustedCertificates());
        }
    }
}
