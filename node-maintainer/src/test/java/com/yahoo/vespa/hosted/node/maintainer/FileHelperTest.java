// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class FileHelperTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void initFiles() throws IOException {
        for (int i=0; i<10; i++) {
            File temp = folder.newFile("test_" + i + ".json");
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(130).toMillis());
        }

        for (int i=0; i<7; i++) {
            File temp = folder.newFile("test_" + i + "_file.test");
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(250).toMillis());
        }

        for (int i=0; i<5; i++) {
            File temp = folder.newFile(i + "-abc" + ".json");
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(80).toMillis());
        }

        File temp = folder.newFile("week_old_file.json");
        temp.setLastModified(System.currentTimeMillis() - Duration.ofDays(8).toMillis());
    }

    @Test
    public void testDeleteAll() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.empty(), false);

        assertEquals(0, getContentsOfDirectory(folder.getRoot()).length);
    }

    @Test
    public void testDeletePrefix() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of("^test_"), false);

        assertEquals(6, getContentsOfDirectory(folder.getRoot()).length); // 5 abc files + 1 week_old_file
    }

    @Test
    public void testDeleteSuffix() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of(".json$"), false);

        assertEquals(7, getContentsOfDirectory(folder.getRoot()).length);
    }

    @Test
    public void testDeletePrefixAndSuffix() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of("^test_.*\\.json$"), false);

        assertEquals(13, getContentsOfDirectory(folder.getRoot()).length); // 5 abc files + 7 test_*_file.test files + week_old_file
    }

    @Test
    public void testDeleteOld() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ofSeconds(600), Optional.empty(), false);

        assertEquals(13, getContentsOfDirectory(folder.getRoot()).length); // All 23 - 6 (from test_*_.json) - 3 (from test_*_file.test) - 1 week old file
    }

    @Test
    public void testDeleteWithAllParameters() throws IOException {
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ofSeconds(200), Optional.of("^test_.*\\.json$"), false);

        assertEquals(15, getContentsOfDirectory(folder.getRoot()).length); // All 23 - 8 (from test_*_.json)
    }

    @Test
    public void testDeleteWithSubDirectoriesNoRecursive() throws IOException {
        initSubDirectories();
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of("^test_.*\\.json$"), false);

        // 6 test_*.json from test_folder1/
        // + 9 test_*.json and 4 abc_*.json from test_folder2/
        // + 13 test_*.json from test_folder2/subSubFolder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + test_folder1/ and test_folder2/ and test_folder2/subSubFolder2/ themselves
        assertEquals(48, getNumberOfFilesAndDirectoriesIn(folder.getRoot()));
    }

    @Test
    public void testDeleteWithSubDirectoriesRecursive() throws IOException {
        initSubDirectories();
        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of("^test_.*\\.json$"), true);

        // 4 abc_*.json from test_folder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + test_folder2/ itself
        assertEquals(18, getNumberOfFilesAndDirectoriesIn(folder.getRoot()));
    }

    @Test
    public void testDeleteFilesWhereFilenameRegexAlsoMatchesDirectories() throws IOException {
        initSubDirectories();

        FileHelper.deleteFiles(folder.getRoot().toPath(), Duration.ZERO, Optional.of("^test_"), false);

        assertEquals(8, getContentsOfDirectory(folder.getRoot()).length); // 5 abc files + 1 week_old_file + 2 directories
    }

    @Test
    public void testGetContentsOfNonExistingDirectory() {
        Path fakePath = Paths.get("/some/made/up/dir/");
        assertEquals(Collections.emptyList(), FileHelper.listContentsOfDirectory(fakePath));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteFilesExceptNMostRecentWithNegativeN() throws IOException {
        FileHelper.deleteFilesExceptNMostRecent(folder.getRoot().toPath(), -5);
    }

    @Test
    public void testDeleteFilesExceptFiveMostRecent() throws IOException {
        FileHelper.deleteFilesExceptNMostRecent(folder.getRoot().toPath(), 5);

        assertEquals(5, getContentsOfDirectory(folder.getRoot()).length);

        String[] oldestFiles = {"test_5_file.test", "test_6_file.test", "test_8.json", "test_9.json", "week_old_file.json"};
        String[] remainingFiles = Arrays.stream(getContentsOfDirectory(folder.getRoot()))
                .map(File::getName)
                .sorted()
                .toArray(String[]::new);

        assertArrayEquals(oldestFiles, remainingFiles);
    }

    @Test
    public void testDeleteFilesExceptNMostRecentWithLargeN() throws IOException {
        String[] filesPreDelete = folder.getRoot().list();

        FileHelper.deleteFilesExceptNMostRecent(folder.getRoot().toPath(), 50);

        assertArrayEquals(filesPreDelete, folder.getRoot().list());
    }

    @Test
    public void testDeleteFilesLargerThan10B() throws IOException {
        initSubDirectories();

        File temp1 = new File(folder.getRoot(), "small_file");
        writeNBytesToFile(temp1, 50);

        File temp2 = new File(folder.getRoot(), "some_file");
        writeNBytesToFile(temp2, 20);

        File temp3 = new File(folder.getRoot(), "test_folder1/some_other_file");
        writeNBytesToFile(temp3, 75);

        FileHelper.deleteFilesLargerThan(folder.getRoot().toPath(), 10);

        assertEquals(58, getNumberOfFilesAndDirectoriesIn(folder.getRoot()));
        assertFalse(temp1.exists() || temp2.exists() || temp3.exists());
    }

    @Test
    public void testDeleteDirectories() throws IOException {
        initSubDirectories();

        FileHelper.deleteDirectories(folder.getRoot().toPath(), Duration.ZERO, Optional.of(".*folder2"));

        //23 files in root
        // + 6 in test_folder1 + test_folder1 itself
        assertEquals(30, getNumberOfFilesAndDirectoriesIn(folder.getRoot()));
    }

    @Test
    public void testDeleteDirectoriesBasedOnAge() throws IOException {
        initSubDirectories();
        // Create folder3 which is older than maxAge, inside have a single directory, subSubFolder3, inside it which is
        // also older than maxAge inside the sub directory, create some files which are newer than maxAge.
        // deleteDirectories() should NOT delete folder3
        File subFolder3 = folder.newFolder("test_folder3");
        File subSubFolder3 = folder.newFolder("test_folder3", "subSubFolder3");

        for (int j=0; j<11; j++) {
            File.createTempFile("test_", ".json", subSubFolder3);
        }

        subFolder3.setLastModified(System.currentTimeMillis() - Duration.ofHours(1).toMillis());
        subSubFolder3.setLastModified(System.currentTimeMillis() - Duration.ofHours(3).toMillis());

        FileHelper.deleteDirectories(folder.getRoot().toPath(), Duration.ofSeconds(50), Optional.of(".*folder.*"));

        //23 files in root
        // + 13 in test_folder2
        // + 13 in subSubFolder2
        // + 11 in subSubFolder3
        // + test_folder2 + subSubFolder2 + folder3 + subSubFolder3 itself
        assertEquals(64, getNumberOfFilesAndDirectoriesIn(folder.getRoot()));
    }

    @Test
    public void testRecursivelyDeleteDirectory() throws IOException {
        initSubDirectories();
        FileHelper.recursiveDelete(folder.getRoot().toPath());
        assertFalse(folder.getRoot().exists());
    }

    @Test
    public void testRecursivelyDeleteRegularFile() throws IOException {
        File file = folder.newFile();
        assertTrue(file.exists());
        assertTrue(file.isFile());
        FileHelper.recursiveDelete(file.toPath());
        assertFalse(file.exists());
    }

    @Test
    public void testRecursivelyDeleteNonExistingFile() throws IOException {
        File file = folder.getRoot().toPath().resolve("non-existing-file.json").toFile();
        assertFalse(file.exists());
        FileHelper.recursiveDelete(file.toPath());
        assertFalse(file.exists());
    }

    @Test
    public void testInitSubDirectories() throws IOException {
        initSubDirectories();
        assertTrue(folder.getRoot().exists());
        assertTrue(folder.getRoot().isDirectory());

        Path test_folder1 = folder.getRoot().toPath().resolve("test_folder1");
        assertTrue(test_folder1.toFile().exists());
        assertTrue(test_folder1.toFile().isDirectory());

        Path test_folder2 = folder.getRoot().toPath().resolve("test_folder2");
        assertTrue(test_folder2.toFile().exists());
        assertTrue(test_folder2.toFile().isDirectory());

        Path subSubFolder2 = test_folder2.resolve("subSubFolder2");
        assertTrue(subSubFolder2.toFile().exists());
        assertTrue(subSubFolder2.toFile().isDirectory());
    }

    @Test
    public void testDoesNotFailOnLastModifiedOnSymLink() throws IOException {
        Path symPath = folder.getRoot().toPath().resolve("symlink");
        Path fakePath = Paths.get("/some/not/existant/file");

        Files.createSymbolicLink(symPath, fakePath);
        assertTrue(Files.isSymbolicLink(symPath));
        assertFalse(Files.exists(fakePath));

        // Not possible to set modified time on symlink in java, so just check that it doesn't crash
        FileHelper.getLastModifiedTime(symPath).toInstant();
    }

    private void initSubDirectories() throws IOException {
        File subFolder1 = folder.newFolder("test_folder1");
        File subFolder2 = folder.newFolder("test_folder2");
        File subSubFolder2 = folder.newFolder("test_folder2", "subSubFolder2");

        for (int j=0; j<6; j++) {
            File temp = File.createTempFile("test_", ".json", subFolder1);
            temp.setLastModified(System.currentTimeMillis() - (j+1)*Duration.ofSeconds(60).toMillis());
        }

        for (int j=0; j<9; j++) {
            File.createTempFile("test_", ".json", subFolder2);
        }

        for (int j=0; j<4; j++) {
            File.createTempFile("abc_", ".txt", subFolder2);
        }

        for (int j=0; j<13; j++) {
            File temp = File.createTempFile("test_", ".json", subSubFolder2);
            temp.setLastModified(System.currentTimeMillis() - (j+1)*Duration.ofSeconds(40).toMillis());
        }

        //Must be after all the files have been created
        subFolder1.setLastModified(System.currentTimeMillis() - Duration.ofHours(2).toMillis());
        subFolder2.setLastModified(System.currentTimeMillis() - Duration.ofHours(1).toMillis());
        subSubFolder2.setLastModified(System.currentTimeMillis() - Duration.ofHours(3).toMillis());
    }

    private static int getNumberOfFilesAndDirectoriesIn(File folder) {
        int total = 0;
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                total += getNumberOfFilesAndDirectoriesIn(file);
            }
            total++;
        }

        return total;
    }

    private static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }


    static File[] getContentsOfDirectory(File directory) {
        File[] directoryContents = directory.listFiles();

        return directoryContents == null ? new File[0] : directoryContents;
    }
}
