// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import net.jpountz.lz4.LZ4FrameOutputStream;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * @author baldersheim
 */
public class ApplicationFileManager implements AddFileInterface {

    private final File applicationDir;
    private final FileDirectory fileDirectory;
    private final boolean isHosted;

    ApplicationFileManager(File applicationDir, FileDirectory fileDirectory, boolean isHosted) {
        this.applicationDir = applicationDir;
        this.fileDirectory = fileDirectory;
        this.isHosted = isHosted;
    }

    @Override
    public FileReference addFile(Path path) throws IOException {
        File file = new File(applicationDir, path.getRelative());
        return addFile(file);
    }

    private FileReference addFile(File file) throws IOException {
        return fileDirectory.addFile(file);
    }

    @Override
    public FileReference addUri(String uri, Path path) {
        if (isHosted) throw new IllegalArgumentException("URI type resources are not supported in this Vespa cloud");
        try (TmpDir tmp = new TmpDir()) {
            return addFile(download(uri, tmp.dir, path));
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public FileReference addBlob(ByteBuffer blob, Path path) {
        try (TmpDir tmp = new TmpDir()) {
            return addFile(writeBlob(blob, tmp.dir, path));
        }
        catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private File writeBlob(ByteBuffer blob, File tmpDir, Path path) {
        FileOutputStream fos = null;
        File file = null;
        try {
            file = new File(tmpDir, path.getRelative());
            Files.createDirectories(file.getParentFile().toPath());
            fos = new FileOutputStream(file);
            if (path.last().endsWith(".lz4")) {
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

    private File download(String uri, File tmpDir, Path path) {
        File file = null;
        FileOutputStream fos = null;
        ReadableByteChannel rbc = null;
        try {
            file = new File(tmpDir, path.getRelative());
            Files.createDirectories(file.getParentFile().toPath());
            URL website = new URL(uri);
            if ( ! List.of("http", "https").contains(website.getProtocol().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("only HTTP(S) supported for URI type resources");
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

    private static class TmpDir implements Closeable {
        final File dir = Files.createTempDirectory("").toFile();
        private TmpDir() throws IOException { }
        @Override public void close() { IOUtils.recursiveDeleteDir(dir); }
    }

}
