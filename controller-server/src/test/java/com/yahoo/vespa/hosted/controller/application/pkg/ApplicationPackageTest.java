// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.io.LazyInputStream;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage.filesZip;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                     unzip(new ApplicationPackage(zip).metaDataZip()));
    }

    @Test
    void testMetaDataWithMissingFiles() {
        byte[] zip = filesZip(Map.of("services.xml", servicesXml.getBytes(UTF_8)));

        try {
            new ApplicationPackage(zip).metaDataZip();
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

    @Test
    void testCertificateFileExists() throws Exception {
        getApplicationZip("with-certificate.zip", true);
    }

    @Test
    void testCertificateFileMissing() throws Exception {
        try {
            getApplicationZip("original.zip", true);
            fail("Should fail on missing certificate file file");
        } catch (RuntimeException e) {
            assertEquals("No client certificate found in security/ in application package, see https://cloud.vespa.ai/en/security/guide", e.getMessage());
        }
    }

    static Map<String, String> unzip(byte[] zip) {
        return ZipEntries.from(zip, __ -> true, 1 << 24, true)
                         .asList().stream()
                         .collect(Collectors.toMap(ZipEntries.ZipEntryWithContent::name,
                                                   entry -> new String(entry.content().orElse(new byte[0]), UTF_8)));
    }

    private ApplicationPackage getApplicationZip(String path) throws IOException {
        return getApplicationZip(path, false);
    }

    private ApplicationPackage getApplicationZip(String path, boolean checkCertificateFile) throws IOException {
        return new ApplicationPackage(Files.readAllBytes(Path.of("src/test/resources/application-packages/" + path)), true, checkCertificateFile);
    }

    static byte[] zip(Map<String, String> content) {
        return filesZip(content.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(),
                                                                             entry -> entry.getValue().getBytes(UTF_8))));
    }

    private static class AngryStreams {

        private final byte[] content;
        private final Map<ByteArrayInputStream, Throwable> streams = new LinkedHashMap<>();

        AngryStreams(byte[] content) {
            this.content = content;
        }

        InputStream stream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(Arrays.copyOf(content, content.length)) {
                boolean closed = false;
                @Override public void close() { closed = true; }
                @Override public int read() { assertFalse(closed); return super.read(); }
                @Override public int read(byte[] b, int off, int len) { assertFalse(closed); return super.read(b, off, len); }
                @Override public long transferTo(OutputStream out) throws IOException { assertFalse(closed); return super.transferTo(out); }
                @Override public byte[] readAllBytes() { assertFalse(closed); return super.readAllBytes(); }
            };
            streams.put(stream, new Throwable());
            return stream;
        }

        void verifyAllRead() {
            streams.forEach((stream, stack) -> assertEquals(0, stream.available(),
                                                            "unconsumed content in stream created at " +
                                                            new ByteArrayOutputStream() {{ stack.printStackTrace(new PrintStream(this)); }}));
        }

    }

    @Test
    void testApplicationPackageStream() throws Exception {
        Map<String, String> content = Map.of("deployment.xml", deploymentXml,
                                             "services.xml", servicesXml,
                                             "jdisc.xml", jdiscXml,
                                             "unused1.xml", jdiscXml,
                                             "content/content.xml", contentXml,
                                             "content/nodes.xml", nodesXml,
                                             "gurba", "gurba");
        byte[] zip = zip(content);
        assertEquals(content, unzip(zip));
        AngryStreams angry = new AngryStreams(zip);

        ApplicationPackageStream identity = new ApplicationPackageStream(angry::stream, () -> __ -> true);
        InputStream lazy = new LazyInputStream(() -> new ByteArrayInputStream(identity.truncatedPackage().zippedContent()));
        assertEquals("must completely exhaust input before reading package",
                     assertThrows(IllegalStateException.class, identity::truncatedPackage).getMessage());

        // Verify no content has changed when passing through the stream.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream stream = identity.zipStream()) { stream.transferTo(out); }
        assertEquals(content, unzip(out.toByteArray()));
        assertEquals(content, unzip(identity.truncatedPackage().zippedContent()));
        assertEquals(content, unzip(lazy.readAllBytes()));
        ApplicationPackage original = new ApplicationPackage(zip);
        assertEquals(unzip(original.metaDataZip()), unzip(identity.truncatedPackage().metaDataZip()));
        assertEquals(original.bundleHash(), identity.truncatedPackage().bundleHash());

        // Change deployment.xml, remove unused1.xml and add unused2.xml
        Map<String, UnaryOperator<InputStream>> replacements = Map.of("deployment.xml", in -> new SequenceInputStream(in, new ByteArrayInputStream("\n\n".getBytes(UTF_8))),
                                                                      "unused1.xml", in -> null,
                                                                      "unused2.xml", __ -> new ByteArrayInputStream(jdiscXml.getBytes(UTF_8)));
        Predicate<String> truncation = name -> name.endsWith(".xml");
        ApplicationPackageStream modifier = new ApplicationPackageStream(angry::stream, () -> truncation, replacements);
        out.reset();

        InputStream partiallyRead = modifier.zipStream();
        assertEquals(15, partiallyRead.readNBytes(15).length);

        try (InputStream stream = modifier.zipStream()) { stream.transferTo(out); }

        assertEquals(Map.of("deployment.xml", deploymentXml + "\n\n",
                            "services.xml", servicesXml,
                            "jdisc.xml", jdiscXml,
                            "unused2.xml", jdiscXml,
                            "content/content.xml", contentXml,
                            "content/nodes.xml", nodesXml,
                            "gurba", "gurba"),
                     unzip(out.toByteArray()));

        assertEquals(Map.of("deployment.xml", deploymentXml + "\n\n",
                            "services.xml", servicesXml,
                            "jdisc.xml", jdiscXml,
                            "unused2.xml", jdiscXml,
                            "content/content.xml", contentXml,
                            "content/nodes.xml", nodesXml),
                     unzip(modifier.truncatedPackage().zippedContent()));

        // Compare retained metadata for an updated original package, and the truncated package of the modifier.
        assertEquals(unzip(new ApplicationPackage(zip(Map.of("deployment.xml", deploymentXml + "\n\n",  // Expected to change.
                                                             "services.xml", servicesXml,
                                                             "jdisc.xml", jdiscXml,
                                                             "unused1.xml", jdiscXml,                   // Irrelevant.
                                                             "content/content.xml", contentXml,
                                                             "content/nodes.xml", nodesXml,
                                                             "gurba", "gurba"))).metaDataZip()),
                     unzip(modifier.truncatedPackage().metaDataZip()));

        try (InputStream stream1 = modifier.zipStream();
             InputStream stream2 = modifier.zipStream()) {
            assertArrayEquals(stream1.readAllBytes(),
                              stream2.readAllBytes());
        }

        ByteArrayOutputStream byteAtATime = new ByteArrayOutputStream();
        try (InputStream stream1 = modifier.zipStream();
             InputStream stream2 = modifier.zipStream()) {
            for (int b; (b = stream1.read()) != -1; ) byteAtATime.write(b);
            assertArrayEquals(stream2.readAllBytes(),
                              byteAtATime.toByteArray());
        }

        assertEquals(byteAtATime.size(),
                     15 + partiallyRead.readAllBytes().length);
        partiallyRead.close();

        try (InputStream stream = modifier.zipStream()) { stream.readNBytes(12); }

        angry.verifyAllRead();
    }

}
