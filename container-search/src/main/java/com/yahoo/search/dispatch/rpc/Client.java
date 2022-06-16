// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.compress.CompressionType;
import com.yahoo.prelude.fastsearch.FastHit;

import java.util.List;
import java.util.Optional;

/**
 * A dispatch client.
 *
 * @author bratseth
 */
interface Client {

    /** Creates a connection to a particular node in this */
    NodeConnection createConnection(String hostname, int port);
    void close();

    interface ResponseReceiver {
        void receive(ResponseOrError<ProtobufResponse> response);
    }

    class ResponseOrError<T> {

        final Optional<T> response;
        final Optional<String> error;
        final boolean isTimeout;

        public static <T> ResponseOrError<T> fromResponse(T response) {
            return new ResponseOrError<>(response);
        }

        public static <T> ResponseOrError<T> fromError(String error) {
            return new ResponseOrError<T>(error, false);
        }

        public static <T> ResponseOrError<T> fromTimeoutError(String error) {
            return new ResponseOrError<T>(error, true);
        }

        ResponseOrError(T response) {
            this.response = Optional.of(response);
            this.error = Optional.empty();
            this.isTimeout = false;
        }

        ResponseOrError(String error, boolean isTimeout) {
            this.response = Optional.empty();
            this.error = Optional.of(error);
            this.isTimeout = isTimeout;
        }

        /** Returns the response, or empty if there is an error */
        public Optional<T> response() { return response; }

        /** Returns the error or empty if there is a response */
        public Optional<String> error() { return error; }

        /** @return true if error is a timeout */
        public boolean timeout() { return isTimeout; }
    }

    class GetDocsumsResponse {
        private final byte compression;
        private final int uncompressedSize;
        private final byte[] compressedSlimeBytes;
        private final List<FastHit> hitsContext;

        public GetDocsumsResponse(byte compression, int uncompressedSize, byte[] compressedSlimeBytes, List<FastHit> hitsContext) {
            this.compression = compression;
            this.uncompressedSize = uncompressedSize;
            this.compressedSlimeBytes = compressedSlimeBytes;
            this.hitsContext = hitsContext;
        }

        public byte compression() {
            return compression;
        }

        public int uncompressedSize() {
            return uncompressedSize;
        }

        public byte[] compressedSlimeBytes() {
            return compressedSlimeBytes;
        }

        public List<FastHit> hitsContext() {
            return hitsContext;
        }

    }

    interface NodeConnection {
        void request(String rpcMethod, CompressionType compression, int uncompressedLength, byte[] compressedPayload,
                     ResponseReceiver responseReceiver, double timeoutSeconds);

        /** Closes this connection */
        void close();

    }

    class ProtobufResponse {

        private final byte compression;
        private final int uncompressedSize;
        private final byte[] compressedPayload;

        public ProtobufResponse(byte compression, int uncompressedSize, byte[] compressedPayload) {
            this.compression = compression;
            this.uncompressedSize = uncompressedSize;
            this.compressedPayload = compressedPayload;
        }

        public byte compression() {
            return compression;
        }

        public int uncompressedSize() {
            return uncompressedSize;
        }

        public byte[] compressedPayload() {
            return compressedPayload;
        }

    }

}
