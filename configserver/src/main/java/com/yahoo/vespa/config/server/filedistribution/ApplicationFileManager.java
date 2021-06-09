// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

/**
 * @author baldersheim
 */
public class ApplicationFileManager implements AddFileInterface {

    private final File applicationDir;
    private final FileDirectory fileDirectory;

    ApplicationFileManager(File applicationDir, FileDirectory fileDirectory) {
        this.applicationDir = applicationDir;
        this.fileDirectory = fileDirectory;
    }

    @Override
    public FileReference addFile(String relativePath) {
        return fileDirectory.addFile(new File(applicationDir, relativePath));
    }

    @Override
    public FileReference addUri(String uri, String relativePath) {
        download(uri, relativePath);
        return addFile(relativePath);
    }

    @Override
    public FileReference addBlob(ByteBuffer blob, String relativePath) {
        writeBlob(blob, relativePath);
        return addFile(relativePath);
    }

    private void writeBlob(ByteBuffer blob, String relativePath) {
        File file = new File(applicationDir, relativePath);
        FileOutputStream fos = null;
        try {
            Files.createDirectories(file.toPath().getParent());
            fos = new FileOutputStream(file.getAbsolutePath());
            fos.write(blob.array(), blob.arrayOffset(), blob.remaining());
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed creating directory " + file.getParent(), e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed closing down after writing blob of size " + blob.remaining() + " to " + file.getAbsolutePath());
            }
        }
    }

    private void download(String uri, String relativePath) {
        File file = new File(applicationDir, relativePath);
        FileOutputStream fos = null;
        ReadableByteChannel rbc = null;
        try {
            Files.createDirectories(file.toPath().getParent());
            URL website = new URL(uri);
            rbc = Channels.newChannel(website.openStream());
            fos = new FileOutputStream(file.getAbsolutePath());
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (SocketTimeoutException e) {
            throw new IllegalArgumentException("Failed connecting to or reading from " + uri, e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed creating directory " + file.getParent(), e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (rbc != null) {
                    rbc.close();
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed closing down after downloading " + uri + " to " + file.getAbsolutePath());
            }
        }
    }
}
