package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class SiaUtilsTest {

    @Rule
    public TemporaryFolder tempDirectory = new TemporaryFolder();

    @Test
    public void it_finds_all_identity_names_from_files_in_sia_keys_directory() throws IOException {
        Path siaRoot = tempDirectory.getRoot().toPath();
        Files.createDirectory(siaRoot.resolve("keys"));
        AthenzService fooService = new AthenzService("my.domain.foo");
        Files.createFile(SiaUtils.getPrivateKeyFile(siaRoot, fooService));
        AthenzService barService = new AthenzService("my.domain.bar");
        Files.createFile(SiaUtils.getPrivateKeyFile(siaRoot, barService));

        List<AthenzService> siaIdentities = SiaUtils.findSiaServices(siaRoot);
        assertThat(siaIdentities.size(), equalTo(2));
        assertThat(siaIdentities, hasItem(fooService));
        assertThat(siaIdentities, hasItem(barService));
    }

}