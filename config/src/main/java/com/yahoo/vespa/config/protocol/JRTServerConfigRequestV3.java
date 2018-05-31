// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Request;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The V3 config protocol implemented on the server side. The V3 protocol uses 2 fields JRT
 *
 * * A metadata field containing json data describing config generation, md5 and compression info
 * * A data field containing compressed or uncompressed json config payload. This field can be empty if the payload
 *   has not changed since last request, triggering an optimization at the client where the previous payload is used instead.
 *
 * The implementation of addOkResponse is optimized for doing as little copying of payload data as possible, ensuring
 * that we get a lower memory footprint.
 *
 * @author Ulf Lilleengen
 */
// TODO: Merge with parent
public class JRTServerConfigRequestV3 extends SlimeServerConfigRequest {

    /** Response field */
    private boolean internalRedeploy = false;

    protected JRTServerConfigRequestV3(Request request) {
        super(request);
    }

    @Override
    public void addOkResponse(Payload payload, long generation, boolean internalRedeploy, String configMd5) {
        this.internalRedeploy = internalRedeploy;
        boolean changedConfig = !configMd5.equals(getRequestConfigMd5());
        boolean changedConfigAndNewGeneration = changedConfig && ConfigUtils.isGenerationNewer(generation, getRequestGeneration());
        Payload responsePayload = payload.withCompression(getCompressionType());
        ByteArrayOutputStream byteArrayOutputStream = new NoCopyByteArrayOutputStream(4096);
        try {
            JsonGenerator jsonGenerator = createJsonGenerator(byteArrayOutputStream);
            jsonGenerator.writeStartObject();
            addCommonReturnValues(jsonGenerator);
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIG_MD5, configMd5);
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIG_GENERATION, generation);
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_INTERNAL_REDEPLOY, internalRedeploy);
            jsonGenerator.writeObjectFieldStart(SlimeResponseData.RESPONSE_COMPRESSION_INFO);
            if (responsePayload == null) {
                throw new RuntimeException("Payload is null for ' " + this + ", not able to create response");
            }
            CompressionInfo compressionInfo = responsePayload.getCompressionInfo();
            // If payload is not being sent, we must adjust compression info to avoid client confusion.
            if (!changedConfigAndNewGeneration) {
                compressionInfo = CompressionInfo.create(compressionInfo.getCompressionType(), 0);
            }
            compressionInfo.serialize(jsonGenerator);
            jsonGenerator.writeEndObject();
            if (log.isLoggable(LogLevel.SPAM)) {
                log.log(LogLevel.SPAM, getConfigKey() + ": response dataXXXXX" + payload.withCompression(CompressionType.UNCOMPRESSED) + "XXXXX");
            }
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not add OK response for " + this);
        }
        request.returnValues().add(createResponseValue(byteArrayOutputStream));
        if (changedConfigAndNewGeneration) {
            request.returnValues().add(new DataValue(responsePayload.getData().getBytes()));
        } else {
            request.returnValues().add(new DataValue(new byte[0]));
        }
    }

    @Override
    public long getProtocolVersion() {
        return 3;
    }

    @Override
    public boolean isInternalRedeploy() { return internalRedeploy; }

    public static JRTServerConfigRequestV3 createFromRequest(Request req) {
        return new JRTServerConfigRequestV3(req);
    }

}
