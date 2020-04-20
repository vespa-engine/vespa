// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigPayload;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Class for serializing config responses based on {@link com.yahoo.slime.Slime} implementing the {@link ConfigResponse} interface.
 *
 * @author Ulf Lilleengen
 */
public class SlimeConfigResponse implements ConfigResponse {

    private final Utf8Array payload;
    private final CompressionInfo compressionInfo;
    private final long generation;
    private final boolean internalRedeploy;
    private final String configMd5;

    public static SlimeConfigResponse fromConfigPayload(ConfigPayload payload, long generation,
                                                        boolean internalRedeploy, String configMd5) {
        Utf8Array data = payload.toUtf8Array(true);
        return new SlimeConfigResponse(data, generation, internalRedeploy,
                                       configMd5,
                                       CompressionInfo.create(CompressionType.UNCOMPRESSED, data.getByteLength()));
    }

    public SlimeConfigResponse(Utf8Array payload,
                               long generation,
                               boolean internalRedeploy,
                               String configMd5,
                               CompressionInfo compressionInfo) {
        this.payload = payload;
        this.generation = generation;
        this.internalRedeploy = internalRedeploy;
        this.configMd5 = configMd5;
        this.compressionInfo = compressionInfo;
    }

    @Override
    public Utf8Array getPayload() {
        return payload;
    }

    @Override
    public long getGeneration() {
        return generation;
    }

    /**
     * Returns whether this application instance was produced by an internal redeployment,
     * not an application package change
     */
    @Override
    public boolean isInternalRedeploy() { return internalRedeploy; }

    @Override
    public String getConfigMd5() {
        return configMd5;
    }

    @Override
    public void serialize(OutputStream os, CompressionType type) throws IOException {
        os.write(Payload.from(payload, compressionInfo).withCompression(type).getData().getBytes());
    }

    @Override
    public String toString() {
        return "generation=" + generation +  "\n" +
                "configmd5=" + configMd5 +  "\n" +
                Payload.from(payload, compressionInfo).withCompression(CompressionType.UNCOMPRESSED);
    }

    @Override
    public CompressionInfo getCompressionInfo() { return compressionInfo; }

}
