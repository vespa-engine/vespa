// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.path.Path;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * An application file represents a file within an application package. This class can be used to traverse the entire
 * application package file structure, as well as read and write files to it, and create directories.
 *
 * @author Ulf Lilleengen
 */
public abstract class ApplicationFile implements Comparable<ApplicationFile> {

    private static final String metaDir = ".meta";
    public static final String ContentStatusNew = "new";
    public static final String ContentStatusChanged = "changed";
    public static final String ContentStatusDeleted = "deleted";
    protected final Path path;
    private static final PathFilter defaultFilter = path1 -> true;

    protected ApplicationFile(Path path) {
        this.path = path;
    }

    /**
     * Check whether or not this file is a directory.
     *
     * @return true if it is, false if not.
     */
    public abstract boolean isDirectory();

    /**
     * Test whether or not this file exists.
     *
     * @return true if it exists, false if not.
     */
    public abstract boolean exists();

    /**
     * Create a {@link Reader} for the contents of this file.
     *
     * @return A {@link Reader} that should be closed after use.
     * @throws FileNotFoundException if the file is not found.
     */
    public abstract Reader createReader() throws FileNotFoundException;


    /**
     * Create an {@link InputStream} for the contents of this file.
     *
     * @return An {@link InputStream} that should be closed after use.
     * @throws FileNotFoundException if the file is not found.
     */
    public abstract InputStream createInputStream() throws FileNotFoundException;

    /**
     * Create a directory at the path represented by this file. Parent directories will
     * be automatically created.
     *
     * @return this
     * @throws IllegalArgumentException if the directory already exists.
     */
    public abstract ApplicationFile createDirectory();

    /**
     * Write the contents from this reader to this file. Any existing content will be overwritten!
     *
     * @param input A reader pointing to the content that should be written.
     * @return this
     */
    public abstract ApplicationFile writeFile(Reader input);

    /**
     * Appends the given string to this text file.
     *
     * @return this
     */
    public abstract ApplicationFile appendFile(String value);

    /**
     * List the files under this directory. If this is file, an empty list is returned.
     * Only immediate files/subdirectories are returned.
     *
     * @return a list of files in this directory.
     */
    public List<ApplicationFile> listFiles() {
        return listFiles(defaultFilter);
    }

    /**
     * List the files under this directory. If this is file, an empty list is returned.
     * Only immediate files/subdirectories are returned.
     *
     * @param filter A filter functor for filtering path names
     * @return a list of files in this directory.
     */
    public abstract List<ApplicationFile> listFiles(PathFilter filter);

    /**
     * List the files in this directory, optionally list files for subdirectories recursively as well.
     *
     * @param recurse Set to true if all files in the directory tree should be returned.
     * @return a list of files in this directory.
     */
    public List<ApplicationFile> listFiles(boolean recurse) {
        List<ApplicationFile> ret = new ArrayList<>();
        List<ApplicationFile> files = listFiles();
        ret.addAll(files);
        if (recurse) {
            for (ApplicationFile file : files) {
                if (file.isDirectory()) {
                    ret.addAll(file.listFiles(recurse));
                }
            }
        }
        return ret;
    }

    /**
     * Delete the file pointed to by this. If it is a non-empty directory, the operation will throw.
     *
     * @return this.
     * @throws RuntimeException if the file is a directory and not empty.
     */
    public abstract ApplicationFile delete();

    /**
     * Get the path that this file represents.
     *
     * @return a Path
     */
    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return path.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ApplicationFile) {
            return path.equals(((ApplicationFile) other).path);
        }
        return false;
    }

    protected Path getMetaPath() {
        if (path.toString().equals("")) {
            return Path.fromString(metaDir).append(".root");
        } else {
            return path.getParentPath().append(metaDir).append(path.getName());
        }
    }

    public abstract MetaData getMetaData();

    public static class MetaData {

        public String status = "unknown";
        public String md5 = "";

        public MetaData() {  }

        public MetaData(String status, String md5) {
            this.status = status;
            this.md5= md5;
        }

        public String getStatus() {
            return status;
        }

        public String getMd5() {
            return md5;
        }
    }

    public interface PathFilter {
        boolean accept(Path path);
    }

}
