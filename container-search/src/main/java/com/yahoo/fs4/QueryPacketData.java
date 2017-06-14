// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import java.nio.ByteBuffer;

/**
 * Class for storing data which has to be constant between query and summary
 * fetch for a Vespa hit. Used to avoid to tagging Vespa summary hits with
 * the entire query as an immutable.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class QueryPacketData {

    private byte[] rankProfile = null;
    private int queryFlags = 0;
    private byte[] queryStack = null;
    private byte[] location = null;
    private byte[] propertyMaps = null;

    /**
     * Given src.position() bigger than startOfField, allocate a fresh byte
     * array, and copy the data from startOfField to src.position() into it.
     *
     * @param src
     *            the ByteBuffer to copy from
     * @param startOfField
     *            the position of the buffer at which the field starts
     * @return a copy of the data between startOfField and the buffer position
     *         before invokation
     * @throws IllegalArgumentException
     *             if startOfField is somewhere after src.position()
     */
    private byte[] copyField(final ByteBuffer src, final int startOfField) {
        if (startOfField > src.position()) {
            throw new IllegalArgumentException("startOfField after src.position()");
        }
        final byte[] dst = new byte[src.position() - startOfField];

        src.position(startOfField);
        src.get(dst);
        return dst;
    }

    ByteBuffer encodeRankProfile(final ByteBuffer buffer) {
        return buffer.put(rankProfile);
    }

    void setRankProfile(final ByteBuffer src, final int startOfField) {
        rankProfile = copyField(src, startOfField);
    }

    ByteBuffer encodeQueryFlags(final ByteBuffer buffer) {
        return buffer.putInt(queryFlags);
    }

    void setQueryFlags(final int queryFlags) {
        this.queryFlags = queryFlags;
    }

    ByteBuffer encodeQueryStack(final ByteBuffer buffer) {
        return buffer.put(queryStack);
    }

    void setQueryStack(final ByteBuffer src, final int startOfField) {
        queryStack = copyField(src, startOfField);
    }

    ByteBuffer encodePropertyMaps(final ByteBuffer buffer) {
        if (propertyMaps != null) {
            buffer.put(propertyMaps);
        }
        return buffer;
    }

    void setPropertyMaps(final ByteBuffer src, final int startOfField) {
        propertyMaps = copyField(src, startOfField);
    }

    void setLocation(final ByteBuffer src, final int startOfField) {
        this.location = copyField(src, startOfField);
    }

    ByteBuffer encodeLocation(final ByteBuffer buffer) {
        if (location != null) {
            buffer.put(location);
        }
        return buffer;
    }

}
