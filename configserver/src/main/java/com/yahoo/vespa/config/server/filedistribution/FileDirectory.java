// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.config.model.api.FileDistribution;
import com.yahoo.io.IOUtils;
import com.yahoo.text.Utf8;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class FileDirectory  {
    private static final Logger log = Logger.getLogger(FileDirectory.class.getName());
    private final File root;

    public FileDirectory() {
        this(FileDistribution.getDefaultFileDBPath());
    }

    public FileDirectory(File rootDir) {
        root = rootDir;
        try {
            ensureRootExist();
        } catch (IllegalArgumentException e) {
            log.warning("Failed creating directory in constructor, will retry on demand : " + e.toString());
        }
    }

    private void ensureRootExist() {
        if (! root.exists()) {
            if ( ! root.mkdir()) {
                throw new IllegalArgumentException("Failed creating root dir '" + root.getAbsolutePath() + "'.");
            }
        } else if (!root.isDirectory()) {
            throw new IllegalArgumentException("'" + root.getAbsolutePath() + "' is not a directory");
        }
    }

    static private class Filter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return !".".equals(name) && !"..".equals(name) ;
        }
    }

    String getPath(FileReference ref) {
        return root.getAbsolutePath() + "/" + ref.value();
    }

    File getFile(FileReference reference) {
        ensureRootExist();
        File dir = new File(getPath(reference));
        if (!dir.exists()) {
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + "' does not exist.");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + "' is not a directory.");
        }
        File [] files = dir.listFiles(new Filter());
        if (files.length != 1) {
            StringBuilder msg = new StringBuilder();
            for (File f: files) {
                msg.append(f.getName()).append("\n");
            }
            throw new IllegalArgumentException("File reference '" + reference.toString() + "' with absolute path '" + dir.getAbsolutePath() + " does not contain exactly one file, but [" + msg.toString() + "]");
        }
        return files[0];
    }

    private Long computeReference(File file) throws IOException {
        byte [] wholeFile = IOUtils.readFileBytes(file);
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        return hasher.hash(ByteBuffer.wrap(wholeFile), hasher.hash(ByteBuffer.wrap(Utf8.toBytes(file.getName())), 0));
    }

    public FileReference addFile(File source) {
        try {
            Long hash = computeReference(source);
            FileReference reference = new FileReference(Long.toHexString(hash));
            return addFile(source, reference);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    public FileReference addFile(File source, FileReference reference) {
        ensureRootExist();
        try {
            File destinationDir = new File(root, reference.value());
            if (!destinationDir.exists()) {
                destinationDir.mkdir();
                File tempDestinationDir = File.createTempFile("writing", null, root);
                tempDestinationDir.mkdir();
                File  destination = new File(tempDestinationDir, source.getName());
                IOUtils.copy(source, destination);
                if (!destinationDir.exists()) {
                    if ( ! tempDestinationDir.renameTo(destinationDir)) {
                        log.warning("Failed moving '" + tempDestinationDir.getAbsolutePath() + "' to '" + destination.getAbsolutePath() + "'.");
                    }
                }
                IOUtils.recursiveDeleteDir(tempDestinationDir);
            }
            return reference;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
