// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.path.Path;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * @author hmusum
 */
public class MockFileManager implements AddFileInterface {

    @Override
    public FileReference addUri(String uri, Path path) {
        return null;
    }

    @Override
    public FileReference addFile(Path path) {
        return null;
    }

    @Override
    public FileReference addBlob(ByteBuffer blob, Path path) {
        return null;
    }

}
