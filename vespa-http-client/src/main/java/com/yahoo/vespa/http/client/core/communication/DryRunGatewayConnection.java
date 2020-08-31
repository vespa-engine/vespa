// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;

import java.io.ByteArrayInputStream;
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
    private List<Document> held = new ArrayList<>();

    public DryRunGatewayConnection(Endpoint endpoint, Clock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
    }

    @Override
    public InputStream write(List<Document> docs) {
        StringBuilder result = new StringBuilder();
        if (hold) {
            held.addAll(docs);
        }
        else {
            for (Document doc : held)
                result.append(okResponse(doc).render());
            held.clear();
            for (Document doc : docs)
                result.append(okResponse(doc).render());
        }
        return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void hold(boolean hold) {
        this.hold = hold;
    }

    @Override
    public InputStream poll() {
        lastPollTime = clock.instant();
        return write(new ArrayList<>());
    }

    @Override
    public Instant lastPollTime() { return lastPollTime; }

    @Override
    public InputStream drain() {
        return write(new ArrayList<>());
    }

    @Override
    public boolean connect() {
        connectionTime = clock.instant();
        return true;
    }

    @Override
    public Instant connectionTime() { return connectionTime; }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public void handshake() { }

    @Override
    public void close() { }

    /** Returns the document currently held in this */
    public List<Document> held() { return Collections.unmodifiableList(held); }

    private OperationStatus okResponse(Document document) {
        return new OperationStatus("ok", document.getOperationId(), ErrorCode.OK, false, "");
    }

}
