// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.application.TestBase;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.provision.zone.ZoneInfo;
import com.yahoo.io.IOUtils;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class PreprocessingTester {

    private final String applicationPackagePath;
    private final File applicationDir;
    private final FilesApplicationPackage application;

    private ApplicationPackage lastProcessed;

    public PreprocessingTester(String applicationPackagePath, TemporaryFolder temporaryFolder) {
        try {
            this.applicationPackagePath = applicationPackagePath;
            this.applicationDir = temporaryFolder.newFolder();
            IOUtils.copyDirectory(new File("src/test/resources/multienvapp"), applicationDir);
            assertTrue(new File(applicationDir, "services.xml").exists());
            assertTrue(new File(applicationDir, "hosts.xml").exists());
            this.application = FilesApplicationPackage.fromDir(applicationDir, Map.of());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public ApplicationPackage preprocess(ZoneInfo zoneInfo) {
        try {
            this.lastProcessed = application.preprocess(zoneInfo, new BaseDeployLogger());
            assertTrue(new File(applicationDir, ".preprocessed").exists());
            return lastProcessed;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void assertServices(String expectedServices) {
        TestBase.assertDocument(expectedServices, lastProcessed.getServices());
    }

    public void assertHosts(String expectedHosts) {
        TestBase.assertDocument(expectedHosts, lastProcessed.getHosts());
    }

}
