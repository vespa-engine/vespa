// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.function.Supplier;

/**
 * Class wrapping an integer stored on disk
 *
 * @author freva
 */
public class StoredInteger implements Supplier<OptionalInt> {

    private final Path path;
    private OptionalInt value;
    private boolean hasBeenRead = false;

    public StoredInteger(Path path) {
        this.path = path;
    }

    @Override
    public OptionalInt get() {
        if (!hasBeenRead) {
            try {
                String value = new String(Files.readAllBytes(path));
                this.value = OptionalInt.of(Integer.valueOf(value));
            } catch (NoSuchFileException e) {
                this.value = OptionalInt.empty();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read integer in " + path, e);
            }
            hasBeenRead = true;
        }
        return value;
    }

    public void write(int value) {
        try {
            Files.write(path, Integer.toString(value).getBytes());
            this.value = OptionalInt.of(value);
            this.hasBeenRead = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store integer in " + path, e);
        }
    }
}
