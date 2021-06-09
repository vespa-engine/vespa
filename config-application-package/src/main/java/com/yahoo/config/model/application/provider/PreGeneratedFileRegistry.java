// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    private final Map<String, String> path2Hash = new LinkedHashMap<>();

    private static final String entryDelimiter = "\t";
    private static final Pattern entryDelimiterPattern = Pattern.compile(entryDelimiter, Pattern.LITERAL);

    private PreGeneratedFileRegistry(Reader readerArg) {
        try (BufferedReader reader = new BufferedReader(readerArg)) {
            fileSourceHost = reader.readLine();
            if (fileSourceHost == null)
                throw new RuntimeException("Error while reading pre-generated file registry");

            String line;
            while ((line = reader.readLine()) != null) {
                addFromLine(line);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while reading pre-generated file registry", e);
        }
    }

    private void addFromLine(String line) {
        String[] parts = entryDelimiterPattern.split(line);
        if (parts.length < 2)
            throw new IllegalArgumentException("Cannot split '" + line + "' into two parts");
        addEntry(parts[0], parts[1]);
    }

    private void addEntry(String relativePath, String hash) {
        path2Hash.put(relativePath, hash);
    }

    public static String exportRegistry(FileRegistry registry) {
        List<Entry> entries = registry.export();
        StringBuilder builder = new StringBuilder();

        builder.append(registry.fileSourceHost()).append('\n');
        for (FileRegistry.Entry entry : entries) {
            builder.append(entry.relativePath).append(entryDelimiter).append(entry.reference.value()).append('\n');
        }

        return builder.toString();
    }

    public static PreGeneratedFileRegistry importRegistry(Reader reader) {
        return new PreGeneratedFileRegistry(reader);
    }

    public FileReference addFile(String relativePath) {
        String reference = path2Hash.get(relativePath);
        if (reference == null) {
            throw new IllegalArgumentException("File '" + relativePath + "' not found");
        }
        return new FileReference(reference);
    }

    @Override
    public FileReference addUri(String uri) {
        return new FileReference(path2Hash.get(uri));
    }
    @Override
    public FileReference addBlob(ByteBuffer blob) {
        return new FileReference(path2Hash.get(blob));
    }

    @Override
    public String fileSourceHost() {
        return fileSourceHost;
    }

    public Set<String> getPaths() {
        return path2Hash.keySet();
    }

    @Override
    public List<Entry> export() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, String> entry : path2Hash.entrySet()) {
            entries.add(new Entry(entry.getKey(), new FileReference(entry.getValue())));
        }
        return entries;
    }
}
