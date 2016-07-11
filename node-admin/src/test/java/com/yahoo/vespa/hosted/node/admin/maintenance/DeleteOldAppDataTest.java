package com.yahoo.vespa.hosted.node.admin.maintenance;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author valerijf
 */
public class DeleteOldAppDataTest {
    private File folder;

    @Before
    public void initFiles() throws IOException {
        folder = File.createTempFile("temp_folder", String.valueOf(System.currentTimeMillis()));
        folder.delete(); // Delete the file so that we can create directory with the same name
        folder.mkdir(); // Create create the directory
        folder.deleteOnExit(); // Delete the directory after we are finished

        for (int i=0; i<10; i++) {
            File temp = File.createTempFile("test_" + i, ".json", folder);
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(130).toMillis());
            temp.deleteOnExit();
        }

        for (int i=0; i<7; i++) {
            File temp = File.createTempFile("test_" + i + "_file", ".test", folder);
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(250).toMillis());
            temp.deleteOnExit();
        }

        for (int i=0; i<5; i++) {
            File temp = File.createTempFile(i + "-abc", ".json", folder);
            temp.setLastModified(System.currentTimeMillis() - i*Duration.ofSeconds(80).toMillis());
            temp.deleteOnExit();
        }
    }

    @Test
    public void testDeleteAll() {
        String[] args = {"--path", folder.getAbsolutePath()};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(0));
    }

    @Test
    public void testDeletePrefix() {
        String[] args = {"--path", folder.getAbsolutePath(), "--prefix", "test_"};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(5));
    }

    @Test
    public void testDeleteSuffix() {
        String[] args = {"--path", folder.getAbsolutePath(), "--suffix", ".json"};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(7));
    }

    @Test
    public void testDeletePrefixAndSuffix() {
        String[] args = {"--path", folder.getAbsolutePath(), "--prefix", "test_", "--suffix", ".json"};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(12));
    }

    @Test
    public void testDeleteOld() {
        String[] args = {"--path", folder.getAbsolutePath(), "--max_age", "600"};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(13)); // All 22 - 6 (from test_*_.json) - 3 (from test_*_file.test)
    }

    @Test
    public void testDeleteWithAllParameters() {
        String[] args = {"--path", folder.getAbsolutePath(), "--prefix", "test_", "--suffix", ".json", "--max_age", "200"};
        DeleteOldAppData.main(args);

        assertThat(folder.listFiles().length, is(14)); // All 22 - 8 (from test_*_.json)
    }
}
