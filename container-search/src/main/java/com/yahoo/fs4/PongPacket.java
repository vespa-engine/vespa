// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * A pong packet for FS4. It maps to PCODE_MONITORRESULTX
 * in the C++ implementation of the protocol.
 *
 * @author Steinar Knutsen
 */
public class PongPacket extends BasicPacket {

    private int dispatchTimestamp;

    @SuppressWarnings("unused")
    private int totalNodes; // configured nodes
    private Optional<Integer> activeNodes = Optional.empty(); // number of nodes that are up
    @SuppressWarnings("unused")
    private int totalPartitions; // configured partitions
    private Optional<Integer> activePartitions = Optional.empty(); // number of partitions that are up

    private Optional<Long> activeDocuments = Optional.empty(); // how many documents are searchable (sum)

    public PongPacket() {
    }

    /** For testing */
    public PongPacket(long activeDocuments) {
        this.activeDocuments = Optional.of(activeDocuments);
    }

    private int code;
    protected void codeDecodedHook(int code) { this.code = code; }
    public int getCode() { return code; }

    public void decodeBody(ByteBuffer buffer) {
        int features = buffer.getInt();
        buffer.getInt();  // Unused lowPartitionId
        dispatchTimestamp = buffer.getInt();
        if ((features & MRF_MLD) != 0) {
            totalNodes = buffer.getInt();
            activeNodes = Optional.of(buffer.getInt());
            totalPartitions = buffer.getInt();
            activePartitions = Optional.of(buffer.getInt());
        }
        if ((features & MRF_RFLAGS) != 0) {
            buffer.getInt(); // ignore rflags (historical field)
        }
        if ((features & MRF_ACTIVEDOCS) != 0) {
            activeDocuments = Optional.of(Long.valueOf(buffer.getLong()));
        }
    }

    public static PongPacket create() {
        return new PongPacket();
    }

    /**
     * Return current docstamp for backend to make cache invalidation
     * possible.
     * */
    public int getDocstamp() {
        return dispatchTimestamp;
    }

    /**
     * retrieve the reported number of active (searchable) documents
     * in the monitored backend.
     **/
    public Optional<Long> getActiveDocuments() {
        return activeDocuments;
    }

    public Optional<Integer> getActiveNodes() {
        return activeNodes;
    }

    public Optional<Integer> getActivePartitions() {
        return activePartitions;
    }

    /** feature bits, taken from searchlib/common/transport.h */
    static final int MRF_MLD        = 0x00000001;
    static final int MRF_RFLAGS     = 0x00000008;
    static final int MRF_ACTIVEDOCS = 0x00000010;

    /** packet codes, taken from searchlib/common/transport.h */
    static final int PCODE_MONITORRESULTX    = 221;

}
