// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * @author hmusum
 */
public class MockFileManager implements AddFileInterface {

    @Override
    public FileReference addUri(String uri, String relativePath) {
        return null;
    }

    @Override
    public FileReference addFile(String relativePath) {
        return null;
    }

    @Override
    public FileReference addFile(File file) {
        return null;
    }

    @Override
    public FileReference addBlob(ByteBuffer blob, String relativePath) {
        return null;
    }

}
