// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import com.yahoo.document.GlobalId;

import java.nio.ByteBuffer;

/**
 * An "extended query result" packet. This is the query result
 * packets used today, they allow more flexible sets of parameters
 * to be shipped with query results. This packet can be decoded only.
 *
 * @author  bratseth
 */
public class DocsumPacket extends Packet {

    private GlobalId globalId = new GlobalId(new byte[GlobalId.LENGTH]);

    private byte[] data;

    private DocsumPacket() {
    }

    /**
     * Constructor used by streaming search
     */
    public DocsumPacket(byte[] buffer) {
        data = buffer.clone();
    }

    public static DocsumPacket create() {
        return new DocsumPacket();
    }

    public int getCode() { return 205; }

    /**
     * Fills this packet from a byte buffer positioned at the
     * first byte of the packet
     */
    public void decodeBody(ByteBuffer buffer) {
        byte[] rawGid = new byte[GlobalId.LENGTH];
        buffer.get(rawGid);
        globalId = new GlobalId(rawGid);
        data=new byte[getLength()-12-GlobalId.LENGTH];
        buffer.get(data);
    }

    public GlobalId getGlobalId() { return globalId; }

    public byte[] getData() { return data; }

    public String toString() {
        return "docsum packet [globalId: " + globalId.toString() +
               ", size: " + (data==null ? "(no data)" : data.length + " bytes") + " ]";
    }

}
