// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.document.GlobalId;


/**
 * Key for each entry in the packet cache.
 *
 * @author Mathias Mølster Lidal
 */
public class DocsumPacketKey {

    private GlobalId globalId;
    private int partid;
    private String summaryClass;

    private static boolean strEquals(String a, String b) {
        if (a == null || b == null) {
            return (a == null && b == null);
        }
        return a.equals(b);
    }

    private static int strHashCode(String s) {
        if (s == null) {
            return 0;
        }
        return s.hashCode();
    }

    public DocsumPacketKey(GlobalId globalId, int partid, String summaryClass) {
        this.globalId = globalId;
        this.partid = partid;
        this.summaryClass = summaryClass;
    }

    public GlobalId getGlobalId() {
        return globalId;
    }

    public int getPartid() {
        return partid;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DocsumPacketKey) {
            DocsumPacketKey other = (DocsumPacketKey) o;

            if (globalId.equals(other.getGlobalId())
                    && partid == other.getPartid()
                    && strEquals(summaryClass, other.summaryClass))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return globalId.hashCode() + 10 * partid + strHashCode(summaryClass);
    }

}
