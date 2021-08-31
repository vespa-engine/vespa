// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author baldersheim
 */
public interface AddFileInterface {
    FileReference addUri(String uri, String relativePath);
    FileReference addFile(String relativePath) throws IOException;
    FileReference addFile(File file) throws IOException;
    FileReference addBlob(ByteBuffer blob, String relativePath);
}
