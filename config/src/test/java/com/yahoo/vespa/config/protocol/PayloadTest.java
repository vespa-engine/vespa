// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.google.common.testing.EqualsTester;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.LZ4PayloadCompressor;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class PayloadTest {

    @Test
    public void testUncompressedToCompressedWithoutCompressionInfo() {
        String json = "{\"foo\":13}";
        ConfigPayload configPayload = ConfigPayload.fromString(json);
        Payload payload = Payload.from(configPayload);
        assertEquals(json, payload.getData().toString());
        payload = Payload.from(payload.getData(), CompressionInfo.create(CompressionType.UNCOMPRESSED, 0));
        Payload compressed = payload.withCompression(CompressionType.LZ4);
        Payload uncompressed = compressed.withCompression(CompressionType.UNCOMPRESSED);
        assertEquals(json, uncompressed.getData().toString());
        assertEquals(json, compressed.toString());
        assertEquals(json, uncompressed.toString());
    }

    @Test
    public void testEquals() {
        final String foo1 = "foo 1";
        final String foo2 = "foo 2";

        Payload a = Payload.from(foo1);
        Payload b = Payload.from(foo1);

        Payload c = Payload.from(foo2);

        Slime slime = new Slime();
        slime.setString(foo1);
        Payload d = Payload.from(new ConfigPayload(slime));

        slime.setString(foo1);
        Payload e = Payload.from(new ConfigPayload(slime));

        slime.setString("foo 2");
        Payload f = Payload.from(new ConfigPayload(slime));

        Payload g, h, i, j;
        g = Payload.from(new Utf8Array(foo1.getBytes(StandardCharsets.UTF_8)), CompressionInfo.uncompressed());
        h = Payload.from(new Utf8Array(foo1.getBytes(StandardCharsets.UTF_8)), CompressionInfo.uncompressed());

        LZ4PayloadCompressor compressor = new LZ4PayloadCompressor();
        CompressionInfo info = CompressionInfo.create(CompressionType.LZ4, foo2.length());
        Utf8Array compressed = new Utf8Array(compressor.compress(foo2.getBytes()));

        i = Payload.from(compressed, info);
        j = Payload.from(compressed, info);

        new EqualsTester()
                .addEqualityGroup(a, b, g, h)
                .addEqualityGroup(c)
                .addEqualityGroup(d, e)
                .addEqualityGroup(f)
                .addEqualityGroup(i, j).
                testEquals();
    }
}
