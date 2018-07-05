// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.log.LogLevel;
import com.yahoo.search.Query;

/**
 * Responsible for dumping query &amp; query result packets
 *
 * @author Tony Vaagenes
 */
public class PacketDumper implements PacketListener {
    /** High level representation of packet types (e.g. query, result, ...) */
    public static enum PacketType {
        query(QueryPacket.class),
        result(QueryResultPacket.class);

        Class<? extends BasicPacket> implementationType;

        PacketType(Class<? extends BasicPacket> implementationType) {
            this.implementationType = implementationType;
        }
    }

    private static Logger log = Logger.getLogger(PacketDumper.class.getSimpleName());

    private volatile boolean disabled = true;
    private final File logDirectory;
    private final Map<Class<? extends BasicPacket>, DataOutputStream> dumpFiles =
            new HashMap<>();
    private final String fileNamePattern;

    private void handlePacket(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm, String direction) {
        //minimize overhead when disabled:
        if (disabled)
            return;

        try {
            DataOutputStream stream = getOutputStream(packet);
            if (stream != null) {
                synchronized (stream) {
                    stream.writeChars(packet.getTimestamp() + " " + direction + " packet on channel " + channel.getChannelId());
                    String indent = "    ";
                    Query query = channel.getQuery();
                    if (query != null)
                        stream.writeChars('\n' + indent + "Query: '" + query.getModel().getQueryString());
                    hexDump(indent, stream, serializedForm);

                    stream.writeChar('\n');
                    stream.flush();
                }
            }
        } catch (IOException e) {
            log.log(LogLevel.WARNING, "Could not log packet.", e);
        }
    }

    private void hexDump(String indent, DataOutputStream stream, ByteBuffer serializedForm) throws IOException {
        HexByteIterator hexByteIterator = new HexByteIterator(serializedForm);

        long count = 0;
        final int maxNumCharacters = 80;
        while (hexByteIterator.hasNext()) {
            if (count++ % maxNumCharacters == 0)
                stream.writeChar('\n');
            stream.writeChars(hexByteIterator.next());
        }
    }

    private synchronized DataOutputStream getOutputStream(BasicPacket packet) {
        return dumpFiles.get(packet.getClass());
    }

    public void packetSent(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        handlePacket(channel, packet, serializedForm, "Sent");
    }

    public void packetReceived(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) {
        handlePacket(channel, packet, serializedForm, "Received");
    }

    public synchronized void dumpPackets(PacketType packetType, boolean on) throws IOException {
        OutputStream stream = dumpFiles.get(packetType.implementationType);
        if (!on && stream != null)
            closeFile(stream, packetType);
        else if (on && stream == null)
            openFile(packetType);
    }

    private void openFile(PacketType packetType) throws FileNotFoundException {
        if (!logDirectory.exists() ||
                logDirectory.mkdirs()) {

            throw new RuntimeException("PacketDumper: Could not create log directory " + logDirectory);
        }
        String fileName = fileNamePattern.replace("%s", packetType.toString());
        boolean append = true;
        DataOutputStream outputStream =
                new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(new File(logDirectory, fileName), append)));
        dumpFiles.put(packetType.implementationType, outputStream);

        disabled = dumpFiles.isEmpty();
    }

    private void closeFile(OutputStream stream, PacketType packetType) throws IOException {
        try {
            synchronized (stream) {
                stream.close();
            }
        } finally {
            dumpFiles.remove(packetType.implementationType);
            disabled = dumpFiles.isEmpty();
        }
    }

    public PacketDumper(File logDirectory, String fileNamePattern) {
        this.logDirectory = logDirectory;
        this.fileNamePattern = fileNamePattern;
    }
}
