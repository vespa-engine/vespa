// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class DirConfigSource {

    private final File tempFolder;
    public final ConfigSource configSource; // TODO jonmv: remove, unused

    public DirConfigSource(File tmpDir, String testSourcePrefix) {
        this.tempFolder = tmpDir;
        this.configSource = new ConfigSourceSet(testSourcePrefix + new Random().nextLong());
    }

    public void writeConfig(String name, String contents) {
        File file = new File(tempFolder, name + ".cfg");
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
        return "dir:" + tempFolder.getPath();
    }

    public ConfigSource configSource() {
        return configSource;
    }

    private static void printFile(File f, String content) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
