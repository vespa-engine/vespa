// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.test.file;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Feature;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.PathType;

import java.nio.file.FileSystem;

public class TestFileSystem {
    public static FileSystem create() {
        // This configuration is based on Configuration.unix(), except:
        //  - Use "posix" attribute view which is necessary for permissions, owner, and group.
        Configuration configuration = Configuration.builder(PathType.unix())
                .setRoots("/")
                .setWorkingDirectory("/work")
                .setAttributeViews("posix")
                .setSupportedFeatures(Feature.LINKS, Feature.SYMBOLIC_LINKS, Feature.SECURE_DIRECTORY_STREAM, Feature.FILE_CHANNEL)
                .build();
        return Jimfs.newFileSystem(configuration);
    }

    private TestFileSystem() { }
}
