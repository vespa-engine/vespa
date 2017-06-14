// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

/**
 * A ping packet for FS4. This packet has no data. It maps to
 * PCODE_MONITORQUERY the C++ implementation of the protocol.
 *
 * @author Steinar Knutsen
 */
public class PingPacket extends BasicPacket {

    public int getCode() { return 220; }

    public void encodeBody(ByteBuffer buffer) {
        buffer.putInt(MQF_QFLAGS);
        buffer.putInt(MQFLAG_REPORT_ACTIVEDOCS);
    }

    /** feature bits, taken from searchlib/common/transport.h */
    static final int MQF_QFLAGS = 0x00000002;

    /** flag bits, taken from searchlib/common/transport.h */
    static final int MQFLAG_REPORT_ACTIVEDOCS = 0x00000020;
}
