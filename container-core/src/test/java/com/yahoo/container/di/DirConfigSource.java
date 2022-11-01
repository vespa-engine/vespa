// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.DirSource;
import com.yahoo.config.subscription.FileSource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
class DirConfigSource {

    private final File tempFolder;
    private long generation = 1;

    DirConfigSource(File tmpDir) {
        this.tempFolder = tmpDir;
    }

    void writeConfig(String name, String contents) {
        try {
            Files.writeString(tempFolder.toPath().resolve(name + ".cfg"), contents + "\n");
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void incrementGeneration() {
        ++generation;
    }

    String configId() {
        return "dir:" + tempFolder.getPath();
    }

    ConfigSource source() {
        return new DirSource(tempFolder) {
            @Override public FileSource get(String name) {
                subs.add(name);
                return new FileSource(new File(tempFolder, name)) {
                    @Override public long generation() {
                        return generation;
                    }
                };
            }
        };
    }

}
