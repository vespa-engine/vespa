// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application;

import com.yahoo.config.model.application.provider.Bundle;
import com.yahoo.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.jar.JarFile;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class ConfigDefinitionDirTest {
    private static final String bundleFileName = "com.yahoo.searcher1.jar";
    private static final File bundleFile = new File("src/test/resources/defdircomponent/" + bundleFileName);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void require_that_defs_are_added() throws IOException {
        File defDir = temporaryFolder.newFolder();
        ConfigDefinitionDir dir = new ConfigDefinitionDir(defDir);
        Bundle bundle = new Bundle(new JarFile(bundleFile), bundleFile);
        assertThat(defDir.listFiles().length, is(0));
        dir.addConfigDefinitionsFromBundle(bundle, new ArrayList<Bundle>());
        assertThat(defDir.listFiles().length, is(1));
    }


    @Test
    public void require_that_conflicting_defs_are_not_added() throws IOException {
        File defDir = temporaryFolder.newFolder();
        IOUtils.writeFile(new File(defDir, "foo.def"), "alreadyexists", false);
        ConfigDefinitionDir dir = new ConfigDefinitionDir(defDir);
        Bundle bundle = new Bundle(new JarFile(bundleFile), bundleFile);
        ArrayList<Bundle> bundlesAdded = new ArrayList<>();

        // Conflict with built-in config definition
        try {
            dir.addConfigDefinitionsFromBundle(bundle, bundlesAdded);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains
                    ("The config definition with name 'bar.foo' contained in the bundle '" +
                            bundleFileName +
                            "' conflicts with a built-in config definition"));
        }
        bundlesAdded.add(bundle);

        // Conflict with another bundle
        Bundle bundle2 = new Bundle(new JarFile(bundleFile), bundleFile);
        try {
            dir.addConfigDefinitionsFromBundle(bundle2, bundlesAdded);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(),
                    is("The config definition with name 'bar.foo' contained in the bundle '" +
                            bundleFileName +
                            "' conflicts with the same config definition in the bundle 'com.yahoo.searcher1.jar'. Please choose a different name."));
        }
    }
}
