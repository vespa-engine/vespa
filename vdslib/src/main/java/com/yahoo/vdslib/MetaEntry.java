// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.io.GrowableByteBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MetaEntry {
    public static int REMOVE_ENTRY = 1;
    public static int BODY_STRIPPED = 2;
    public static int BODY_IN_HEADER = 4;
    public static int UPDATE_ENTRY = 8;
    public static int COMPRESSED = 16;

    public static int SIZE = 32;

    public long timestamp = 0;
    public int headerPos = 0;
    public int headerLen = 0;
    public int bodyPos = 0;
    public int bodyLen = 0;
    public byte flags = 0;

    public MetaEntry() {
    }

    public MetaEntry(byte[] buffer, int position) {
        ByteBuffer buf = ByteBuffer.wrap(buffer, position, SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        timestamp = buf.getLong();
        headerPos = buf.getInt();
        headerLen = buf.getInt();
        bodyPos = buf.getInt();
        bodyLen = buf.getInt();
        flags = buf.get();
    }

    public void serialize(GrowableByteBuffer buf) {
        ByteOrder originalOrder = buf.order();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(timestamp); // 8
        buf.putInt(headerPos); // 12
        buf.putInt(headerLen); // 16
        buf.putInt(bodyPos); // 20
        buf.putInt(bodyLen); // 24
        buf.putInt(flags); // 28 (written as little-endian int, this is on purpose)
        buf.putInt(0); // 32
        buf.order(originalOrder);
    }
}
