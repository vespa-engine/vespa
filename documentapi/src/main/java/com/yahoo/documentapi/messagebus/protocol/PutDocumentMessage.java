// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.api.annotations.Beta;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.serialization.DocumentDeserializer;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Simon Thoresen Hult
 */
public class PutDocumentMessage extends TestAndSetMessage {

    private DocumentPut put = null;
    private long time = 0;
    private long persistedTimestamp = 0;
    // TODO Vespa 9: remove. Inherently tied to legacy protocol version.
    private DocumentDeserializer buffer = null;
    private LazyDecoder decoder = null;

    /**
     * Constructs a new message from a byte buffer.
     *
     * @param decoder The decoder to use for deserialization.
     * @param buffer  A byte buffer that contains a serialized message.
     */
    public PutDocumentMessage(LazyDecoder decoder, DocumentDeserializer buffer) {
        this.decoder = decoder;
        this.buffer = buffer;
    }

    /** Constructs a new document put message */
    public PutDocumentMessage(DocumentPut put) {
        this.put = put;
    }

    /**
     * Creates an empty PutDocumentMessage
     */
    public static PutDocumentMessage createEmpty() {
        return new PutDocumentMessage(null);
    }

    /**
     * This method will make sure that any serialized content is deserialized into proper message content on first
     * entry. Any subsequent entry into this function will do nothing.
     */
    private void deserialize() {
        if (decoder != null && buffer != null) {
            decoder.decode(this, buffer);
            decoder = null;
            buffer = null;
        }
    }

    /** Returns the document put operation */
    public DocumentPut getDocumentPut() {
        deserialize();
        return put;
    }

    /** Sets the document to put */
    public void setDocumentPut(DocumentPut put) {
        buffer = null;
        decoder = null;
        this.put = put;
    }

    /**
     * <p>Returns the timestamp of the document to put.</p>
     *
     * <p>Only used by the feed pipeline; use {@link #getPersistedTimestamp()} to get the
     * persisted backend timestamp of the document as observed by the client of a running
     * visitor operation.</p>
     */
    public long getTimestamp() {
        deserialize();
        return time;
    }

    /**
     * <p>Sets the timestamp of the document to put.</p>
     *
     * <p>Timestamp assignment should normally always be left to the content cluster, i.e.
     * this method should <em>not</em> be called.</p>
     *
     * <p>Only use this if you have a very specific use case and are aware of the downsides
     * of side-stepping the internal timestamp mechanisms.</p>
     */
    public void setTimestamp(long time) {
        buffer = null;
        decoder = null;
        this.time = time;
    }

    /**
     * <p>Set the timestamp of the last known mutating operation to this document.</p>
     *
     * <p>This is normally only invoked by the backends as part of visiting.</p>
     */
    @Beta
    public void setPersistedTimestamp(long time) {
        this.persistedTimestamp = time;
    }

    /**
     * <p>When a visitor client receives a Put as part of the visiting operation, this
     * timestamp represents the wall clock time in microseconds(*) of the last known mutating
     * operation to the document.</p>
     *
     * <p>If zero, the sending content node is too old to support this feature.</p>
     *
     * <p>This value is not guaranteed to be linearizable. During e.g. network partitions this
     * value might not represent the latest acknowledged operation for the document.</p>
     *
     * <p>Unsupported (and ignored) for Puts sent by the client during feeding.</p>
     *
     * <p>(*) (wall clock seconds since UTC epoch * 1M) + synthetic intra-second microsecond counter.</p>
     */
    @Beta
    public long getPersistedTimestamp() {
        return this.persistedTimestamp;
    }

    /**
     * Returns the raw serialized buffer. This buffer is stored as the message is received from accross the network, and
     * deserialized from as soon as a member is requested. This method will return null if the buffer has been decoded.
     *
     * @return the buffer containing the serialized data for this message, or null
     */
    ByteBuffer getSerializedBuffer() {
        return buffer != null ? buffer.getBuf().getByteBuffer() : null; // TODO: very dirty. Must make interface.
    }

    @Override
    public DocumentReply createReply() {
        return new WriteDocumentReply(DocumentProtocol.REPLY_PUTDOCUMENT);
    }

    @Override
    public int getApproxSize() {
        if (buffer != null) {
            return buffer.getBuf().remaining();
        }
        return put.getDocument().getApproxSize();
    }

    @Override
    public boolean hasSequenceId() {
        return true;
    }

    @Override
    public long getSequenceId() {
        deserialize();
        return Arrays.hashCode(put.getId().getGlobalId());
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_PUTDOCUMENT;
    }

    @Override
    public TestAndSetCondition getCondition() {
        deserialize();
        return put.getCondition();
    }

    @Override
    public void setCondition(TestAndSetCondition condition) {
        put.setCondition(condition);
    }

    @Beta
    public void setCreateIfNonExistent(boolean value) {
        put.setCreateIfNonExistent(value);
    }

    @Beta
    public boolean getCreateIfNonExistent() {
        deserialize();
        return put.getCreateIfNonExistent();
    }
}
