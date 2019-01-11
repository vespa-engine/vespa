// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
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
        assertThat(SiaUtils.findSiaServices(siaRoot), is(emptyList()));
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
