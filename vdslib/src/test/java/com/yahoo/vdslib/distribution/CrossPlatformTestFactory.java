// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * Helper class to implement unit tests that should produce the same result in different implementations.
 */
public abstract class CrossPlatformTestFactory {
    private final String directory;
    private final String name;

    public CrossPlatformTestFactory(String directory, String name) {
        this.directory = directory;
        this.name = name;
    }

    public String getName() { return name; }

    public void loadTestResults() throws Exception {
        File reference = new File(directory, name + ".reference.results");
        if (!reference.exists()) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(reference))) {
            StringBuilder sb = new StringBuilder();
            while (true) {
                String line = br.readLine();
                if (line == null) break;
                sb.append(line);
            }
            parse(sb.toString());
        }
    }

    public void recordTestResults() throws Exception {
        File results = new File(directory, name + ".java.results");
        try (FileWriter fw = new FileWriter(results)) {
            fw.write(serialize());
        }
    }

    public abstract String serialize();
    public abstract void parse(String serialized) throws Exception;
}
