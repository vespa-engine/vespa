// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.routing.test;

import com.yahoo.config.ConfigInstance;
import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static helpers.CompareConfigTestHelper.assertSerializedConfigEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen
 */
public class RoutingTestCase {

    private static final boolean WRITE_FILES = false;

    @Test
    @Ignore // TODO: Why?
    public void testRoutingContent() throws IOException {
        assertApplication(new File("src/test/cfg/routing/contentsimpleconfig"));
        assertApplication(new File("src/test/cfg/routing/content_two_clusters"));
    }

    @Test
    public void testRouting() throws IOException {
        assertApplication(new File("src/test/cfg/routing/unexpectedrecipient"));
        assertApplication(new File("src/test/cfg/routing/servicenotfound"));
        assertApplication(new File("src/test/cfg/routing/routenotfoundinroute"));
        assertApplication(new File("src/test/cfg/routing/routenotfound"));
        assertApplication(new File("src/test/cfg/routing/routeconfig"));
        assertApplication(new File("src/test/cfg/routing/replaceroute"));
        assertApplication(new File("src/test/cfg/routing/replacehop"));
        assertApplication(new File("src/test/cfg/routing/mismatchedrecipient"));
        assertApplication(new File("src/test/cfg/routing/hopnotfound"));
        assertApplication(new File("src/test/cfg/routing/hoperrorinroute"));
        assertApplication(new File("src/test/cfg/routing/hoperrorinrecipient"));
        assertApplication(new File("src/test/cfg/routing/hoperror"));
        assertApplication(new File("src/test/cfg/routing/hopconfig"));
        assertApplication(new File("src/test/cfg/routing/emptyroute"));
        assertApplication(new File("src/test/cfg/routing/emptyhop"));
        assertApplication(new File("src/test/cfg/routing/duplicateroute"));
        assertApplication(new File("src/test/cfg/routing/duplicatehop"));
        assertApplication(new File("src/test/cfg/routing/defaultconfig"));
    }

    /**
     * Tests whether or not the given application produces the expected output. When creating new tests, create an
     * application directory containing the necessary setup files, and call this method with a TRUE create flag.
     *
     * @param application The application directory.
     */
    private static void assertApplication(File application) throws IOException {
        assertTrue(application.isDirectory());
        String applicationName = application.getName();
        Map<String, File> files = new HashMap<>();
        for (File file : application.listFiles(new ContentFilter())) {
            files.put(file.getName(), file);
        }
        String path = null;
        try {
            path = application.getCanonicalPath();
        } catch (IOException e) {
            fail("Could not resolve path for application '" + applicationName + "'.");
        }
        VespaModelCreatorWithFilePkg creator = new VespaModelCreatorWithFilePkg(path);

        VespaModel model = creator.create();
        List<String> errors = model.getRouting().getErrors();
        if (errors.isEmpty()) {
            if (files.containsKey("errors.txt")) {
                if (WRITE_FILES) {
                    files.remove("errors.txt").delete();
                } else {
                    fail("Route verification did not fail.");
                }
            }
            MessagebusConfig.Builder mBusB = new MessagebusConfig.Builder();
            model.getConfig(mBusB, "");
            MessagebusConfig mBus = new MessagebusConfig(mBusB);
            assertConfigFileContains(application, files, "messagebus.cfg", mBus);

            DocumentrouteselectorpolicyConfig.Builder drB = new DocumentrouteselectorpolicyConfig.Builder();
            model.getConfig(drB, "");
            DocumentrouteselectorpolicyConfig dr = new DocumentrouteselectorpolicyConfig(drB);
            assertConfigFileContains(application, files, "documentrouteselectorpolicy.cfg", dr);
        } else {
            StringBuilder msg = new StringBuilder();
            for (String error : errors) {
                msg.append(error).append("\n");
            }
            assertFileContains(application, files, "errors.txt", msg.toString());
        }
    }

    /**
     * Tests whether or not a given file exists and contains some expected content.
     *
     * @param application     The application directory.
     * @param files           The filtered list of files within the application.
     * @param fileName        The name of the file whose content to check.
     * @param expectedContent The content required in the file being checked.
     */
    private static void assertFileContains(File application, Map<String, File> files,
                                           String fileName, String expectedContent) {
        if (WRITE_FILES) {
            files.put(fileName, writeFile(application, fileName, expectedContent.trim() + "\n"));
        }
        if (!files.containsKey(fileName)) {
            fail("Expected file '" + fileName + "' not found.\nExpected content: " + expectedContent);
            return;
        }
        StringBuilder content = new StringBuilder();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(files.get(fileName)));
            String line = reader.readLine();
            while (line != null) {
                content.append(line).append("\n");
                line = reader.readLine();
            }
            reader.close();
        } catch (FileNotFoundException e) {
            fail("File '" + fileName + "' not found.");
        } catch (IOException e) {
            fail("Failed to read content of file '" + fileName + ".");
        }

        assertEquals(content.toString().trim(), expectedContent.replace("\r", "").trim());
    }
    /**
     * Tests whether or not a given file exists and contains some expected content.
     *
     * @param application     The application directory.
     * @param files           The filtered list of files within the application.
     * @param fileName        The name of the file whose content to check.
     * @param config          The config required in the file being checked.
     * @throws IOException 
     */
    private static void assertConfigFileContains(File application, Map<String, File> files,
                                                 String fileName, ConfigInstance config) throws IOException {
        String configString = config.toString();
        if (WRITE_FILES) {
            files.put(fileName, writeFile(application, fileName, configString.trim() + "\n"));
        }
        if (!files.containsKey(fileName)) {
            fail("Expected file '" + fileName + "' not found.");
            return;
        }
        assertSerializedConfigEquals(IOUtils.readFile(files.get(fileName)), configString);
    }

    /**
     * Writes content to a specific file.
     *
     * @param application The application directory.
     * @param name        The name of the file to write.
     * @param content     The content to write.
     * @return The file written.
     */
    private static File writeFile(File application, String name, String content) {
        File ret = null;
        try {
            name = application.getCanonicalPath() + "/" + name;
            System.out.println("\tWriting file '" + name + "'.");

            PrintWriter writer = new PrintWriter(new FileWriter(name));
            writer.print(content);
            writer.close();

            ret = new File(name);
        } catch (IOException e) {
            fail(e.getMessage());
        }
        return ret;
    }

    /**
     * Helper class to filter what files within an application directory gets added to the files index.
     */
    private static class ContentFilter implements FilenameFilter {
        public boolean accept(File file, String name) {
            return !name.equals(".git") && !name.equals(".svn");
        }
    }

}
