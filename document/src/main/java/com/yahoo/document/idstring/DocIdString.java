// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import com.yahoo.collections.MD5;
import com.yahoo.text.Utf8;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Representation of doc scheme in document IDs.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocIdString extends IdString {
    /**
     * Create a doc scheme object.
     * <code>doc:&lt;namespace&gt;:&lt;namespaceSpecific&gt;</code>
     *
     * @param namespace The namespace of this document id.
     * @param namespaceSpecific The namespace specific part.
     */
    public DocIdString(String namespace, String namespaceSpecific) {
        super(Scheme.doc, namespace, namespaceSpecific);
    }

    /**
     * Get the location of this document id. The location is used for distribution
     * in clusters. For the doc scheme, the location is a hash of the whole id.
     *
     * @return The 64 bit location.
     */
    public long getLocation() {
        long result = 0;
        byte[] md5sum = MD5.md5.get().digest(Utf8.toBytes(toString()));
        for (int i=0; i<8; ++i) {
            result |= (md5sum[i] & 0xFFl) << (8*i);
        }

        return result;
    }

    /** Get the scheme specific part. Which is non-existing for doc scheme. */
    public String getSchemeSpecific() {
        return "";
    }
}
