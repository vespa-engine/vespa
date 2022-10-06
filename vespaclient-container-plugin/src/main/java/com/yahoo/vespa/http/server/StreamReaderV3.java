// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.vespa.http.server.util.ByteLimitedInputStream;
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.vespaxmlparser.FeedReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * This code is based on v2 code, but restructured so stream reading code is in one dedicated class.
 *
 * @author dybis
 */
public class StreamReaderV3 {

    private final FeedReaderFactory feedReaderFactory;
    private final DocumentTypeManager docTypeManager;

    public StreamReaderV3(FeedReaderFactory feedReaderFactory, DocumentTypeManager docTypeManager) {
        this.feedReaderFactory = feedReaderFactory;
        this.docTypeManager = docTypeManager;
    }

    public FeedOperation getNextOperation(InputStream requestInputStream, FeederSettings settings) throws Exception {
        int length = readByteLength(requestInputStream);
        try (InputStream limitedInputStream = new ByteLimitedInputStream(requestInputStream, length)){
            FeedReader reader = feedReaderFactory.createReader(limitedInputStream, docTypeManager, settings.dataFormat);
            return reader.read();
        }
    }

    public Optional<String> getNextOperationId(InputStream requestInputStream) throws IOException {
        StringBuilder idBuf = new StringBuilder(100);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 32) {
                break;
            }
            idBuf.append((char) c);  // it's ASCII
        }
        if (idBuf.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(Encoder.decode(idBuf.toString(), new StringBuilder(idBuf.length())).toString());
    }

    private int readByteLength(InputStream requestInputStream) throws IOException {
        StringBuilder lenBuf = new StringBuilder(8);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 10) {
                break;
            }
            lenBuf.append((char) c);  // it's ASCII
        }
        if (lenBuf.length() == 0) {
            throw new IllegalStateException("Operation length missing.");
        }
        return Integer.valueOf(lenBuf.toString(), 16);
    }

    public static InputStream unzipStreamIfNeeded(final HttpRequest httpRequest) throws IOException {
        String contentEncodingHeader = httpRequest.getHeader("content-encoding");
        if ("gzip".equals(contentEncodingHeader)) {
            return new GZIPInputStream(httpRequest.getData());
        } else {
            return httpRequest.getData();
        }
    }

}
