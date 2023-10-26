// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Class for serializing config responses based on {@link com.yahoo.slime.Slime} implementing the {@link ConfigResponse} interface.
 *
 * @author Ulf Lilleengen
 */
public class SlimeConfigResponse implements ConfigResponse {

    private final AbstractUtf8Array payload;
    private final CompressionInfo compressionInfo;
    private final long generation;
    private final boolean applyOnRestart;
    private final PayloadChecksums payloadChecksums;

    public static SlimeConfigResponse fromConfigPayload(ConfigPayload payload,
                                                        long generation,
                                                        boolean applyOnRestart,
                                                        PayloadChecksums payloadChecksums) {
        AbstractUtf8Array data = payload.toUtf8Array(true);
        return new SlimeConfigResponse(data,
                                       generation,
                                       applyOnRestart,
                                       payloadChecksums,
                                       CompressionInfo.create(CompressionType.UNCOMPRESSED, data.getByteLength()));
    }

    public SlimeConfigResponse(AbstractUtf8Array payload,
                               long generation,
                               boolean applyOnRestart,
                               PayloadChecksums payloadChecksums,
                               CompressionInfo compressionInfo) {
        this.payload = payload;
        this.generation = generation;
        this.applyOnRestart = applyOnRestart;
        this.payloadChecksums = payloadChecksums;
        this.compressionInfo = compressionInfo;
    }

    @Override
    public AbstractUtf8Array getPayload() {
        return payload;
    }

    @Override
    public long getGeneration() {
        return generation;
    }

    @Override
    public boolean applyOnRestart() { return applyOnRestart; }

    @Override
    public void serialize(OutputStream os, CompressionType type) throws IOException {
        ByteBuffer buf = Payload.from(payload, compressionInfo).withCompression(type).getData().wrap();
        os.write(buf.array(), buf.arrayOffset()+buf.position(), buf.remaining());
    }

    @Override
    public String toString() {
        return "generation=" + generation +  "\n" +
                "checksums=" + payloadChecksums +  "\n" +
                Payload.from(payload, compressionInfo).withCompression(CompressionType.UNCOMPRESSED);
    }

    @Override
    public CompressionInfo getCompressionInfo() { return compressionInfo; }

    @Override
    public PayloadChecksums getPayloadChecksums() { return payloadChecksums; }
}
