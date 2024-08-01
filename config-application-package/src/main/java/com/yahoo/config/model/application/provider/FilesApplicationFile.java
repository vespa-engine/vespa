// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.util.ConfigUtils;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author Ulf Lilleengen
 * @author Vegard Havdal
 */
public class FilesApplicationFile extends ApplicationFile {

    private static final Logger log = Logger.getLogger("FilesApplicationFile");
    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();

    public FilesApplicationFile(Path path, File file) {
        super(path);
        this.file = file;
    }

    @Override
    public boolean isDirectory() { return file.isDirectory(); }

    @Override
    public boolean exists() { return file.exists(); }

    @Override
    public ApplicationFile delete() {
        if (file.isDirectory()) {
            if (!listFiles().isEmpty())
                throw new RuntimeException("Can't delete, directory not empty: " + this + "(" + listFiles() + ")." + listFiles().size());

            var files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        }
        if (!file.delete())
            throw new IllegalStateException("Unable to delete: " + this);
        uncheck(() -> writeMetaFile("", ContentStatusDeleted));
        return this;
    }

    public static boolean deleteFile(File path) {
        if (path.exists() && path.isDirectory()) {
            File[] files = path.listFiles();
            for (File value : files) {
                if (value.isDirectory())
                    deleteFile(value);
                else
                    value.delete();
            }
        }
        return(path.delete());
    }

    @Override
    public Reader createReader() throws FileNotFoundException {
        return new FileReader(file);
    }

    @Override
    public InputStream createInputStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    public ApplicationFile createDirectory() {
        if (file.isDirectory()) return this;
        if (file.exists()) {
            throw new IllegalArgumentException("Unable to create directory, file exists: "+file);
        }
        if (!file.mkdirs()) {
            throw new IllegalArgumentException("Unable to create directory: "+file);
        }
        try {
            writeMetaFile("", ContentStatusNew);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public ApplicationFile writeFile(Reader input) {
        return uncheck(() -> writeFile(Utf8.toBytes(IOUtils.readAll(input))));
    }

    @Override
    public ApplicationFile writeFile(InputStream input) {
        return uncheck(() -> writeFile(input.readAllBytes()));
    }

    private ApplicationFile writeFile(byte[] data) {
        if (file.getParentFile() != null)
            file.getParentFile().mkdirs();

        String status = file.exists() ? ApplicationFile.ContentStatusChanged : ApplicationFile.ContentStatusNew;
        uncheck(() -> Files.write(file.toPath(), data));
        uncheck(() -> writeMetaFile(data, status));
        return this;
    }

    @Override
    public ApplicationFile appendFile(String value) {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try {
            String status = file.exists() ? ContentStatusChanged : ContentStatusNew;
            IOUtils.writeFile(file, value, true);
            writeMetaFile(value, status);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public List<ApplicationFile> listFiles(final PathFilter filter) {
        List<ApplicationFile> files = new ArrayList<>();
        if (!file.isDirectory()) return files;

        FileFilter fileFilter = pathname -> filter.accept(path.append(pathname.getName()));
        File[] list = file.listFiles(fileFilter);
        if (list == null) return files;

        for (File child : list) {
            // Ignore dot-files.
            if (!child.getName().startsWith(".")) {
                files.add(new FilesApplicationFile(path.append(child.getName()), child));
            }
        }
        return files;
    }

    private void writeMetaFile(String data, String status) throws IOException {
        writeMetaFile(Utf8.toBytes(data), status);
    }

    private void writeMetaFile(byte[] data, String status) throws IOException {
        File metaDir = createMetaDir();
        log.log(Level.FINE, () -> "meta dir=" + metaDir);
        File metaFile = new File(metaDir + "/" + getPath().getName());
        if (status == null)
            status = metaFile.exists() ? ContentStatusChanged : ContentStatusNew;

        String hash = (file.isDirectory() || status.equals(ContentStatusDeleted))
                ? ""
                : ConfigUtils.getMd5(data);
        mapper.writeValue(metaFile, new MetaData(status, hash));
    }

    private File createMetaDir() {
        File metaDir = getMetaDir();
        if (!metaDir.exists()) {
            log.log(Level.FINE, () -> "Creating meta dir " + metaDir);
            metaDir.mkdirs();
        }
        return metaDir;
    }

    private File getMetaDir() {
        String substring = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf("/") + 1);
        return new File(substring + Path.fromString(".meta/"));
    }

    public MetaData getMetaData() {
        File metaDir = getMetaDir();
        File metaFile = new File(metaDir + "/" + getPath().getName());
        log.log(Level.FINE, () -> "Getting metadata for " + metaFile);
        if (metaFile.exists()) {
            try {
                return mapper.readValue(metaFile, MetaData.class);
            } catch (IOException e) {
                System.out.println("whot:" + Exceptions.toMessageString(e));
                // return below
            }
        }
        try {
            if (file.isDirectory()) {
                return new MetaData(ContentStatusNew, "");
            } else {
                return new MetaData(ContentStatusNew, ConfigUtils.getMd5(IOUtils.readAll(createReader())));
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override public long getSize() { return file.length(); }

    @Override
    public int compareTo(ApplicationFile other) {
        if (other == this) return 0;
        return this.getPath().getName().compareTo((other).getPath().getName());
    }

}
