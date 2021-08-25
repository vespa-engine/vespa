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
    /**
     * @deprecated Remove after 7.455
     */
    @Deprecated
    default FileReference addBlob(ByteBuffer blob) { return null; }
    FileReference addBlob(String name, ByteBuffer blob);
    default FileReference addApplicationPackage() { return addFile(""); }

    /**
     * Returns the name of the host which is the source of the files
     * @deprecated Remove after 7.453
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

    /**
     * @deprecated Remove after 7.455
     */
    @Deprecated
    static String blobName(ByteBuffer blob) {
        blob.mark();
        long blobHash = XXHashFactory.fastestJavaInstance().hash64().hash(blob, 0);
        blob.reset();
        return Long.toHexString(blobHash);
    }

}
