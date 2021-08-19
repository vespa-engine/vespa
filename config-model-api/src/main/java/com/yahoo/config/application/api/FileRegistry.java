// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import java.nio.ByteBuffer;
import java.util.List;

import com.yahoo.config.FileReference;
import com.yahoo.net.HostName;
import net.jpountz.xxhash.XXHashFactory;


/**
 * @author Tony Vaagenes
 */
public interface FileRegistry {

    FileReference addFile(String relativePath);
    FileReference addUri(String uri);
    FileReference addBlob(ByteBuffer blob);
    default FileReference addApplicationPackage() { return addFile(""); }

    /**
     * Returns the name of the host which is the source of the files
     * @deprecated Remove after 7.253
     */
    @Deprecated
    default String fileSourceHost() { return HostName.getLocalhost(); }

    List<Entry> export();

    class Entry {
        public final String relativePath;
        public final FileReference reference;

        public Entry(String relativePath, FileReference reference) {
            this.relativePath = relativePath;
            this.reference = reference;
        }
    }

    static String blobName(ByteBuffer blob) {
        long blobHash = XXHashFactory.fastestJavaInstance().hash64().hash(blob, 0);
        return Long.toHexString(blobHash);
    }

}
