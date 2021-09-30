// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public FileReference addFile(String relativePath) throws IOException {
        return fileDirectory.addFile(new File(applicationDir, relativePath));
    }

    @Override
    public FileReference addFile(File file) throws IOException {
        return fileDirectory.addFile(file);
    }

    @Override
    public FileReference addUri(String uri, String relativePath) {
        File file = download(uri, relativePath);
        try {
            return addFile(file);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } finally {
            cleanup(file, relativePath);
        }
    }

    @Override
    public FileReference addBlob(ByteBuffer blob, String relativePath) {
        File file = writeBlob(blob, relativePath);
        try {
            return addFile(file);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } finally {
            cleanup(file, relativePath);
        }
    }

    private File writeBlob(ByteBuffer blob, String relativePath) {
        FileOutputStream fos = null;
        File file = null;
        try {
            Path path = Files.createTempDirectory("");
            file = new File(path.toFile(), relativePath);
            Files.createDirectories(file.getParentFile().toPath());
            fos = new FileOutputStream(file);
            if (relativePath.endsWith(".lz4")) {
                LZ4FrameOutputStream lz4 = new LZ4FrameOutputStream(fos);
                lz4.write(blob.array(), blob.arrayOffset(), blob.remaining());
                lz4.close();
            } else {
                fos.write(blob.array(), blob.arrayOffset(), blob.remaining());
            }
            return file;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed creating temp file", e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed closing down after writing blob of size " + blob.remaining() + " to " + file);
            }
        }
    }

    private File download(String uri, String relativePath) {
        File file = null;
        FileOutputStream fos = null;
        ReadableByteChannel rbc = null;
        try {
            Path path = Files.createTempDirectory("");
            file = new File(path.toFile(), relativePath);
            Files.createDirectories(file.getParentFile().toPath());
            URL website = new URL(uri);
            rbc = Channels.newChannel(website.openStream());
            fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            return file;
        } catch (SocketTimeoutException e) {
            throw new IllegalArgumentException("Failed connecting to or reading from " + uri, e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed creating " + file, e);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (rbc != null) {
                    rbc.close();
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed closing down after downloading " + uri + " to " + file);
            }
        }
    }

    private void cleanup(File file, String relativePath) {
        Path pathToDelete = file.toPath();
        // Remove as many components as there are in relative path to find temp path to delete
        for (int i = 0; i < Paths.get(relativePath).getNameCount(); i++)
            pathToDelete = pathToDelete.resolveSibling("");
        IOUtils.recursiveDeleteDir(pathToDelete.toFile());
    }


}
