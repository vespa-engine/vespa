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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
class DirConfigSource {

    private final Set<String> checked = new HashSet<>();
    private final File tempFolder;
    private boolean doubleChecked;

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

    synchronized void clearCheckedConfigs() {
        checked.clear();
        doubleChecked = false;
    }

    synchronized void awaitConfigChecked(long millis) throws InterruptedException {
         long remaining, doom = System.currentTimeMillis() + millis;
         while ( ! doubleChecked && (remaining = doom - System.currentTimeMillis()) > 0) wait(remaining);
        assertTrue(doubleChecked, "some config should be checked more than once during " + millis + " millis; checked ones: " + checked);
    }

    ConfigSource configSource() {
        return new DirSource(tempFolder) {
            @Override public FileSource getFile(String name) {
                return new FileSource(new File(tempFolder, name)) {
                    @Override public long getLastModified() {
                        synchronized (DirConfigSource.this) {
                            if ( ! checked.add(name)) {
                                doubleChecked = true;
                                DirConfigSource.this.notifyAll();
                            }
                            return super.getLastModified();
                        }
                    }
                };
            }
        };
    }

}
