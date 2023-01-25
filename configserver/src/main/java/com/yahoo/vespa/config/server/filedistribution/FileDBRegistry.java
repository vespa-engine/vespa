// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import net.jpountz.xxhash.XXHashFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * File registry for one application package
 *
 * @author Tony Vaagenes
 */
public class FileDBRegistry implements FileRegistry {

    private final boolean silenceNonExistingFiles;
    private final AddFileInterface manager;
    private final Map<String, FileReference> fileReferenceCache = new HashMap<>();
    private static final String entryDelimiter = "\t";
    private static final Pattern entryDelimiterPattern = Pattern.compile(entryDelimiter, Pattern.LITERAL);

    public FileDBRegistry(AddFileInterface manager) {
        this(manager, Map.of(), false);
    }

    private FileDBRegistry(AddFileInterface manager, Map<String, FileReference> knownReferences, boolean silenceNonExistingFiles) {
        this.silenceNonExistingFiles = silenceNonExistingFiles;
        this.manager = manager;
        fileReferenceCache.putAll(knownReferences);
    }

    public static FileDBRegistry create(AddFileInterface manager, Reader persistedState) {
        try (BufferedReader reader = new BufferedReader(persistedState)) {
            String ignoredFileSourceHost = reader.readLine();
            if (ignoredFileSourceHost == null)
                throw new RuntimeException("No file source host");
            return new FileDBRegistry(manager, decode(reader), true);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading pre-generated file registry", e);
        }
    }

    static Map<String, FileReference> decode(BufferedReader reader) {
        Map<String, FileReference> refs = new HashMap<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = entryDelimiterPattern.split(line);
                if (parts.length < 2)
                    throw new IllegalArgumentException("Cannot split '" + line + "' into two parts");
                refs.put(parts[0], new FileReference(parts[1]));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while reading pre-generated file registry", e);
        }
        return refs;
    }

    @Override
    public synchronized FileReference addFile(String relativePath) {
        if (relativePath.startsWith("/"))
            throw new IllegalArgumentException(relativePath + " is not relative");

        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(relativePath));
        return cachedReference.orElseGet(() -> {
            try {
                FileReference newRef = manager.addFile(Path.fromString(relativePath));
                fileReferenceCache.put(relativePath, newRef);
                return newRef;
            } catch (FileNotFoundException e) {
                if (silenceNonExistingFiles) {
                    return new FileReference("non-existing-file");
                } else {
                    throw new IllegalArgumentException(e);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            }
        );
    }

    @Override
    public synchronized FileReference addUri(String uri) {
        String relativePath = uriToRelativeFile(uri);
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(uri));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addUri(uri, Path.fromString(relativePath));
            fileReferenceCache.put(uri, newRef);
            return newRef;
        });
    }

    @Override
    public synchronized FileReference addBlob(String blobName, ByteBuffer blob) {
        String relativePath = blobToRelativeFile(blobName);
        Optional<FileReference> cachedReference = Optional.ofNullable(fileReferenceCache.get(blobName));
        return cachedReference.orElseGet(() -> {
            FileReference newRef = manager.addBlob(blob, Path.fromString(relativePath));
            fileReferenceCache.put(blobName, newRef);
            return newRef;
        });
    }

    @Override
    public synchronized List<Entry> export() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, FileReference> entry : fileReferenceCache.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    // Used for testing only
    synchronized Map<String, FileReference> getMap() {
        return ImmutableMap.copyOf(fileReferenceCache);
    }

    public static String exportRegistry(FileRegistry registry) {
        List<Entry> entries = registry.export();
        StringBuilder builder = new StringBuilder();

        builder.append(HostName.getLocalhost()).append('\n');
        for (FileRegistry.Entry entry : entries) {
            builder.append(entry.relativePath).append(entryDelimiter).append(entry.reference.value()).append('\n');
        }

        return builder.toString();
    }

    private static String uriToRelativeFile(String uri) {
        String relative = "uri/" + XXHashFactory.fastestJavaInstance().hash64().hash(ByteBuffer.wrap(Utf8.toBytes(uri)), 0);
        if (uri.endsWith(".json")) {
            relative += ".json";
        } else if (uri.endsWith(".json.lz4")) {
            relative += ".json.lz4";
        } else if (uri.endsWith(".lz4")) {
            relative += ".lz4";
        }
        return relative;
    }

    private static String blobToRelativeFile(String blobName) {
        return "blob/" + blobName;
    }

}
