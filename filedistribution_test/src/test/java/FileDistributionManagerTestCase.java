// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.vespa.filedistribution.FileDistributionManager;

import static org.junit.Assume.assumeTrue;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;


/**
 * @author tonytv
 */
public class FileDistributionManagerTestCase {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    File testDir;
    File dbDir = new File(testDir, "dbdir");
    File appDir = new File(testDir, "appdir");
    File components = new File(appDir, "components");
    FileDistributionManager manager;
    MockLock lock;

    private String addFile(String name, String contents) throws IOException {
        File file = new File(components, name);
        FileWriter writer = new FileWriter(file, false);
        writer.write(contents);
        writer.close();

        return manager.addFile("components/" + name);
    }

    @Before
    public void before() throws IOException {
        assumeTrue(FileDistributionManager.isAvailable());

        System.out.println(System.getProperty("java.library.path"));
        testDir = folder.newFolder("filedistributionmanagertest" + System.currentTimeMillis());
        dbDir.mkdir();
        appDir.mkdir();
        components.mkdir();
        lock = new MockLock();

        manager = new FileDistributionManager(dbDir, appDir, "mockfiledistributionmodel.testing", "foo", lock);
    }

    @After
    public void after() {
        if (manager != null)
            manager.shutdown();
    }

    @Test
    public void addFiles() throws IOException {
        final String commonContent = "content";
        final String name1 = "searcher1", name2 = "searcher2", name3 = "searcher3";

        String hash1 = addFile(name1, commonContent);
        String hash2 = addFile(name2, commonContent);
        String hash3 = addFile(name3, "different content");
        assertThat(lock.numAcquire, is(3));
        assertThat(lock.numRelease, is(3));

        assertNotSame(hash1, hash2);
        assertNotSame(hash1, hash3);
        assertNotSame(hash2, hash3);

        assertTrue(hash1.length() == 40);

        assertFileExists(name1, hash1);
        assertFileExists(name2, hash2);
        assertFileExists(name3, hash3);
    }

    private void assertFileExists(String name, String hash) {
        File destinationDir = new File(dbDir, hash + ".new");
        assertTrue(destinationDir.exists());

        File file = new File(destinationDir, name);
        assertTrue(file.exists());
    }

    private class MockLock implements Lock {
        int numAcquire = 0;
        int numRelease = 0;

        public void lock() {
            numAcquire++;

        }

        public void lockInterruptibly() throws InterruptedException {
        }

        public boolean tryLock() {
            return false;
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return false;
        }

        public void unlock() {
            numRelease++;
        }

        public Condition newCondition() {
            return null;
        }
    }
}
