// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;

public interface AddFileInterface {
    FileReference addUri(String uri, String relativePath);
    FileReference addUri(String uri, String relativePath, FileReference reference);
    FileReference addFile(String relativePath);
    FileReference addFile(String relativePath, FileReference reference);
}
