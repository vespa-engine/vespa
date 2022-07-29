// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;
import com.yahoo.vespa.test.file.TestFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Set.of;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author freva
 */

public class FileFinderTest {

    @Nested
    public class GeneralLogicTests {

        private final FileSystem fileSystem = TestFileSystem.create();

        @Test
        void all_files_non_recursive() {
            assertFileHelper(FileFinder.files(testRoot())
                            .maxDepth(1),

                    of("file-1.json", "test.json", "test.txt"),
                    of("test", "test/file.txt", "test/data.json", "test/subdir-1", "test/subdir-1/test", "test/subdir-2"));
        }

        @Test
        void all_files_recursive() {
            assertFileHelper(FileFinder.files(testRoot()),

                    of("file-1.json", "test.json", "test.txt", "test/file.txt", "test/data.json", "test/subdir-1/test"),
                    of("test", "test/subdir-1", "test/subdir-2"));
        }

        @Test
        void all_files_recursive_with_prune_relative() {
            assertFileHelper(FileFinder.files(testRoot()).prune(fileSystem.getPath("test")),

                    of("file-1.json", "test.json", "test.txt"),
                    of("test", "test/file.txt", "test/data.json", "test/subdir-1", "test/subdir-1/test", "test/subdir-2"));
        }

        @Test
        void all_files_recursive_with_prune_absolute() {
            assertFileHelper(FileFinder.files(testRoot()).prune(testRoot().resolve("test/subdir-1")),

                    of("file-1.json", "test.json", "test.txt", "test/file.txt", "test/data.json"),
                    of("test", "test/subdir-1", "test/subdir-1/test", "test/subdir-2"));
        }

        @Test
        void throws_if_prune_path_not_under_base_path() {
            assertThrows(IllegalArgumentException.class, () -> {
                FileFinder.files(Paths.get("/some/path")).prune(Paths.get("/other/path"));
            });
        }

        @Test
        void with_file_filter_recursive() {
            assertFileHelper(FileFinder.files(testRoot())
                            .match(FileFinder.nameEndsWith(".json")),

                    of("file-1.json", "test.json", "test/data.json"),
                    of("test.txt", "test", "test/file.txt", "test/subdir-1", "test/subdir-1/test", "test/subdir-2"));
        }

        @Test
        void all_files_limited_depth() {
            assertFileHelper(FileFinder.files(testRoot())
                            .maxDepth(2),

                    of("test.txt", "file-1.json", "test.json", "test/file.txt", "test/data.json"),
                    of("test", "test/subdir-1", "test/subdir-1/test", "test/subdir-2"));
        }

        @Test
        void directory_with_filter() {
            assertFileHelper(FileFinder.directories(testRoot())
                            .match(FileFinder.nameStartsWith("subdir"))
                            .maxDepth(2),

                    of("test/subdir-1", "test/subdir-2"),
                    of("file-1.json", "test.json", "test.txt", "test", "test/file.txt", "test/data.json"));
        }

        @Test
        void match_file_and_directory_with_same_name() {
            assertFileHelper(FileFinder.from(testRoot())
                            .match(FileFinder.nameEndsWith("test")),

                    of("test", "test/subdir-1/test"),
                    of("file-1.json", "test.json", "test.txt"));
        }

        @Test
        void all_contents() {
            assertFileHelper(FileFinder.from(testRoot())
                            .maxDepth(1),

                    of("file-1.json", "test.json", "test.txt", "test"),
                    of());

            assertTrue(Files.exists(testRoot()));
        }

        @BeforeEach
        public void setup() throws IOException {
            Path root = testRoot();
            Files.createDirectories(root);

            Files.createFile(root.resolve("file-1.json"));
            Files.createFile(root.resolve("test.json"));
            Files.createFile(root.resolve("test.txt"));

            Files.createDirectories(root.resolve("test"));
            Files.createFile(root.resolve("test/file.txt"));
            Files.createFile(root.resolve("test/data.json"));

            Files.createDirectories(root.resolve("test/subdir-1"));
            Files.createFile(root.resolve("test/subdir-1/test"));

            Files.createDirectories(root.resolve("test/subdir-2"));
        }

        private Path testRoot() {
            return fileSystem.getPath("/file-finder");
        }

        private void assertFileHelper(FileFinder fileFinder, Set<String> expectedList, Set<String> expectedContentsAfterDelete) {
            Set<String> actualList = fileFinder.stream()
                    .map(FileFinder.FileAttributes::path)
                    .map(testRoot()::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
            assertEquals(expectedList, actualList);

            fileFinder.deleteRecursively(mock(TaskContext.class));
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
                return List.of();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    public class FilterUnitTests {

        private final BasicFileAttributes attributes = mock(BasicFileAttributes.class);

        @Test
        void age_filter_test() {
            Path path = Paths.get("/my/fake/path");
            when(attributes.lastModifiedTime()).thenReturn(FileTime.from(Instant.now().minus(Duration.ofHours(1))));
            FileFinder.FileAttributes fileAttributes = new FileFinder.FileAttributes(path, attributes);

            assertFalse(FileFinder.olderThan(Duration.ofMinutes(61)).test(fileAttributes));
            assertTrue(FileFinder.olderThan(Duration.ofMinutes(59)).test(fileAttributes));

            assertTrue(FileFinder.youngerThan(Duration.ofMinutes(61)).test(fileAttributes));
            assertFalse(FileFinder.youngerThan(Duration.ofMinutes(59)).test(fileAttributes));
        }

        @Test
        void size_filters() {
            Path path = Paths.get("/my/fake/path");
            when(attributes.size()).thenReturn(100L);
            FileFinder.FileAttributes fileAttributes = new FileFinder.FileAttributes(path, attributes);

            assertFalse(FileFinder.largerThan(101).test(fileAttributes));
            assertTrue(FileFinder.largerThan(99).test(fileAttributes));

            assertTrue(FileFinder.smallerThan(101).test(fileAttributes));
            assertFalse(FileFinder.smallerThan(99).test(fileAttributes));
        }

        @Test
        void filename_filters() {
            Path path = Paths.get("/my/fake/path/some-12352-file.json");
            FileFinder.FileAttributes fileAttributes = new FileFinder.FileAttributes(path, attributes);

            assertTrue(FileFinder.nameStartsWith("some-").test(fileAttributes));
            assertFalse(FileFinder.nameStartsWith("som-").test(fileAttributes));

            assertTrue(FileFinder.nameEndsWith(".json").test(fileAttributes));
            assertFalse(FileFinder.nameEndsWith("file").test(fileAttributes));

            assertTrue(FileFinder.nameMatches(Pattern.compile("some-[0-9]+-file.json")).test(fileAttributes));
            assertTrue(FileFinder.nameMatches(Pattern.compile("^some-[0-9]+-file.json$")).test(fileAttributes));
            assertFalse(FileFinder.nameMatches(Pattern.compile("some-[0-9]-file.json")).test(fileAttributes));
        }
    }
}
