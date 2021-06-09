// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.net.HostName;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A file registry for config server tests
 *
 * @author hmusum
 */
public class MockFileRegistry implements FileRegistry {

    private final List<Entry> entries = new ArrayList<>();
    private final AddFileInterface addFileInterface;

    public MockFileRegistry(File applicationDir, Path rootPath) {
        FileDirectory fileDirectory = new FileDirectory(rootPath.toFile());
        this.addFileInterface = new ApplicationFileManager(applicationDir, fileDirectory);
    }

    public FileReference addFile(String relativePath) {
        if (relativePath.isEmpty())
            relativePath = "./";
        addFileInterface.addFile(relativePath);

        FileReference fileReference = new FileReference(relativePath);
        entries.add(new Entry(relativePath, fileReference));
        return fileReference;
    }

    @Override
    public String fileSourceHost() { return HostName.getLocalhost(); }

    public List<Entry> export() { return entries; }

    @Override
    public FileReference addUri(String uri) {
        throw new IllegalArgumentException("FileReference addUri(String uri) is not implemented for " + getClass().getCanonicalName());
    }

    @Override
    public FileReference addBlob(ByteBuffer blob) {
        long blobHash = XXHashFactory.fastestJavaInstance().hash64().hash(blob, 0);
        String relativePath = "./" + Long.toHexString(blobHash) + ".blob";
        FileReference fileReference = addFileInterface.addBlob(blob, relativePath);

        entries.add(new Entry(relativePath, fileReference));
        return fileReference;
    }

}
