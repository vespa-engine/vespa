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
public class OrderDocIdString extends IdString {
    String group;
    int widthBits;
    int divisionBits;
    long ordering;
    long location;

    /**
     * Create a groupdoc scheme object.
     * <code>groupdoc:&lt;namespace&gt;:&lt;group&gt;:&lt;namespaceSpecific&gt;</code>
     *
     * @param namespace The namespace of this document id.
     * @param group The groupname of this groupdoc id.
     * @param widthBits The number of bits used for the width of the data set
     * @param divisionBits The number of bits used for the smalles partitioning of the data set
     * @param ordering A value used to order documents of this type.
     * @param namespaceSpecific The namespace specific part.
     */
    public OrderDocIdString(String namespace, String group, int widthBits, int divisionBits, long ordering, String namespaceSpecific) {
        super(Scheme.orderdoc, namespace, namespaceSpecific);
        this.group = group;
        this.widthBits = widthBits;
        this.divisionBits = divisionBits;
        this.ordering = ordering;

        try {
            this.location = Long.parseLong(group);
        } catch (Exception foo) {
            location = 0;
            byte[] md5sum = MD5.md5.get().digest(Utf8.toBytes(group));
            for (int i=0; i<8; ++i) {
                location |= (md5sum[i] & 0xFFl) << (8*i);
            }
        }
    }

    /**
     * Get the location of this document id. The location is used for distribution
     * in clusters. For the orderdoc scheme, the location is a hash of the groupname or just the number specified.
     *
     * @return The 64 bit location.
     */
    public long getLocation() {
        return location;
    }

    public String getSchemeParameters() {
        return "(" + widthBits + "," + divisionBits + ")";
    }

    /** Get the scheme specific part. */
    public String getSchemeSpecific() {
        return group + ":" + ordering + ":";
    }

    public GidModifier getGidModifier() {
        GidModifier gm = new GidModifier();
        gm.usedBits = widthBits - divisionBits;
        long gidBits = (ordering << (64 - widthBits));
        gidBits = Long.reverse(gidBits);
        long gidMask = (0xFFFFFFFFFFFFFFFFl >>> (64 - gm.usedBits));
        gidBits &= gidMask;
        gm.value = gidBits;
        return gm;
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

    @Override
    public boolean hasNumber() {
        return true;
    }

    @Override
    public long getNumber() {
        return location;
    }

    public long getUserId() {
        return location;
    }

    public int getWidthBits() {
        return widthBits;
    }

    public int getDivisionBits() {
        return divisionBits;
    }

    public long getOrdering() {
        return ordering;
    }
}
