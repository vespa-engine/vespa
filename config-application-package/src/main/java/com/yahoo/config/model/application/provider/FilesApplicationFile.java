// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public ApplicationFile delete() {
        log.log(LogLevel.DEBUG, "Delete " + file);
        if (file.isDirectory() && !listFiles().isEmpty()) {
            throw new RuntimeException("files. Can't delete, directory not empty: " + this  + "(" + listFiles() + ")." + listFiles().size());
        }
        if (file.isDirectory() && file.listFiles() != null && file.listFiles().length > 0) {
            for (File f : file.listFiles()) {
                deleteFile(f);
            }
        }
        if (!file.delete()) {
            throw new IllegalStateException("Unable to delete: "+this);
        }
        try {
            writeMetaFile("", ApplicationFile.ContentStatusDeleted);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }


    public static boolean deleteFile(File path) {
        if( path.exists() ) {
            if (path.isDirectory()) {
                File[] files = path.listFiles();
                for(int i=0; i<files.length; i++) {
                    if(files[i].isDirectory()) {
                        deleteFile(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
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
            writeMetaFile("", ApplicationFile.ContentStatusNew);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public ApplicationFile writeFile(Reader input) {
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try {
            String data = com.yahoo.io.IOUtils.readAll(input);
            String status = file.exists() ? ApplicationFile.ContentStatusChanged : ApplicationFile.ContentStatusNew;
            IOUtils.writeFile(file, data, false);
            writeMetaFile(data, status);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public List<ApplicationFile> listFiles(final PathFilter filter) {
        List<ApplicationFile> files = new ArrayList<>();
        if (!file.isDirectory()) {
            return files;
        }
        FileFilter fileFilter = pathname -> filter.accept(path.append(pathname.getName()));
        for (File child : file.listFiles(fileFilter)) {
            // Ignore dot-files.
            if (!child.getName().startsWith(".")) {
                files.add(new FilesApplicationFile(path.append(child.getName()), child));
            }
        }
        return files;
    }

    private void writeMetaFile(String data, String status) throws IOException {
        File metaDir = createMetaDir();
        log.log(LogLevel.DEBUG, "meta dir=" + metaDir);
        File metaFile = new File(metaDir + "/" + getPath().getName());
        if (status == null) {
            status = ApplicationFile.ContentStatusNew;
            if (metaFile.exists()) {
                status = ApplicationFile.ContentStatusChanged;
            }
        }
        String hash;
        if (file.isDirectory() || status.equals(ApplicationFile.ContentStatusDeleted)) {
            hash = "";
        } else {
            hash = ConfigUtils.getMd5(data);
        }
        mapper.writeValue(metaFile, new MetaData(status, hash));
    }

    private File createMetaDir() {
        File metaDir = getMetaDir();
        if (!metaDir.exists()) {
            log.log(LogLevel.DEBUG, "Creating meta dir " + metaDir);
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
        log.log(LogLevel.DEBUG, "Getting metadata for " + metaFile);
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
                return new MetaData(ApplicationFile.ContentStatusNew, "");
            } else {
                return new MetaData(ApplicationFile.ContentStatusNew, ConfigUtils.getMd5(IOUtils.readAll(createReader())));
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int compareTo(ApplicationFile other) {
        if (other == this) return 0;
        return this.getPath().getName().compareTo((other).getPath().getName());
    }

}
