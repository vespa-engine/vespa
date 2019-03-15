// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

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

    void getDocsums(List<FastHit> hits, NodeConnection node, CompressionType compression,
                    int uncompressedLength, byte[] compressedSlime, RpcFillInvoker.GetDocsumsResponseReceiver responseReceiver,
                    double timeoutSeconds);

    /** Creates a connection to a particular node in this */
    NodeConnection createConnection(String hostname, int port);

    class GetDocsumsResponseOrError {

        // One of these will be non empty and the other not
        private Optional<GetDocsumsResponse> response;
        private Optional<String> error;

        public static GetDocsumsResponseOrError fromResponse(GetDocsumsResponse response) {
            return new GetDocsumsResponseOrError(Optional.of(response), Optional.empty());
        }

        public static GetDocsumsResponseOrError fromError(String error) {
            return new GetDocsumsResponseOrError(Optional.empty(), Optional.of(error));
        }

        private GetDocsumsResponseOrError(Optional<GetDocsumsResponse> response, Optional<String> error) {
            this.response = response;
            this.error = error;
        }

        /** Returns the response, or empty if there is an error */
        public Optional<GetDocsumsResponse> response() { return response; }

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

        /** Closes this connection */
        void close();

    }

}
