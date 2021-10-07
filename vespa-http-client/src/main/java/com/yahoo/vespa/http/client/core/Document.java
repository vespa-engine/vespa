// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A document operation
 *
 * @author Einar M R Rosenvinge
 */
final public class Document {

    private final String documentId;
    private final ByteBuffer data;
    private final Instant createTime;
    // This is initialized lazily to reduce work on calling thread (which is the thread calling the API)
    private String operationId = null;
    private final Object context;
    private Instant queueInsertTime;

    public Document(String documentId, byte[] data, Object context, Instant createTime) {
        this(documentId, null, ByteBuffer.wrap(data), context, createTime);
    }

    public Document(String documentId, String operationId, CharSequence data, Object context, Instant createTime) {
        this(documentId, operationId, encode(data, documentId), context, createTime);
    }

    private Document(String documentId, String operationId, ByteBuffer data, Object context, Instant createTime) {
        this.documentId = documentId;
        this.operationId = operationId;
        this.data = data;
        this.context = context;
        this.createTime = Objects.requireNonNull(createTime, "createTime cannot be null");
        this.queueInsertTime = createTime;
    }

    public void setQueueInsertTime(Instant queueInsertTime) {
        this.queueInsertTime = queueInsertTime;
    }

    public Instant getQueueInsertTime() { return queueInsertTime; }

    public CharSequence getDataAsString() {
        return StandardCharsets.UTF_8.decode(data.asReadOnlyBuffer());
    }

    public Object getContext() { return context; }

    public static class DocumentException extends IOException {
        private static final long serialVersionUID = 29832833292L;
        public DocumentException(String message)
        {
            super(message);
        }
    }

    public String getDocumentId() { return documentId; }

    public ByteBuffer getData() {
        return data.asReadOnlyBuffer();
    }

    public int size() {
        return data.remaining();
    }

    public Instant createTime() { return createTime; }

    public String getOperationId() {
        if (operationId == null) {
            operationId = new BigInteger(64, ThreadLocalRandom.current()).toString(32);
        }
        return operationId;
    }

    @Override
    public String toString() { return "document '" + documentId + "'"; }

    private static ByteBuffer encode(CharSequence data, String documentId) {
        try {
            return StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(data));
        } catch (CharacterCodingException e) {
            throw new RuntimeException("Error encoding document data into UTF8 " + documentId, e);
        }
    }

}
