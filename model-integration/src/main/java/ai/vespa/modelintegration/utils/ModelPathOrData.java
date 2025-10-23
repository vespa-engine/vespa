// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.utils;

import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Union object that contains model file path or data.
 *
 * @author bjorncs
 */
public record ModelPathOrData(Optional<String> path, Optional<byte[]> data) {
    public ModelPathOrData {
        if (path.isEmpty() == data.isEmpty()) {
            throw new IllegalArgumentException("Either path or data must be non-empty");
        }
    }

    public static ModelPathOrData of(String path) {
        return new ModelPathOrData(Optional.of(path), Optional.empty());
    }

    public static ModelPathOrData of(byte[] data) {
        return new ModelPathOrData(Optional.empty(), Optional.of(data));
    }

    public long calculateHash() {
        if (path.isPresent()) {
            try (var hasher = XXHashFactory.fastestInstance().newStreamingHash64(0);
                 var in = Files.newInputStream(Paths.get(path.get()))) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    hasher.update(buffer, 0, bytesRead);
                }

                return hasher.getValue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return XXHashFactory.fastestInstance().hash64().hash(data.get(), 0, data.get().length, 0);
        }
    }
}
