// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.search.Query;

/**
 * Adds packets to the query context
 *
 * @author Tony Vaagenes
 */
public class PacketQueryTracer implements PacketListener {

    private final static int traceLevel = 10;

    private void addTrace(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        Query query = channel.getQuery();
        if (query != null && query.getTraceLevel() >= traceLevel) {
            StringBuilder traceString = new StringBuilder();
            traceString.append(packet.getClass().getSimpleName()).append(": ");
            hexDump(serializedForm, traceString);

            final boolean includeQuery = true;
            query.trace(traceString.toString(), includeQuery, traceLevel);
        }
    }

    private void hexDump(ByteBuffer serializedForm, StringBuilder traceString) {
        HexByteIterator hexByteIterator = new HexByteIterator(serializedForm);

        long count = 0;
        final int maxNumCharacters = 80;
        while (hexByteIterator.hasNext()) {
            if (++count % maxNumCharacters == 0)
                traceString.append('\n');
            traceString.append(hexByteIterator.next());
        }
    }

    @Override
    public void packetSent(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        addTrace(channel, packet, serializedForm);
    }

    @Override
    public void packetReceived(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        addTrace(channel, packet, serializedForm);
    }

}

