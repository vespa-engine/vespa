// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * FileAcquirer and FileRegistry working on a local directory.
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public class LocalFileDb implements FileAcquirer, FileRegistry {

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
                return new File(reference.value()); // Downloaded file reference: Will (hopefully) be resolved client side
            }
            return file;
        }
    }

    @Override
    public void shutdown() {
    }

    /* FileRegistry overrides */
    @Override
    public FileReference addFile(String relativePath) {
        File file = appPath.resolve(relativePath).toFile();
        Path relative = appPath.relativize(file.toPath()).normalize();
        if (relative.isAbsolute() || relative.startsWith(".."))
            throw new IllegalArgumentException(file + " is not a descendant of " + appPath);

        if (!file.exists()) {
            throw new RuntimeException("The file does not exist: " + file.getPath());
        }

        FileReference fileReference = new FileReference("LocalFileDb:" + relativePath);
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

    @Override
    public FileReference addBlob(String name, ByteBuffer blob) {
        writeBlob(blob, name);
        File file = appPath.resolve(name).toFile();
        FileReference fileReference = new FileReference("LocalFileDb:" + name);
        fileReferenceToFile.put(fileReference, file);
        return fileReference;
    }

    private void writeBlob(ByteBuffer blob, String relativePath) {
        try (FileOutputStream fos = new FileOutputStream(new File(appPath.toFile(), relativePath))) {
            if (relativePath.endsWith(".lz4")) {
                LZ4FrameOutputStream lz4 = new LZ4FrameOutputStream(fos);
                lz4.write(blob.array(), blob.arrayOffset(), blob.remaining());
                lz4.close();
            } else {
                fos.write(blob.array(), blob.arrayOffset(), blob.remaining());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed writing temp file", e);
        }
    }

}
