// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class DeleteOldAppDataTest {
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
    public void testDeleteAll() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, null, false);

        assertThat(folder.getRoot().listFiles().length, is(0));
    }

    @Test
    public void testDeletePrefix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "^test_", false);

        assertThat(folder.getRoot().listFiles().length, is(6)); // 5 abc files + 1 week_old_file
    }

    @Test
    public void testDeleteSuffix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, ".json$", false);

        assertThat(folder.getRoot().listFiles().length, is(7));
    }

    @Test
    public void testDeletePrefixAndSuffix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "^test_.*\\.json$", false);

        assertThat(folder.getRoot().listFiles().length, is(13)); // 5 abc files + 7 test_*_file.test files + week_old_file
    }

    @Test
    public void testDeleteOld() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 600, null, false);

        assertThat(folder.getRoot().listFiles().length, is(13)); // All 23 - 6 (from test_*_.json) - 3 (from test_*_file.test) - 1 week old file
    }

    @Test
    public void testDeleteWithAllParameters() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 200, "^test_.*\\.json$", false);

        assertThat(folder.getRoot().listFiles().length, is(15)); // All 23 - 8 (from test_*_.json)
    }

    @Test
    public void testDeleteWithSubDirectoriesNoRecursive() throws IOException {
        initSubDirectories();
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "^test_.*\\.json$", false);

        // 6 test_*.json from test_folder1/
        // + 9 test_*.json and 4 abc_*.json from test_folder2/
        // + 13 test_*.json from test_folder2/subSubFolder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + test_folder1/ and test_folder2/ and test_folder2/subSubFolder2/ themselves
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(48));
    }

    @Test
    public void testDeleteWithSubDirectoriesRecursive() throws IOException {
        initSubDirectories();
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "^test_.*\\.json$", true);

        // 4 abc_*.json from test_folder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + test_folder2/ itself
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(18));
    }

    @Test
    public void testDeleteFilesWhereFilenameRegexAlsoMatchesDirectories() throws IOException {
        initSubDirectories();

        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "^test_", false);

        assertThat(folder.getRoot().listFiles().length, is(8)); // 5 abc files + 1 week_old_file + 2 directories
    }

    @Test
    public void testGetContentsOfNonExistingDirectory() throws IOException {
        assertArrayEquals(new File[0], DeleteOldAppData.getContentsOfDirectory("/some/made/up/dir/"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testDeleteFilesExceptNMostRecentWithNegativeN() {
        DeleteOldAppData.deleteFilesExceptNMostRecent(folder.getRoot().getAbsolutePath(), -5);
    }

    @Test
    public void testDeleteFilesExceptFiveMostRecent() {
        DeleteOldAppData.deleteFilesExceptNMostRecent(folder.getRoot().getAbsolutePath(), 5);

        assertThat(folder.getRoot().listFiles().length, is(5));

        String[] oldestFiles = {"test_5_file.test", "test_6_file.test", "test_8.json", "test_9.json", "week_old_file.json"};
        String[] remainingFiles = folder.getRoot().list();
        Arrays.sort(remainingFiles);

        assertArrayEquals(oldestFiles, remainingFiles);
    }

    @Test
    public void testDeleteFilesExceptNMostRecentWithLargeN() {
        String[] filesPreDelete = folder.getRoot().list();

        DeleteOldAppData.deleteFilesExceptNMostRecent(folder.getRoot().getAbsolutePath(), 50);

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

        DeleteOldAppData.deleteFilesLargerThan(folder.getRoot(), 10);

        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(58));
        assertThat(temp1.exists() || temp2.exists() || temp3.exists(), is(false));
    }

    @Test
    public void testDeleteDirectories() throws IOException {
        initSubDirectories();

        DeleteOldAppData.deleteDirectories(folder.getRoot().getAbsolutePath(), 0, ".*folder2");

        //23 files in root
        // + 6 in test_folder1 + test_folder1 itself
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(30));
    }

    @Test
    public void testDeleteDirectoriesBasedOnAge() throws IOException {
        initSubDirectories();

        DeleteOldAppData.deleteDirectories(folder.getRoot().getAbsolutePath(), 50, ".*folder.*");

        //23 files in root
        // + 13 in test_folder2
        // + 13 in subSubFolder2
        // + test_folder2 + subSubFolder2 itself
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(51));
    }

    @Test
    public void testRecursivelyDeleteDirectory() throws IOException {
        initSubDirectories();
        DeleteOldAppData.recursiveDelete(folder.getRoot());
        assertTrue(!folder.getRoot().exists());
    }

    @Test
    public void testRecursivelyDeleteRegularFile() throws IOException {
        File file = folder.newFile();
        assertTrue(file.exists());
        assertTrue(file.isFile());
        DeleteOldAppData.recursiveDelete(file);
        assertTrue(!file.exists());
    }

    @Test
    public void testRecursivelyDeleteNonExistingFile() throws IOException {
        File file = folder.getRoot().toPath().resolve("non-existing-file.json").toFile();
        assertTrue(!file.exists());
        DeleteOldAppData.recursiveDelete(file);
        assertTrue(!file.exists());
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

    private void initSubDirectories() throws IOException {
        File subFolder1 = folder.newFolder("test_folder1");
        File subFolder2 = folder.newFolder("test_folder2");
        File subSubFolder2 = folder.newFolder("test_folder2/subSubFolder2");


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

    public static void writeNBytesToFile(File file, int nBytes) throws IOException {
        Files.write(file.toPath(), new byte[nBytes]);
    }
}
