// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.net.HostName;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * FileAcquirer and FileRegistry working on a local directory.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class LocalFileDb implements FileAcquirer, FileRegistry {
    private static final Constructor<FileReference> fileReferenceConstructor = createFileReferenceConstructor();

    private final Map<FileReference, File> fileReferenceToFile = new HashMap<>();
    private final Path appPath;

    public LocalFileDb(Path appPath) {
        this.appPath = appPath;
    }

    /* FileAcquirer overrides */
    @Override
    public File waitFor(FileReference reference, long l, TimeUnit timeUnit) {
        synchronized (this) {
            File file = fileReferenceToFile.get(reference);
            if (file == null) {
                throw new RuntimeException("Invalid file reference " + reference);
            }
            return file;
        }
    }

    @Override
    public void shutdown() {
    }

    /* FileRegistry overrides */
    public FileReference addFile(String relativePath) {
        File file = appPath.resolve(relativePath).toFile();
        if (!file.exists()) {
            throw new RuntimeException("The file does not exist: " + file.getPath());
        }

        FileReference fileReference = null;
        try {
            fileReference = fileReferenceConstructor.newInstance("LocalFileDb:" + relativePath);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Unable to create new FileReference", e);
        }
        fileReferenceToFile.put(fileReference, file);
        return fileReference;
    }

    @Override
    public List<Entry> export() {
        return fileReferenceToFile.entrySet().stream().map(entry -> new Entry(entry.getValue().getPath(), entry.getKey()))
                .collect(Collectors.toList());
    }

    @Override
    public FileReference addUri(String uri) {
        throw new RuntimeException("addUri(String uri) is not implemented here.");
    }

    public String fileSourceHost() {
        return HostName.getLocalhost();
    }

    public Set<String> allRelativePaths() {
        return fileReferenceToFile.values().stream().map(File::getPath).collect(Collectors.toSet());
    }

    private static Constructor<FileReference> createFileReferenceConstructor() {
        try {
            Constructor<FileReference> method = FileReference.class.getDeclaredConstructor(String.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
