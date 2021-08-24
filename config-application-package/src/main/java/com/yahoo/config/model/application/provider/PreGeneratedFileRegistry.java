// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registry of files added earlier (i.e. during deployment)
 *
 * @author Tony Vaagenes
 */
public class PreGeneratedFileRegistry implements FileRegistry {

    private final String fileSourceHost;
    private final Map<String, FileReference> path2Hash;

    private static final String entryDelimiter = "\t";
    private static final Pattern entryDelimiterPattern = Pattern.compile(entryDelimiter, Pattern.LITERAL);

    public static Map<String, FileReference> decode(BufferedReader reader) {
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
    private PreGeneratedFileRegistry(Reader readerArg) {
        try (BufferedReader reader = new BufferedReader(readerArg)) {
            fileSourceHost = reader.readLine();
            if (fileSourceHost == null)
                throw new RuntimeException("Error while reading pre-generated file registry");

            path2Hash = decode(reader);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading pre-generated file registry", e);
        }
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

    public static PreGeneratedFileRegistry importRegistry(Reader reader) {
        return new PreGeneratedFileRegistry(reader);
    }

    public FileReference addFile(String relativePath) {
        FileReference reference = path2Hash.get(relativePath);
        if (reference == null) {
            throw new IllegalArgumentException("File '" + relativePath + "' not found");
        }
        return reference;
    }

    @Override
    public FileReference addUri(String uri) {
        FileReference reference = path2Hash.get(uri);
        if (reference == null) {
            throw new IllegalArgumentException("Uri '" + uri + "' not found");
        }
        return reference;
    }
    @Override
    public FileReference addBlob(ByteBuffer blob) {
        String blobName = FileRegistry.blobName(blob);
        FileReference reference = path2Hash.get(blobName);
        if (reference == null) {
            throw new IllegalArgumentException("Blob '" + blobName + "(" + blob.remaining()+ ")' not found");
        }
        return reference;
    }

    public Set<String> getPaths() {
        return path2Hash.keySet();
    }

    @Override
    public List<Entry> export() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, FileReference> entry : path2Hash.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }
}
