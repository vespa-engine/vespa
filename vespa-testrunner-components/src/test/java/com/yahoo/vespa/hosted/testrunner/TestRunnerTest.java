// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import org.fusesource.jansi.Ansi;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests relying on a UNIX shell >_<
 *
 * @author jvenstad
 */
public class TestRunnerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path artifactsPath;
    private Path testPath;
    private Path logFile;
    private Path configFile;
    private Path settingsFile;

    @Before
    public void setup() throws IOException {
        artifactsPath = tmp.newFolder("artifacts").toPath();
        Files.createFile(artifactsPath.resolve("my-tests.jar"));
        Files.createFile(artifactsPath.resolve("my-fat-test.jar"));
        testPath = tmp.newFolder("testData").toPath();
        logFile = tmp.newFile("maven.log").toPath();
        configFile = tmp.newFile("testConfig.json").toPath();
        settingsFile = tmp.newFile("settings.xml").toPath();
    }

    @Test
    public void ansiCodesAreConvertedToHtml() throws InterruptedException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, logFile, configFile, settingsFile,
                                           __ -> new ProcessBuilder("echo", Ansi.ansi().fg(Ansi.Color.RED).a("Hello!").reset().toString()));
        runner.test(TestProfile.SYSTEM_TEST, new byte[0]);
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        Iterator<LogRecord> log = runner.getLog(-1).iterator();
        log.next();
        LogRecord record = log.next();
        assertEquals("<span style=\"color: red;\">Hello!</span>", record.getMessage());
        assertEquals(0, runner.getLog(record.getSequenceNumber()).size());
        assertEquals(TestRunner.Status.SUCCESS, runner.getStatus());
    }

    @Test
    public void errorLeadsToError() throws InterruptedException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, logFile, configFile, settingsFile,
                                           __ -> new ProcessBuilder("This is a command that doesn't exist, for sure!"));
        runner.test(TestProfile.SYSTEM_TEST, new byte[0]);
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        Iterator<LogRecord> log = runner.getLog(-1).iterator();
        log.next();
        LogRecord record = log.next();
        assertEquals("Failed to execute maven command: This is a command that doesn't exist, for sure!", record.getMessage());
        assertNotNull(record.getThrown());
        assertEquals(TestRunner.Status.ERROR, runner.getStatus());
    }

    @Test
    public void failureLeadsToFailure() throws InterruptedException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, logFile, configFile, settingsFile,
                                           __ -> new ProcessBuilder("false"));
        runner.test(TestProfile.SYSTEM_TEST, new byte[0]);
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals(1, runner.getLog(-1).size());
        assertEquals(TestRunner.Status.FAILURE, runner.getStatus());
    }

    @Test
    public void filesAreGenerated() throws InterruptedException, IOException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, logFile, configFile, settingsFile,
                                           __ -> new ProcessBuilder("echo", "Hello!"));
        runner.test(TestProfile.SYSTEM_TEST, "config".getBytes());
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals("config", new String(Files.readAllBytes(configFile)));
        assertTrue(Files.exists(testPath.resolve("pom.xml")));
        assertTrue(Files.exists(settingsFile));
        assertEquals("Hello!\n", new String(Files.readAllBytes(logFile)));
    }

    @Test
    public void runnerCanBeReused() throws InterruptedException, IOException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, logFile, configFile, settingsFile,
                                           __ -> new ProcessBuilder("sleep", "0.1"));
        runner.test(TestProfile.SYSTEM_TEST, "config".getBytes());
        assertEquals(TestRunner.Status.RUNNING, runner.getStatus());

        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals(1, runner.getLog(-1).size());
        assertEquals(TestRunner.Status.SUCCESS, runner.getStatus());

        runner.test(TestProfile.STAGING_TEST, "newConfig".getBytes());
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }

        assertEquals("newConfig", new String(Files.readAllBytes(configFile)));
        assertEquals(1, runner.getLog(-1).size());
    }

}
