// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.yahoo.jrt.*;
import com.yahoo.slime.*;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.ErrorCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Base class for new generation of config requests based on {@link Slime}. Allows for some customization of
 * payload encoding and decoding, as well as adding extra request/response fields. Used by both V2 and V3
 * config protocol.
 *
 * @author Ulf Lilleengen
 */
abstract class SlimeServerConfigRequest implements JRTServerConfigRequest {

    protected static final Logger log = Logger.getLogger(SlimeServerConfigRequest.class.getName());

    private static final JsonFactory jsonFactory = new JsonFactory();

    private final SlimeRequestData requestData;

    // Response values
    private boolean isDelayed = false;
    private Trace requestTrace = null;
    protected final Request request;

    protected SlimeServerConfigRequest(Request request) {
        this.requestData = new SlimeRequestData(request);
        this.request = request;
    }

    protected static JsonGenerator createJsonGenerator(ByteArrayOutputStream byteArrayOutputStream) throws IOException {
        return jsonFactory.createGenerator(byteArrayOutputStream);
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
                .append(",").append(getRequestConfigMd5())
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
    public long getWantedGeneration() {
        return requestData.getWantedGeneration();
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
    public String getRequestConfigMd5() {
        return requestData.getRequestConfigMd5();
    }

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

    protected static Value createResponseValue(ByteArrayOutputStream byteArrayOutputStream) {
        return new StringValue(new Utf8Array(byteArrayOutputStream.toByteArray()));
    }

    protected void addCommonReturnValues(JsonGenerator jsonGenerator) throws IOException {
        ConfigKey<?> key = requestData.getConfigKey();
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_VERSION, getProtocolVersion());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_NAME, key.getName());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_NAMESPACE, key.getNamespace());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_DEF_MD5, key.getMd5());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CONFIGID, key.getConfigId());
        setResponseField(jsonGenerator, SlimeResponseData.RESPONSE_CLIENT_HOSTNAME, requestData.getClientHostName());
        jsonGenerator.writeFieldName(SlimeResponseData.RESPONSE_TRACE);
        jsonGenerator.writeRawValue(getRequestTrace().toString(true));
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
    public Optional<VespaVersion> getVespaVersion() {
        return requestData.getVespaVersion();
    }

}
