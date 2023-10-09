// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.foo.SimpletypesConfig;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.LZ4PayloadCompressor;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Ulf Lilleengen
 */
public class ConfigResponseTest {

    @Test
    public void require_that_slime_response_is_initialized() throws IOException {
        ConfigPayload configPayload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        PayloadChecksums payloadChecksums = PayloadChecksums.fromPayload(Payload.from(configPayload));
        ConfigResponse response =
                SlimeConfigResponse.fromConfigPayload(configPayload,
                                                      3,
                                                      false,
                                                      payloadChecksums);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        String payload = baos.toString(StandardCharsets.UTF_8);
        assertNotNull(payload);
        assertEquals("{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}", payload);
        assertEquals(3L, response.getGeneration());
        assertEquals(payloadChecksums.getForType(MD5), response.getPayloadChecksums().getForType(MD5));
        assertEquals(payloadChecksums.getForType(XXHASH64), response.getPayloadChecksums().getForType(XXHASH64));

        baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        assertEquals(baos.toString(),"{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}");
    }

    @Test
    public void require_that_slime_response_decompresses_on_serialize() throws IOException {
        ConfigPayload configPayload = ConfigPayload.fromInstance(new SimpletypesConfig(new SimpletypesConfig.Builder()));
        AbstractUtf8Array data = configPayload.toUtf8Array(true);
        Utf8Array bytes = new Utf8Array(new LZ4PayloadCompressor().compress(data.wrap()));
        ConfigResponse response = new SlimeConfigResponse(bytes, 3, false, PayloadChecksums.empty(), CompressionInfo.create(CompressionType.LZ4, data.getByteLength()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        String payload = baos.toString(StandardCharsets.UTF_8);
        assertNotNull(payload);

        baos = new ByteArrayOutputStream();
        response.serialize(baos, CompressionType.UNCOMPRESSED);
        assertEquals(baos.toString(), "{\"boolval\":false,\"doubleval\":0.0,\"enumval\":\"VAL1\",\"intval\":0,\"longval\":0,\"stringval\":\"s\"}");
    }

}
