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

    public DryRunGatewayConnection(Endpoint endpoint, Clock clock) {
        this.endpoint = endpoint;
        this.clock = clock;
    }

    @Override
    public InputStream write(List<Document> docs) {
        StringBuilder result = new StringBuilder();
        for (Document doc : docs) {
            OperationStatus operationStatus = new OperationStatus("ok", doc.getOperationId(), ErrorCode.OK, false, "");
            result.append(operationStatus.render());
        }
        return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
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

}
