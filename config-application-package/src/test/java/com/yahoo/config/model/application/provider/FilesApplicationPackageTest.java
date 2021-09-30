// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.TestBase;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class FilesApplicationPackageTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testPreprocessing() throws IOException {
        File appDir = temporaryFolder.newFolder();
        IOUtils.copyDirectory(new File("src/test/resources/multienvapp"), appDir);
        assertTrue(new File(appDir, "services.xml").exists());
        assertTrue(new File(appDir, "hosts.xml").exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);

        ApplicationPackage processed = app.preprocess(new Zone(Environment.dev, RegionName.defaultName()),
                                                      new BaseDeployLogger());
        assertTrue(new File(appDir, ".preprocessed").exists());
        String expectedServices = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><services xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\" version=\"1.0\">\n" +
                "    <admin version=\"2.0\">\n" +
                "        <adminserver hostalias=\"node0\"/>\n" +
                "    </admin>\n" +
                "    <content id=\"foo\" version=\"1.0\">\n" +
                "      <redundancy>1</redundancy>\n" +
                "      <documents>\n" +
                "        <document mode=\"index\" type=\"music.sd\"/>\n" +
                "      </documents>\n" +
                "      <nodes>\n" +
                "        <node distribution-key=\"0\" hostalias=\"node0\"/>\n" +
                "      </nodes>\n" +
                "    </content>\n" +
                "    <container id=\"stateless\" version=\"1.0\">\n" +
                "      <search/>\n" +
                "      <component bundle=\"foobundle\" class=\"MyFoo\" id=\"foo\"/>\n" +
                "      <component bundle=\"foobundle\" class=\"TestBar\" id=\"bar\"/>\n" +
                "      <nodes>\n" +
                "        <node hostalias=\"node0\" baseport=\"5000\"/>\n" +
                "      </nodes>\n" +
                "    </container>\n" +
                "</services>";
        TestBase.assertDocument(expectedServices, processed.getServices());
        String expectedHosts = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><hosts xmlns:deploy=\"vespa\" xmlns:preprocess=\"properties\">\n" +
                "    <host name=\"bar.yahoo.com\">\n" +
                "        <alias>node1</alias>\n" +
                "    </host>\n" +
                "</hosts>";
        TestBase.assertDocument(expectedHosts, processed.getHosts());
    }

    @Test
    public void testDeploymentXmlNotAvailable()  {
        File appDir = new File("src/test/resources/multienvapp");
        assertFalse(new File(appDir, "deployment.xml").exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);
        assertFalse(app.getDeployment().isPresent());
    }

    @Test
    public void testDeploymentXml() throws IOException {
        File appDir = new File("src/test/resources/app-with-deployment");
        final File deployment = new File(appDir, "deployment.xml");
        assertTrue(deployment.exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);
        assertTrue(app.getDeployment().isPresent());
        assertFalse(app.getMajorVersion().isPresent());
        assertEquals(IOUtils.readAll(app.getDeployment().get()), IOUtils.readAll(new FileReader(deployment)));
    }

    @Test
    public void testPinningMajorVersion() throws IOException {
        File appDir = new File("src/test/resources/app-pinning-major-version");
        final File deployment = new File(appDir, "deployment.xml");
        assertTrue(deployment.exists());
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);
        assertTrue(app.getDeployment().isPresent());
        assertTrue(app.getMajorVersion().isPresent());
        assertEquals(6, (int)app.getMajorVersion().get());
        assertEquals(IOUtils.readAll(app.getDeployment().get()), IOUtils.readAll(new FileReader(deployment)));
    }

    @Test
    public void failOnEmptyServicesXml() throws IOException {
        File appDir = temporaryFolder.newFolder();
        IOUtils.copyDirectory(new File("src/test/resources/multienvapp"), appDir);
        Files.delete(new File(appDir, "services.xml").toPath());
        FilesApplicationPackage app = FilesApplicationPackage.fromFile(appDir);
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(containsString("services.xml in application package is empty"));
        app.preprocess(new Zone(Environment.dev, RegionName.defaultName()), new BaseDeployLogger());
    }

}
