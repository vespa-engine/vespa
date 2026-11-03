// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class ConfigResponseFactoryTest {

    private static final ConfigPayload payload = ConfigPayload.fromString("{ \"field1\": 11, \"field2\": 11 }");

    private static final PayloadChecksum payloadChecksum = PayloadChecksum.fromPayload(Payload.from(payload));

    @Test
    public void testUncompressedFactory() {
        UncompressedConfigResponseFactory responseFactory = new UncompressedConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(payload.toUtf8Array(true), 3, false, payloadChecksum);
        assertEquals(CompressionType.UNCOMPRESSED, response.getCompressionInfo().getCompressionType());
        assertEquals(3L,response.getGeneration());
        assertEquals(25, response.getPayload().getByteLength());
        assertEquals(payloadChecksum, response.getPayloadChecksum());
    }

    @Test
    public void testLZ4CompressedFactory() {
        // Response always includes a xxhash64 checksum, regardless of what the request contains
        assertEquals(payloadChecksum, createResponse(payloadChecksum).getPayloadChecksum());
        assertEquals(payloadChecksum, createResponse(PayloadChecksum.empty()).getPayloadChecksum());
    }

    private ConfigResponse createResponse(PayloadChecksum payloadChecksum) {
        LZ4ConfigResponseFactory responseFactory = new LZ4ConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(payload.toUtf8Array(true), 3, false, payloadChecksum);
        assertEquals(CompressionType.LZ4, response.getCompressionInfo().getCompressionType());
        assertEquals(3L, response.getGeneration());
        assertEquals(23, response.getPayload().getByteLength());

        return response;
    }

}
