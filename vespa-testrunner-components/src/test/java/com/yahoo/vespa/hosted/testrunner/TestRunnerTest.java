// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.concurrent.ExecutionException;
import java.util.logging.LogRecord;

import static com.yahoo.vespa.testrunner.TestRunner.Suite.STAGING_TEST;
import static com.yahoo.vespa.testrunner.TestRunner.Suite.SYSTEM_TEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    private Path configFile;
    private Path settingsFile;

    @Before
    public void setup() throws IOException {
        artifactsPath = tmp.newFolder("artifacts").toPath();
        Files.createFile(artifactsPath.resolve("my-tests.jar"));
        Files.createFile(artifactsPath.resolve("my-fat-test.jar"));
        testPath = tmp.newFolder("testData").toPath();
        configFile = tmp.newFile("testConfig.json").toPath();
        settingsFile = tmp.newFile("settings.xml").toPath();
    }

    @Test
    public void noTestJarIsANoTests() throws InterruptedException, IOException, ExecutionException {
        Files.delete(artifactsPath.resolve("my-tests.jar"));
        TestRunner runner = new TestRunner(artifactsPath, testPath, configFile, settingsFile,
                                           __ -> new ProcessBuilder("This is a command that doesn't exist, for sure!"));
        runner.test(SYSTEM_TEST, new byte[0]).get();
        assertFalse(runner.getLog(-1).iterator().hasNext());
        assertEquals(TestRunner.Status.NO_TESTS, runner.getStatus());
    }

    @Test
    public void errorLeadsToError() throws InterruptedException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, configFile, settingsFile,
                                           __ -> new ProcessBuilder("false"));
        runner.test(SYSTEM_TEST, new byte[0]);
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals(1, runner.getLog(-1).size());
        assertEquals(TestRunner.Status.FAILURE, runner.getStatus());
    }

    @Test
    public void failureLeadsToFailure() throws InterruptedException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, configFile, settingsFile,
                                           __ -> new ProcessBuilder("false"));
        runner.test(SYSTEM_TEST, new byte[0]);
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals(1, runner.getLog(-1).size());
        assertEquals(TestRunner.Status.FAILURE, runner.getStatus());
    }

    @Test
    public void filesAreGenerated() throws InterruptedException, IOException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, configFile, settingsFile,
                                           __ -> new ProcessBuilder("echo", "Hello!"));
        runner.test(SYSTEM_TEST, "config".getBytes());
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals("config", new String(Files.readAllBytes(configFile)));
        assertTrue(Files.exists(testPath.resolve("pom.xml")));
        assertTrue(Files.exists(settingsFile));
    }

    @Test
    public void runnerCanBeReused() throws InterruptedException, IOException {
        TestRunner runner = new TestRunner(artifactsPath, testPath, configFile, settingsFile,
                                           __ -> new ProcessBuilder("sleep", "0.1"));
        runner.test(SYSTEM_TEST, "config".getBytes());
        assertEquals(TestRunner.Status.RUNNING, runner.getStatus());

        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }
        assertEquals(1, runner.getLog(-1).size());
        assertEquals(TestRunner.Status.SUCCESS, runner.getStatus());

        runner.test(STAGING_TEST, "newConfig".getBytes());
        while (runner.getStatus() == TestRunner.Status.RUNNING) {
            Thread.sleep(10);
        }

        assertEquals("newConfig", new String(Files.readAllBytes(configFile)));
        assertEquals(1, runner.getLog(-1).size());
    }

}
