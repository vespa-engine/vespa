// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespa.http.client.core.ServerResponseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dummy implementation.
 *
 * @author dybis
 */
public class DryRunGatewayConnection implements GatewayConnection {

    private final Endpoint endpoint;
    private final Clock clock;
    private Instant connectionTime = null;
    private Instant lastPollTime = null;

    /** Set to true to hold off responding with a result to any incoming operations until this is set false */
    private boolean hold = false;
    private final List<Document> held = new ArrayList<>();

    /** If this is set, handshake operations will throw this exception */
    private ServerResponseException throwThisOnHandshake = null;

    /** If this is set, all write operations will throw this exception */
    private IOException throwThisOnWrite = null;

    public DryRunGatewayConnection(Endpoint endpoint, Clock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
    }

    @Override
    public synchronized InputStream write(List<Document> docs) throws IOException {
        if (throwThisOnWrite != null)
            throw throwThisOnWrite;

        if (hold) {
            held.addAll(docs);
            return new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        }
        else {
            StringBuilder result = new StringBuilder();
            for (Document doc : held)
                result.append(okResponse(doc).render());
            held.clear();
            for (Document doc : docs)
                result.append(okResponse(doc).render());
            return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public synchronized InputStream poll() throws IOException {
        lastPollTime = clock.instant();
        return write(new ArrayList<>());
    }

    @Override
    public synchronized Instant lastPollTime() { return lastPollTime; }

    @Override
    public synchronized InputStream drain() throws IOException {
        return write(new ArrayList<>());
    }

    @Override
    public synchronized boolean connect() {
        connectionTime = clock.instant();
        return true;
    }

    @Override
    public synchronized Instant connectionTime() { return connectionTime; }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public synchronized void handshake() throws ServerResponseException {
        if (throwThisOnHandshake != null)
            throw throwThisOnHandshake;
    }

    @Override
    public synchronized void close() { }

    public synchronized void hold(boolean hold) {
        this.hold = hold;
    }

    /** Returns the document currently held in this */
    public synchronized List<Document> held() { return Collections.unmodifiableList(held); }

    public synchronized void throwOnWrite(IOException throwThisOnWrite) {
        this.throwThisOnWrite = throwThisOnWrite;
    }

    public synchronized void throwOnHandshake(ServerResponseException throwThisOnHandshake) {
        this.throwThisOnHandshake = throwThisOnHandshake;
    }

    private OperationStatus okResponse(Document document) {
        return new OperationStatus("ok", document.getOperationId(), ErrorCode.OK, false, "");
    }

}
