// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.modelintegration.utils;

import ai.vespa.modelintegration.evaluator.OnnxStreamParser;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CheckedInputStream;

/**
 * Union object that contains either model file path or data.
 *
 * @author bjorncs
 * @author glebashnik
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
            var model = Paths.get(path.get());
            // Hashes the model while reading its external data locations, which are then hashed as well.
            try (var hasher = XXHashFactory.fastestInstance().newStreamingHash64(0);
                 var inputStream = new CheckedInputStream(new BufferedInputStream(Files.newInputStream(model)),
                                                          hasher.asChecksum())) {
                var externalDataLocations = OnnxStreamParser.tryGetExternalDataLocations(inputStream);
                // Hashes the rest of the file, as the parser may stop before the end.
                inputStream.transferTo(OutputStream.nullOutputStream());
                hashExternalDataFiles(model, externalDataLocations, hasher);
                return hasher.getValue();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return XXHashFactory.fastestInstance().hash64().hash(data.get(), 0, data.get().length, 0);
        }
    }

    /**
     * Hashes the location, size and modification time of external data files
     * instead of their content because the content can be large and takes time to hash.
     */
    private static void hashExternalDataFiles(Path model, Set<Path> locations, StreamingXXHash64 hasher)
            throws IOException {
        for (var location : new TreeSet<>(locations)) {
            long size = -1;
            long modified = -1;
            try {
                var attributes = Files.readAttributes(model.resolveSibling(location), BasicFileAttributes.class);
                size = attributes.size();
                modified = attributes.lastModifiedTime().toMillis();
            } catch (IOException e) {
                // A missing or unreadable file is left for the runtime to report when loading the model
            }
            var locationBytes = location.toString().getBytes(StandardCharsets.UTF_8);
            // Prefixes the location with its length to keep the encoding unambiguous
            var identity = ByteBuffer.allocate(Integer.BYTES + locationBytes.length + 2 * Long.BYTES)
                    .putInt(locationBytes.length)
                    .put(locationBytes)
                    .putLong(size)
                    .putLong(modified)
                    .array();
            hasher.update(identity, 0, identity.length);
        }
    }
}
