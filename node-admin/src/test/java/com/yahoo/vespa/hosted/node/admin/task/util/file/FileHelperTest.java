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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableSet.of;
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
        public void all_files_non_recursive() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterFile(FileHelper.all()),

                    of("file-1.json", "test.json", "test.txt"),
                    of("test", "test/file.txt", "test/data.json", "test/subdir-1", "test/subdir-1/file", "test/subdir-2"));
        }

        @Test
        public void all_files_recursive() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterFile(FileHelper.all())
                            .maxDepth(3),

                    of("file-1.json", "test.json", "test.txt", "test/file.txt", "test/data.json", "test/subdir-1/file"),
                    of("test", "test/subdir-1", "test/subdir-2"));
        }

        @Test
        public void with_file_filter_recursive() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterFile(FileHelper.nameEndsWith(".json"))
                            .maxDepth(3),

                    of("file-1.json", "test.json", "test/data.json"),
                    of("test.txt", "test", "test/file.txt", "test/subdir-1", "test/subdir-1/file", "test/subdir-2"));
        }

        @Test
        public void all_files_limited_depth() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterFile(FileHelper.all())
                            .maxDepth(2),

                    of("test.txt", "file-1.json", "test.json", "test/file.txt", "test/data.json"),
                    of("test", "test/subdir-1", "test/subdir-1/file", "test/subdir-2"));
        }

        @Test
        public void directory_with_filter() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterDirectory(FileHelper.nameStartsWith("subdir"))
                            .maxDepth(2),

                    of("test/subdir-1", "test/subdir-2"),
                    of("file-1.json", "test.json", "test.txt", "test", "test/file.txt", "test/data.json"));
        }

        @Test
        public void all_contents() {
            assertFileHelper(FileHelper.from(testRoot())
                            .filterDirectory(FileHelper.all())
                            .filterFile(FileHelper.all()),

                    of("file-1.json", "test.json", "test.txt", "test"),
                    of());

            assertTrue(Files.exists(testRoot()));
        }

        @Test
        public void everything() {
            FileHelper.from(testRoot()).delete(true);

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

        private void assertFileHelper(FileHelper fileHelper, Set<String> expectedList, Set<String> expectedContentsAfterDelete) {
            Set<String> actualList = fileHelper.stream()
                    .map(FileHelper.FileAttributes::path)
                    .map(testRoot()::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
            assertEquals(expectedList, actualList);

            fileHelper.delete();
            Set<String> actualContentsAfterDelete = recursivelyListContents(testRoot()).stream()
                    .map(testRoot()::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
            assertEquals(expectedContentsAfterDelete, actualContentsAfterDelete);
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
            } catch (NoSuchFileException e) {
                return Collections.emptyList();
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
