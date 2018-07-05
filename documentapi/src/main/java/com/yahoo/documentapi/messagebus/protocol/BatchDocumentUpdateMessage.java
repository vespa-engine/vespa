// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.idstring.GroupDocIdString;
import com.yahoo.document.idstring.IdString;
import com.yahoo.document.idstring.UserDocIdString;
import com.yahoo.document.serialization.DocumentDeserializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class BatchDocumentUpdateMessage extends DocumentMessage {

    private DocumentDeserializer buffer = null;
    private List<DocumentUpdate> updates = new ArrayList<DocumentUpdate>();
    private LazyDecoder decoder = null;
    private String group = null;
    private Long userId = null;
    private BucketId bucketId = null;

    public String getGroup() {
        return group;
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * Constructs a new message for deserialization.
     */
    BatchDocumentUpdateMessage() {
        // empty
    }

    /**
     * Constructs a new message from a byte buffer.
     *  @param decoder The decoder to use for deserialization.
     * @param buffer  A byte buffer that contains a serialized message.
     */
    public BatchDocumentUpdateMessage(long userId, LazyDecoder decoder, DocumentDeserializer buffer) {
        this.userId = userId;
        this.decoder = decoder;
        this.buffer = buffer;
        setBucketId(new UserDocIdString("foo", userId, "bar"));
    }

    /**
     * Constructs a new message from a byte buffer.
     *  @param decoder The decoder to use for deserialization.
     * @param buffer  A byte buffer that contains a serialized message.
     */
    public BatchDocumentUpdateMessage(String group, LazyDecoder decoder, DocumentDeserializer buffer) {
        this.group = group;
        this.decoder = decoder;
        this.buffer = buffer;
        setBucketId(new GroupDocIdString("foo", group, "bar"));
    }

    /**
     * Constructs a batch document update message, to contain updates for documents for the given user.
     */
    public BatchDocumentUpdateMessage(long userId) {
        super();
        this.userId = userId;
        setBucketId(new UserDocIdString("foo", userId, "bar"));
    }

    /**
     * Constructs a batch document update message, to contain updates for documents for the given user.
     */
    public BatchDocumentUpdateMessage(String group) {
        super();
        this.group = group;
        setBucketId(new GroupDocIdString("foo", group, "bar"));
    }

    void setBucketId(IdString id) {
        BucketIdFactory factory = new BucketIdFactory();
        bucketId = factory.getBucketId(new DocumentId(id));
    }

    BucketId getBucketId() {
        return bucketId;
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

    /**
     * Returns the list of document updates to perform.
     *
     * @return The updates.
     */
    public List<DocumentUpdate> getUpdates() {
        deserialize();
        return updates;
    }

    /**
     * Adds a document update to perform.
     *
     * @param upd The document update to set.
     */
    public void addUpdate(DocumentUpdate upd) {
        buffer = null;
        decoder = null;

        verifyUpdate(upd);
        updates.add(upd);
    }

    /**
     * Returns the raw serialized buffer. This buffer is stored as the message is received from accross the network, and
     * deserialized from as soon as a member is requested. This method will return null if the buffer has been decoded.
     *
     * @return The buffer containing the serialized data for this message, or null.
     */
    ByteBuffer getSerializedBuffer() {
        return buffer != null ? buffer.getBuf().getByteBuffer() : null;
    }

    @Override
    public DocumentReply createReply() {
        return new BatchDocumentUpdateReply();
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_BATCHDOCUMENTUPDATE;
    }

    void verifyUpdate(DocumentUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("Document update can not be null.");
        }

        IdString idString = update.getId().getScheme();

        if (group != null) {
            String idGroup;

            if (idString.hasGroup()) {
                idGroup = idString.getGroup();
            } else {
                throw new IllegalArgumentException("Batch update message can only contain groupdoc or orderdoc items");
            }

            if (!group.equals(idGroup)) {
                throw new IllegalArgumentException("Batch update message can not contain messages from group " + idGroup + " only group " + group);
            }
        } else {
            long foundUserId = 0;

            if (idString.hasNumber()) {
                foundUserId = idString.getNumber();
            } else {
                throw new IllegalArgumentException("Batch update message can only contain userdoc or orderdoc items");
            }

            if (userId != foundUserId) {
                throw new IllegalArgumentException("Batch update message can not contain messages from user " + foundUserId + " only user " + userId);
            }
        }
    }

}
