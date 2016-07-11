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

        File temp = folder.newFile("test_week_old_file.json");
        temp.setLastModified(System.currentTimeMillis() - Duration.ofDays(8).toMillis());
    }

    @Test
    public void testDeleteAll() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--max_age=0"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(0));
    }

    @Test
    public void testDeleteAllDefaultMaxAge() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath()};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(22));
    }

    @Test
    public void testDeletePrefix() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--prefix", "test_", "--max_age=0"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(5));
    }

    @Test
    public void testDeleteSuffix() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--suffix", ".json", "--max_age=0"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(7));
    }

    @Test
    public void testDeletePrefixAndSuffix() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--prefix", "test_", "--suffix", ".json", "--max_age=0"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(12));
    }

    @Test
    public void testDeleteOld() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--max_age", "600"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(13)); // All 23 - 6 (from test_*_.json) - 3 (from test_*_file.test) - 1 week old file
    }

    @Test
    public void testDeleteWithAllParameters() {
        String[] args = {"--path", folder.getRoot().getAbsolutePath(), "--prefix", "test_", "--suffix", ".json", "--max_age", "200"};
        DeleteOldAppData.main(args);

        assertThat(folder.getRoot().listFiles().length, is(14)); // All 23 - 8 (from test_*_.json) - 1 week old file
    }
}
