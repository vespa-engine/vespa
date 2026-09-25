// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.ConfigResponse;
import com.yahoo.vespa.config.protocol.Payload;
import org.junit.Test;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Ulf Lilleengen
 */
public class ConfigResponseFactoryTest {

    private static final ConfigPayload payload = ConfigPayload.fromString("{ \"field1\": 11, \"field2\": 11 }");

    private static final PayloadChecksums payloadChecksums = PayloadChecksums.fromPayload(Payload.from(payload));
    private static final PayloadChecksums payloadChecksumsEmpty = PayloadChecksums.empty();
    private static final PayloadChecksums payloadChecksumsOnlyXxhash64 =
            PayloadChecksums.from(PayloadChecksum.fromPayload(Payload.from(payload), XXHASH64));

    @Test
    public void testUncompressedFactory() {
        UncompressedConfigResponseFactory responseFactory = new UncompressedConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(payload.toUtf8Array(true), 3, false, payloadChecksums);
        assertEquals(CompressionType.UNCOMPRESSED, response.getCompressionInfo().getCompressionType());
        assertEquals(3L,response.getGeneration());
        assertEquals(25, response.getPayload().getByteLength());
        assertNull(response.getPayloadChecksums().getForType(MD5));
        assertEquals(payloadChecksums.getForType(XXHASH64), response.getPayloadChecksums().getForType(XXHASH64));
    }

    @Test
    public void testLZ4CompressedFactory() {
        // Response never includes a md5 checksum, regardless of what the request contains, only xxhash64
        {
            ConfigResponse response = createResponse(payloadChecksums);
            assertNull(response.getPayloadChecksums().getForType(MD5));
            assertEquals(payloadChecksums.getForType(XXHASH64), response.getPayloadChecksums().getForType(XXHASH64));
        }

        {
            ConfigResponse response = createResponse(payloadChecksumsEmpty);
            assertNull(response.getPayloadChecksums().getForType(MD5));
            assertEquals(payloadChecksums.getForType(XXHASH64), response.getPayloadChecksums().getForType(XXHASH64));
        }

        {
            ConfigResponse response = createResponse(payloadChecksumsOnlyXxhash64);
            assertNull(response.getPayloadChecksums().getForType(MD5));
            assertEquals(payloadChecksumsOnlyXxhash64.getForType(XXHASH64), response.getPayloadChecksums().getForType(XXHASH64));
        }
    }

    private ConfigResponse createResponse(PayloadChecksums payloadChecksums) {
        LZ4ConfigResponseFactory responseFactory = new LZ4ConfigResponseFactory();
        ConfigResponse response = responseFactory.createResponse(payload.toUtf8Array(true), 3, false, payloadChecksums);
        assertEquals(CompressionType.LZ4, response.getCompressionInfo().getCompressionType());
        assertEquals(3L, response.getGeneration());
        assertEquals(23, response.getPayload().getByteLength());

        return response;
    }

}
