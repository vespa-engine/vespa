// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    interface ResponseReceiver {
        void receive(ResponseOrError<ProtobufResponse> response);
    }

    class ResponseOrError<T> {

        final Optional<T> response;
        final Optional<String> error;

        public static <T> ResponseOrError<T> fromResponse(T response) {
            return new ResponseOrError<>(response);
        }

        public static <T> ResponseOrError<T> fromError(String error) {
            return new ResponseOrError<T>(error);
        }

        ResponseOrError(T response) {
            this.response = Optional.of(response);
            this.error = Optional.empty();
        }

        ResponseOrError(String error) {
            this.response = Optional.empty();
            this.error = Optional.of(error);
        }

        /** Returns the response, or empty if there is an error */
        public Optional<T> response() { return response; }

        /** Returns the error or empty if there is a response */
        public Optional<String> error() { return error; }
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
        void getDocsums(List<FastHit> hits, CompressionType compression, int uncompressedLength, byte[] compressedSlime,
                RpcFillInvoker.GetDocsumsResponseReceiver responseReceiver, double timeoutSeconds);

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
