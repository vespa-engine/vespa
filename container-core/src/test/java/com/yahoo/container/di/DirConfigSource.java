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
 * Config test helper class that adds atomicity across all created config subscriptions.
 * This is done by requiring each new generation to be seen by all active subscriptions before
 * allowing a new generation. It is required only by that one {@link ContainerTest} test where
 * config is fetched from a different thread; all other test config usages are single-threaded (?).
 *
 * @author jonmv
 */
class DirConfigSource {

    private final Object monitor = new Object();
    private final Set<String> subs = new HashSet<>();
    private final Set<String> unchecked = new HashSet<>();
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
        try {
            synchronized (monitor) {
                while ( ! unchecked.isEmpty()) monitor.wait();
                unchecked.addAll(subs);
                ++generation;
            }
        }
        catch (InterruptedException e) {
            throw new AssertionError(e);
        }
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
                        synchronized (monitor) {
                            unchecked.remove(name);
                            if (unchecked.isEmpty()) monitor.notifyAll();
                            return generation;
                        }
                    }
                };
            }
        };
    }

}
