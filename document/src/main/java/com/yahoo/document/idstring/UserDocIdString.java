// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import java.math.BigInteger;

/**
 * Representation of userdoc scheme in document IDs. A user id is any 64 bit
 * number. Note that internally, these are handled as unsigned values.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class UserDocIdString extends IdString {
    long userId;

    /**
     * Create a userdoc scheme object.
     * <code>userdoc:&lt;namespace&gt;:&lt;userid&gt;:&lt;namespaceSpecific&gt;</code>
     *
     * @param namespace The namespace of this document id.
     * @param userId 64 bit user id of this userdoc id.
     * @param namespaceSpecific The namespace specific part.
     */
    public UserDocIdString(String namespace, long userId, String namespaceSpecific) {
        super(Scheme.userdoc, namespace, namespaceSpecific);
        this.userId = userId;
    }

    @Override
    public boolean hasNumber() {
        return true;
    }

    @Override
    public long getNumber() {
        return userId;
    }

    /**
     * Get the location of this document id. The location is used for distribution
     * in clusters. For the userdoc scheme, the location equals the user id.
     *
     * @return The 64 bit location.
     */
    public long getLocation() { return userId; }

    /** Get the scheme specific part. Which for a userdoc, is the userid and a colon. */
    public String getSchemeSpecific() {
        BigInteger uid = BigInteger.ZERO;
        for (int i=0; i<64; i++) {
            if ((userId >>> i & 0x1) == 1) {
                uid = uid.setBit(i);
            }
        }
        return uid.toString() + ":";
    }

    /** @return Get the user id of this id. */
    public long getUserId() { return userId; }
}
