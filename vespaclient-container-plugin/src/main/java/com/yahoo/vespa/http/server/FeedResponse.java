// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;

/**
 * Reads feed responses from a queue and renders them continuously to the
 * feeder.
 *
 * @author Steinar Knutsen
 */
public class FeedResponse extends HttpResponse {

    BlockingQueue<OperationStatus> operations;

    public FeedResponse(
            int status,
            BlockingQueue<OperationStatus> operations,
            int protocolVersion,
            String sessionId) {
        super(status);
        this.operations = operations;
        headers().add(Headers.SESSION_ID, sessionId);
        headers().add(Headers.VERSION, Integer.toString(protocolVersion));
    }

    // This is used by the V3 protocol.
    public FeedResponse(
            int status,
            BlockingQueue<OperationStatus> operations,
            int protocolVersion,
            String sessionId,
            int outstandingClientOperations,
            String hostName) {
        super(status);
        this.operations = operations;
        headers().add(Headers.SESSION_ID, sessionId);
        headers().add(Headers.VERSION, Integer.toString(protocolVersion));
        headers().add(Headers.OUTSTANDING_REQUESTS, Integer.toString(outstandingClientOperations));
        headers().add(Headers.HOSTNAME, hostName);
    }

    @Override
    public void render(OutputStream output) throws IOException {
        int i = 0;
        OperationStatus status;
        try {
            status = operations.take();
            while (status.errorCode != ErrorCode.END_OF_FEED) {
                output.write(toBytes(status.render()));
                if (++i % 5 == 0) {
                    output.flush();
                }
                status = operations.take();
            }
        } catch (InterruptedException e) {
            output.flush();
        }
    }

    private byte[] toBytes(String s) {
        byte[] b = new byte[s.length()];
        for (int i = 0; i < b.length; ++i) {
            b[i] = (byte) s.charAt(i); // renderSingleStatus ensures ASCII only
        }
        return b;
    }

    @Override
    public String getContentType() {
        return "text/plain";
    }

    @Override
    public String getCharacterEncoding() {
        return StandardCharsets.US_ASCII.name();
    }

}
