// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

/**
 * An "extended query result" packet. This is the query result
 * packets used today, they allow more flexible sets of parameters
 * to be shipped with query results. This packet can be decoded only.
 *
 * @author  bratseth
 */
public class DocsumPacket {

    private byte[] data;

    /**
     * Constructor used by streaming search
     */
    public DocsumPacket(byte[] buffer) {
        data = buffer.clone();
    }


    public byte[] getData() { return data; }

    public String toString() {
        return "docsum packet size: " + (data==null ? "(no data)" : data.length + " bytes") + " ]";
    }

}
