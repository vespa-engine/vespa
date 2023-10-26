// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Value;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ErrorCode;
import com.yahoo.vespa.config.PayloadChecksums;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

/**
 * The V3 config protocol implemented on the server side. The V3 protocol uses 2 fields:
 *
 * * A metadata field containing json data describing config generation, md5 and compression info
 * * A data field containing compressed or uncompressed json config payload
 *
 * The implementation of addOkResponse is optimized for doing as little copying of payload data as possible, ensuring
 * that we get a lower memory footprint.
 *
 * @author Ulf Lilleengen
 */
public class JRTServerConfigRequestV3 implements JRTServerConfigRequest {

    protected static final Logger log = Logger.getLogger(JRTServerConfigRequestV3.class.getName());
    private static final JsonFactory jsonFactory = new JsonFactory();
    protected final Request request;
    private final SlimeRequestData requestData;
    /** Response field */
    private boolean applyOnRestart = false;
    // Response values
    private boolean isDelayed = false;
    private Trace requestTrace = null;

    protected JRTServerConfigRequestV3(Request request) {
        this.requestData = new SlimeRequestData(request);
        this.request = request;
    }

    protected static JsonGenerator createJsonGenerator(ByteArrayOutputStream byteArrayOutputStream) throws IOException {
        return jsonFactory.createGenerator(byteArrayOutputStream);
    }

    protected static Value createResponseValue(ByteArrayOutputStream byteArrayOutputStream) {
        return new StringValue(new Utf8Array(byteArrayOutputStream.toByteArray()));
    }

    protected static void setResponseField(JsonGenerator jsonGenerator, String fieldName, String value) throws IOException {
        jsonGenerator.writeStringField(fieldName, value);
    }

    protected static void setResponseField(JsonGenerator jsonGenerator, String fieldName, long value) throws IOException {
        jsonGenerator.writeNumberField(fieldName, value);
    }

    protected static void setResponseField(JsonGenerator jsonGenerator, String fieldName, boolean value) throws IOException {
        jsonGenerator.writeBooleanField(fieldName, value);
    }

    @Override
    public void addOkResponse(Payload payload, long generation, boolean applyOnRestart, PayloadChecksums checksums) {
        this.applyOnRestart = applyOnRestart;
        Payload responsePayload = payload.withCompression(getCompressionType());
        if (responsePayload == null)
            throw new RuntimeException("Payload is null for ' " + this + ", not able to create response");

        ByteArrayOutputStream outputStream = new NoCopyByteArrayOutputStream(4096);
        try {
            JsonGenerator jsonGenerator = createJsonGenerator(outputStream);
            jsonGenerator.writeStartObject();

            addCommonReturnValues(jsonGenerator);
            addPayloadCheckSums(jsonGenerator, checksums);
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIG_GENERATION, generation);
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_APPLY_ON_RESTART, applyOnRestart);
            jsonGenerator.writeObjectFieldStart(SlimeResponseData.RESPONSE_COMPRESSION_INFO);
            responsePayload.getCompressionInfo().serialize(jsonGenerator);
            jsonGenerator.writeEndObject();

