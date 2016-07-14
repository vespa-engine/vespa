package com.yahoo.vespa.hosted.node.admin.maintenance;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author valerijf
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
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, null, null, false);

        assertThat(folder.getRoot().listFiles().length, is(0));
    }

    @Test
    public void testDeleteAllDefaultMaxAge() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(),
                DeleteOldAppData.DEFAULT_MAX_AGE_IN_SECONDS, null, null, false);

        assertThat(folder.getRoot().listFiles().length, is(22));
    }

    @Test
    public void testDeletePrefix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "test_", null, false);

        assertThat(folder.getRoot().listFiles().length, is(6)); // 5 abc files + 1 week_old_file
    }

    @Test
    public void testDeleteSuffix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, null, ".json", false);

        assertThat(folder.getRoot().listFiles().length, is(7));
    }

    @Test
    public void testDeletePrefixAndSuffix() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "test_", ".json", false);

        assertThat(folder.getRoot().listFiles().length, is(13)); // 5 abc files + 7 test_*_file.test files + week_old_file
    }

    @Test
    public void testDeleteOld() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 600, null, null, false);

        assertThat(folder.getRoot().listFiles().length, is(13)); // All 23 - 6 (from test_*_.json) - 3 (from test_*_file.test) - 1 week old file
    }

    @Test
    public void testDeleteWithAllParameters() {
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 200, "test_", ".json", false);

        assertThat(folder.getRoot().listFiles().length, is(15)); // All 23 - 8 (from test_*_.json)
    }

    @Test
    public void testDeleteWithSubDirectoriesNoRecursive() throws IOException {
        initSubDirectories();
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "test_", ".json", false);

        // 6 test_*.json from subFolder1/
        // + 9 test_*.json and 4 abc_*.json from subFolder2/
        // + 13 test_*.json from subFolder2/subSubFolder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + subFolder1/ and subFolder2/ and subFolder2/subSubFolder2/ themselves
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(48));
    }

    @Test
    public void testDeleteWithSubDirectoriesRecursive() throws IOException {
        initSubDirectories();
        DeleteOldAppData.deleteFiles(folder.getRoot().getAbsolutePath(), 0, "test_", ".json", true);

        // 4 abc_*.json from subFolder2/
        // + 7 test_*_file.test and 5 *-abc.json and 1 week_old_file from root
        // + subFolder2/ itself
        assertThat(getNumberOfFilesAndDirectoriesIn(folder.getRoot()), is(18));
    }

    private void initSubDirectories() throws IOException {
        File subFolder1 = folder.newFolder("subFolder1");
        File subFolder2 = folder.newFolder("subFolder2");
        File subSubFolder2 = folder.newFolder("subFolder2/subSubFolder2");

        for (int j=0; j<6; j++) {
            File.createTempFile("test_", ".json", subFolder1);
        }

        for (int j=0; j<9; j++) {
            File.createTempFile("test_", ".json", subFolder2);
        }

        for (int j=0; j<4; j++) {
            File.createTempFile("abc_", ".txt", subFolder2);
        }

        for (int j=0; j<13; j++) {
            File.createTempFile("test_", ".json", subSubFolder2);
        }
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
}
