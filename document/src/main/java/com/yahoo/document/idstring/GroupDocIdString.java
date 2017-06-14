// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.idstring;

import com.yahoo.collections.MD5;
import com.yahoo.text.Utf8;

import java.security.MessageDigest;

/**
 * Representation of groupdoc scheme in document IDs.
 *
 * @author <a href="mailto:humbe@yahoo-inc.com">H&aring;kon Humberset</a>
 */
public class GroupDocIdString extends IdString {
    String group;

    /**
     * Create a groupdoc scheme object.
     * <code>groupdoc:&lt;namespace&gt;:&lt;group&gt;:&lt;namespaceSpecific&gt;</code>
     *
     * @param namespace The namespace of this document id.
     * @param group The groupname of this groupdoc id.
     * @param namespaceSpecific The namespace specific part.
     */
    public GroupDocIdString(String namespace, String group, String namespaceSpecific) {
        super(Scheme.groupdoc, namespace, namespaceSpecific);
        this.group = group;
    }

    /**
     * Get the location of this document id. The location is used for distribution
     * in clusters. For the groupdoc scheme, the location is a hash of the groupname.
     *
     * @return The 64 bit location.
     */
    public long getLocation() {
        long result = 0;
        try{
            byte[] md5sum = MD5.md5.get().digest(Utf8.toBytes(group));
            for (int i=0; i<8; ++i) {
                result |= (md5sum[i] & 0xFFl) << (8*i);
            }
        } catch (Exception e) {
            e.printStackTrace(); // TODO: FIXME!
        }
        return result;
    }

    /** Get the scheme specific part. Which is for a groupdoc, is the groupdoc and a colon. */
    public String getSchemeSpecific() {
        return group + ":";
    }

    @Override
    public boolean hasGroup() {
        return true;
    }

    /** @return Get the groupname of this id. */
    @Override
    public String getGroup() {
        return group;
    }
}
