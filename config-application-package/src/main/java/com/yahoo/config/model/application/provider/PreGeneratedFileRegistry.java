// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Registry of files added earlier (i.e. during deployment)
 *
 * @author tonytv
 */
public class PreGeneratedFileRegistry implements FileRegistry {

    private final String fileSourceHost;
    private final Map<String, String> path2Hash = new LinkedHashMap<>();

    private static String entryDelimiter = "\t";
    private static Pattern entryDelimiterPattern = Pattern.compile(entryDelimiter, Pattern.LITERAL);

    private PreGeneratedFileRegistry(Reader readerArg) {
        BufferedReader reader = new BufferedReader(readerArg);
        try {
            fileSourceHost = reader.readLine();
            if (fileSourceHost == null)
                throw new RuntimeException("Error while reading pre generated file registry");

            String line;
            while ((line = reader.readLine()) != null) {
                addFromLine(line);
            }
        } catch(IOException e) {
            throw new RuntimeException("Error while reading pre generated file registry", e);
        } finally {
            try {
                reader.close();
            } catch(IOException e) {}
        }
    }

    private void addFromLine(String line) {
        String[] parts = entryDelimiterPattern.split(line);
        addEntry(parts[0], parts[1]);
    }

    private void addEntry(String relativePath, String hash) {
        path2Hash.put(relativePath, hash);
    }

    public static String exportRegistry(FileRegistry registry) {
        List<FileRegistry.Entry> entries = registry.export();
        StringBuilder builder = new StringBuilder();

        builder.append(registry.fileSourceHost()).append('\n');
        for (FileRegistry.Entry entry : entries) {
            builder.append(entry.relativePath).append(entryDelimiter).append(entry.reference.value()).
                    append('\n');
        }

        return builder.toString();
    }

    public static PreGeneratedFileRegistry importRegistry(Reader reader) {
        return new PreGeneratedFileRegistry(reader);
    }

    public FileReference addFile(String relativePath) {
        return new FileReference(path2Hash.get(relativePath));
    }

    @Override
    public FileReference addUri(String uri) {
        // TODO: uri's should also be pregenrated.
        return null;
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
