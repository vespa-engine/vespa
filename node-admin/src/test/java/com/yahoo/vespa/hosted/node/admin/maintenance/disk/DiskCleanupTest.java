// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.disk;

import com.yahoo.vespa.hosted.node.admin.component.TestTaskContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.FileFinder.FileAttributes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.yahoo.vespa.hosted.node.admin.maintenance.disk.DiskCleanupRule.Priority;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
public class DiskCleanupTest {

    private final TestTaskContext context = new TestTaskContext();
    private final DiskCleanupTester tester = new DiskCleanupTester();
    private final DiskCleanup diskCleanup = new DiskCleanup();

    @Test
    void nothing_deleted() throws IOException {
        assertFalse(diskCleanup.cleanup(context, List.of(), 0));
        assertFalse(diskCleanup.cleanup(context, List.of(), 10));

        DiskCleanupRuleMock rule1 = new DiskCleanupRuleMock();
        DiskCleanupRuleMock rule2 = new DiskCleanupRuleMock();
        assertFalse(diskCleanup.cleanup(context, List.of(rule1, rule2), 0));
        assertFalse(diskCleanup.cleanup(context, List.of(rule1, rule2), 10));

        tester.createFile("/path/that-should-not-be-deleted", 5);
        assertFalse(diskCleanup.cleanup(context, List.of(rule1, rule2), 10));
        tester.assertAllFilesExistExcept();

        // Create a file and let rule return it, but before cleanup is run, the file is deleted
        rule1.addFile(tester.createFile("/path/file-does-not-exist", 1), Priority.HIGHEST);
        Files.delete(tester.path("/path/file-does-not-exist"));
        assertFalse(diskCleanup.cleanup(context, List.of(rule1, rule2), 10));
    }

    @Test
    void delete_test() throws IOException {
        tester.createFile("/opt/vespa/var/db/do-not-delete-1.db", 1);
        tester.createFile("/opt/vespa/var/db/do-not-delete-2.db", 1);
        tester.createFile("/opt/vespa/var/zookeeper/do-not-delete-3", 1);
        tester.createFile("/opt/vespa/var/index/something-important", 1);

        DiskCleanupRuleMock rule1 = new DiskCleanupRuleMock()
                .addFile(tester.createFile("/opt/vespa/logs/vespa-1.log", 10), Priority.MEDIUM)
                .addFile(tester.createFile("/opt/vespa/logs/vespa-2.log", 8), Priority.HIGH)
                .addFile(tester.createFile("/opt/vespa/logs/vespa-3.log", 13), Priority.HIGHEST)
                .addFile(tester.createFile("/opt/vespa/logs/vespa-4.log", 10), Priority.HIGHEST);
        DiskCleanupRuleMock rule2 = new DiskCleanupRuleMock()
                .addFile(tester.createFile("/opt/vespa/var/crash/core1", 105), Priority.LOW)
                .addFile(tester.createFile("/opt/vespa/var/crash/vespa-proton-bin.core-232", 190), Priority.HIGH)
                .addFile(tester.createFile("/opt/vespa/var/crash/core3", 54), Priority.MEDIUM)
                .addFile(tester.createFile("/opt/vespa/var/crash/core4", 300), Priority.HIGH);

        // 2 files with HIGHEST priority, tie broken by the largest size which is won by "vespa-3.log", since
        // it is >= 10 bytes, no more files are deleted
        assertTrue(diskCleanup.cleanup(context, List.of(rule1, rule2), 10));
        tester.assertAllFilesExistExcept("/opt/vespa/logs/vespa-3.log");

        // Called with the same arguments, but vespa-3.log is still missing...
        assertTrue(diskCleanup.cleanup(context, List.of(rule1, rule2), 10));
        tester.assertAllFilesExistExcept("/opt/vespa/logs/vespa-3.log", "/opt/vespa/logs/vespa-4.log");

        assertTrue(diskCleanup.cleanup(context, List.of(rule1, rule2), 500));
        tester.assertAllFilesExistExcept("/opt/vespa/logs/vespa-3.log", "/opt/vespa/logs/vespa-4.log", // from before
                // 300 + 190 + 8 + 54
                "/opt/vespa/var/crash/core4", "/opt/vespa/var/crash/vespa-proton-bin.core-232", "/opt/vespa/logs/vespa-2.log", "/opt/vespa/var/crash/core3");
    }

    private static class DiskCleanupRuleMock implements DiskCleanupRule {
        private final ArrayList<PrioritizedFileAttributes> pfa = new ArrayList<>();

        private DiskCleanupRuleMock addFile(Path path, Priority priority) throws IOException {
            PosixFileAttributes attributes = Files.getFileAttributeView(path, PosixFileAttributeView.class).readAttributes();
            pfa.add(new PrioritizedFileAttributes(new FileAttributes(path, attributes), priority));
            return this;
        }

        @Override
        public Collection<PrioritizedFileAttributes> prioritize() {
            return Collections.unmodifiableList(pfa);
        }
    }

    private static class DiskCleanupTester {
        private final FileSystem fileSystem = TestFileSystem.create();
        private final Set<String> files = new HashSet<>();

        private Path path(String path) {
            return fileSystem.getPath(path);
        }

        private Path createFile(String pathStr, int size) throws IOException {
            Path path = path(pathStr);
            Files.createDirectories(path.getParent());
            Files.write(path, new byte[size]);
            files.add(path.toString());
            return path;
        }

        private void assertAllFilesExistExcept(String... deletedPaths) {
            Set<String> actual = FileFinder.files(path("/")).stream().map(fa -> fa.path().toString()).collect(Collectors.toSet());
            Set<String> expected = new HashSet<>(files);
            expected.removeAll(Set.of(deletedPaths));
            assertEquals(expected, actual);
        }
    }
}
