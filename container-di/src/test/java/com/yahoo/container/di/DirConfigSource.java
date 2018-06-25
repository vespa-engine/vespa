// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class DirConfigSource {
    private final TemporaryFolder tempFolder = createTemporaryFolder();
    public final ConfigSource configSource;

    public DirConfigSource(String testSourcePrefix) {
        this.configSource = new ConfigSourceSet(testSourcePrefix + new Random().nextLong());
    }

    public void writeConfig(String name, String contents) {
        File file = new File(tempFolder.getRoot(), name + ".cfg");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        printFile(file, contents + "\n");
    }

    public String configId() {
        return "dir:" + tempFolder.getRoot().getPath();
    }

    public ConfigSource configSource() {
        return configSource;
    }

    public void cleanup() {
        tempFolder.delete();
    }

    private static void printFile(File f, String content) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static TemporaryFolder createTemporaryFolder() {
        TemporaryFolder folder = new TemporaryFolder();
        try {
            folder.create();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return folder;
    }
}
