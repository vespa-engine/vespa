// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.path.Path;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author baldersheim
 */
public interface AddFileInterface {
    FileReference addUri(String uri, Path path);
    FileReference addFile(Path path) throws IOException;
    FileReference addBlob(ByteBuffer blob, Path path);
}
