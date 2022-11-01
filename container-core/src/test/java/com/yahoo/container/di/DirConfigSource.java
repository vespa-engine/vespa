// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.DirSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class DirConfigSource {

    private final File tempFolder;

    public DirConfigSource(File tmpDir) {
        this.tempFolder = tmpDir;
    }

    public void writeConfig(String name, String contents) {
        try {
            Files.writeString(tempFolder.toPath().resolve(name + ".cfg"), contents + "\n", UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String configId() {
        return "dir:" + tempFolder.getPath();
    }

    public ConfigSource source() {
        return new DirSource(tempFolder);
    }

}