            jsonGenerator.writeEndObject();
            jsonGenerator.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not add OK response for " + this);
        }
        addPayload(responsePayload, outputStream);
    }

    @Override
    public long getProtocolVersion() {
        return 3;
    }

    @Override
    public boolean applyOnRestart() { return applyOnRestart; }

    public static JRTServerConfigRequestV3 createFromRequest(Request req) {
        return new JRTServerConfigRequestV3(req);
    }

    @Override
    public ConfigKey<?> getConfigKey() {
        return requestData.getConfigKey();
    }

    @Override
    public DefContent getDefContent() {
        return getSchema();
    }

    @Override
    public boolean noCache() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("request='").append(getConfigKey())
                .append(",").append(getClientHostName())
                .append(",").append(getRequestConfigChecksums())
                .append(",").append(getRequestGeneration())
                .append(",").append(getTimeout()).append("'\n");
        return sb.toString();
    }

    @Override
    public Payload payloadFromResponse(ConfigResponse response) {
        return Payload.from(response.getPayload(), response.getCompressionInfo());
    }

    private DefContent getSchema() {
        return requestData.getSchema();
    }

    @Override
    public String getClientHostName() {
        return requestData.getClientHostName();
    }

    public Trace getRequestTrace() {
        if (requestTrace == null) {
            requestTrace = requestData.getRequestTrace();
        }
        return requestTrace;
    }

    @Override
    public Request getRequest() {
        return request;
    }

    @Override
    public boolean validateParameters() {
        int errorCode = RequestValidation.validateRequest(this);
        if (errorCode != 0) {
            addErrorResponse(errorCode);
        }
        return (errorCode == 0);
    }

    @Override
    public String getRequestDefMd5() { return requestData.getRequestDefMd5(); }

    public PayloadChecksums getRequestConfigChecksums() { return requestData.getRequestConfigChecksums(); }

    private void addErrorResponse(int errorCode) {
        addErrorResponse(errorCode, ErrorCode.getName(errorCode));
    }

    @Override
    public void setDelayedResponse(boolean delayedResponse) {
        this.isDelayed = delayedResponse;
    }

    @Override
    public void addErrorResponse(int errorCode, String name) {
        ByteArrayOutputStream byteArrayOutputStream = new NoCopyByteArrayOutputStream();
        try {
            JsonGenerator jsonWriter = jsonFactory.createGenerator(byteArrayOutputStream);
            jsonWriter.writeStartObject();
            addCommonReturnValues(jsonWriter);
            jsonWriter.writeEndObject();
            jsonWriter.close();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not add error response for " + this);
        }
        request.setError(errorCode, name);
        request.returnValues().add(createResponseValue(byteArrayOutputStream));
    }

    protected void addCommonReturnValues(JsonGenerator jsonGenerator) throws IOException {
        ConfigKey<?> key = requestData.getConfigKey();
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_VERSION, getProtocolVersion());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_NAME, key.getName());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_NAMESPACE, key.getNamespace());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_MD5, requestData.getRequestDefMd5());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIGID, key.getConfigId());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CLIENT_HOSTNAME, requestData.getClientHostName());
        jsonGenerator.writeFieldName(SlimeResponseData.RESPONSE_TRACE);
        jsonGenerator.writeRawValue(getRequestTrace().toString(true));
    }

    private void addPayloadCheckSums(JsonGenerator jsonGenerator, PayloadChecksums checksums) throws IOException {
        if (checksums.getForType(MD5) != null)
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIG_MD5, checksums.getForType(MD5).asString());
        if (checksums.getForType(XXHASH64) != null)
            setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIG_XXHASH64, checksums.getForType(XXHASH64).asString());
    }

    private void addPayload(Payload responsePayload, ByteArrayOutputStream byteArrayOutputStream) {
        request.returnValues().add(createResponseValue(byteArrayOutputStream));
        ByteBuffer buf = responsePayload.getData().wrap();
        if (buf.hasArray() && buf.remaining() == buf.array().length) {
            request.returnValues().add(new DataValue(buf.array()));
        } else {
            byte[] dst = new byte[buf.remaining()];
            buf.get(dst);
            request.returnValues().add(new DataValue(dst));
        }
    }

    @Override
    public long getRequestGeneration() {
        return requestData.getRequestGeneration();
    }

    @Override
    public boolean isDelayedResponse() {
        return isDelayed;
    }

    @Override
    public int errorCode() {
        return request.errorCode();
    }

    @Override
    public String errorMessage() {
        return request.errorMessage();
    }

    @Override
    public String getShortDescription() {
        return toString();
    }

    protected CompressionType getCompressionType() {
        return requestData.getCompressionType();
    }

    @Override
    public long getTimeout() {
        return requestData.getTimeout();
    }

    @Override
    public Optional<VespaVersion> getVespaVersion() { return requestData.getVespaVersion(); }

    @Override
    public PayloadChecksums configPayloadChecksums() { return requestData.getRequestConfigChecksums(); }

}
