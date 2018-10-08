// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
@RunWith(Enclosed.class)
public class FileHelperTest {

    public static class GeneralLogicTests {
        @Rule
        public TemporaryFolder folder = new TemporaryFolder();

        @Test
        public void delete_all_files_non_recursive() {
            int numDeleted = FileHelper.streamFiles(testRoot())
                    .delete();

            assertEquals(3, numDeleted);
            assertRecursiveContents("test", "test/file.txt", "test/data.json", "test/subdir-1", "test/subdir-1/file", "test/subdir-2");
        }

        @Test
        public void delete_all_files_recursive() {
            int numDeleted = FileHelper.streamFiles(testRoot())
                    .recursive(true)
                    .delete();

            assertEquals(6, numDeleted);
            assertRecursiveContents("test", "test/subdir-1", "test/subdir-2");
        }

        @Test
        public void delete_with_filter_recursive() {
            int numDeleted = FileHelper.streamFiles(testRoot())
                    .filterFile(FileHelper.nameEndsWith(".json"))
                    .recursive(true)
                    .delete();

            assertEquals(3, numDeleted);
            assertRecursiveContents("test.txt", "test", "test/file.txt", "test/subdir-1", "test/subdir-1/file", "test/subdir-2");
        }

        @Test
        public void delete_directory_with_filter() {
            int numDeleted = FileHelper.streamDirectories(testRoot())
                    .filterDirectory(FileHelper.nameStartsWith("subdir"))
                    .recursive(true)
                    .delete();

            assertEquals(3, numDeleted);
            assertRecursiveContents("file-1.json", "test.json", "test.txt", "test", "test/file.txt", "test/data.json");
        }

        @Test
        public void delete_all_contents() {
            int numDeleted = FileHelper.streamContents(testRoot())
                    .recursive(true)
                    .delete();

            assertEquals(9, numDeleted);
            assertTrue(Files.exists(testRoot()));
            assertRecursiveContents();
        }

        @Test
        public void delete_everything() {
            int numDeleted = FileHelper.streamContents(testRoot())
                    .includeBase(true)
                    .recursive(true)
                    .delete();

            assertEquals(10, numDeleted);
            assertFalse(Files.exists(testRoot()));
        }

        @Before
        public void setup() throws IOException {
            Path root = testRoot();

            Files.createFile(root.resolve("file-1.json"));
            Files.createFile(root.resolve("test.json"));
            Files.createFile(root.resolve("test.txt"));

            Files.createDirectories(root.resolve("test"));
            Files.createFile(root.resolve("test/file.txt"));
            Files.createFile(root.resolve("test/data.json"));

            Files.createDirectories(root.resolve("test/subdir-1"));
            Files.createFile(root.resolve("test/subdir-1/file"));

            Files.createDirectories(root.resolve("test/subdir-2"));
        }

        private Path testRoot() {
            return folder.getRoot().toPath();
        }

        private void assertRecursiveContents(String... relativePaths) {
            Set<String> expectedPaths = new HashSet<>(Arrays.asList(relativePaths));
            Set<String> actualPaths = recursivelyListContents(testRoot()).stream()
                    .map(testRoot()::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());

            assertEquals(expectedPaths, actualPaths);
        }

        private List<Path> recursivelyListContents(Path basePath) {
            try (Stream<Path> pathStream = Files.list(basePath)) {
                List<Path> paths = new LinkedList<>();
                pathStream.forEach(path -> {
                    paths.add(path);
                    if (Files.isDirectory(path))
                        paths.addAll(recursivelyListContents(path));
                });
                return paths;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class FilterUnitTests {

        private final BasicFileAttributes attributes = mock(BasicFileAttributes.class);

        @Test
        public void age_filter_test() {
            Path path = Paths.get("/my/fake/path");
            when(attributes.lastModifiedTime()).thenReturn(FileTime.from(Instant.now().minus(Duration.ofHours(1))));
            FileHelper.FileAttributes fileAttributes = new FileHelper.FileAttributes(path, attributes);

            assertFalse(FileHelper.olderThan(Duration.ofMinutes(61)).test(fileAttributes));
            assertTrue(FileHelper.olderThan(Duration.ofMinutes(59)).test(fileAttributes));

            assertTrue(FileHelper.youngerThan(Duration.ofMinutes(61)).test(fileAttributes));
            assertFalse(FileHelper.youngerThan(Duration.ofMinutes(59)).test(fileAttributes));
        }

        @Test
        public void size_filters() {
            Path path = Paths.get("/my/fake/path");
            when(attributes.size()).thenReturn(100L);
            FileHelper.FileAttributes fileAttributes = new FileHelper.FileAttributes(path, attributes);

            assertFalse(FileHelper.largerThan(101).test(fileAttributes));
            assertTrue(FileHelper.largerThan(99).test(fileAttributes));

            assertTrue(FileHelper.smallerThan(101).test(fileAttributes));
            assertFalse(FileHelper.smallerThan(99).test(fileAttributes));
        }

        @Test
        public void filename_filters() {
            Path path = Paths.get("/my/fake/path/some-12352-file.json");
            FileHelper.FileAttributes fileAttributes = new FileHelper.FileAttributes(path, attributes);

            assertTrue(FileHelper.nameStartsWith("some-").test(fileAttributes));
            assertFalse(FileHelper.nameStartsWith("som-").test(fileAttributes));

            assertTrue(FileHelper.nameEndsWith(".json").test(fileAttributes));
            assertFalse(FileHelper.nameEndsWith("file").test(fileAttributes));

            assertTrue(FileHelper.nameMatches(Pattern.compile("some-[0-9]+-file.json")).test(fileAttributes));
            assertTrue(FileHelper.nameMatches(Pattern.compile("^some-[0-9]+-file.json$")).test(fileAttributes));
            assertFalse(FileHelper.nameMatches(Pattern.compile("some-[0-9]-file.json")).test(fileAttributes));
        }
    }
}
