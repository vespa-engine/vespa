// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.DirSource;
import com.yahoo.config.subscription.FileSource;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
class DirConfigSource {

    private final File tempFolder;

    DirConfigSource(File tmpDir) {
        this.tempFolder = tmpDir;
    }

    void writeConfig(String name, String contents) {
        try {
            Files.writeString(tempFolder.toPath().resolve(name + ".cfg"), contents);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    String configId() {
        return "dir:" + tempFolder.getPath();
    }

    ConfigSource configSource() {
        return new DirSource(tempFolder);
    }

}
