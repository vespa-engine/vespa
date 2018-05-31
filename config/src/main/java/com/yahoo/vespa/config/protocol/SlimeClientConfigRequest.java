// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.jrt.*;
import com.yahoo.slime.*;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Base class for new generation of config requests based on {@link Slime}. Allows for some customization of
 * payload encoding and decoding, as well as adding extra request/response fields.
 *
 * @author Ulf Lilleengen
 */
public abstract class SlimeClientConfigRequest implements JRTClientConfigRequest {

    protected static final Logger log = Logger.getLogger(SlimeClientConfigRequest.class.getName());

    protected final SlimeRequestData requestData;
    private final SlimeResponseData responseData;

    protected final Request request;

    protected SlimeClientConfigRequest(ConfigKey<?> key,
                                       String hostname,
                                       DefContent defSchema,
                                       String configMd5,
                                       long generation,
                                       long timeout,
                                       Trace trace,
                                       CompressionType compressionType,
                                       Optional<VespaVersion> vespaVersion) {
        Slime data = SlimeRequestData.encodeRequest(key,
                hostname,
                defSchema,
                configMd5,
                generation,
                timeout,
                trace,
                getProtocolVersion(),
                compressionType,
                vespaVersion);
        Request jrtReq = new Request(getJRTMethodName());
        jrtReq.parameters().add(new StringValue(encodeAsUtf8String(data, true)));

        this.requestData = new SlimeRequestData(jrtReq, data);
        this.responseData = new SlimeResponseData(jrtReq);
        this.request = jrtReq;
    }

    protected abstract String getJRTMethodName();

    protected static String encodeAsUtf8String(Slime data, boolean compact) {
        ByteArrayOutputStream baos = new NoCopyByteArrayOutputStream();
        try {
            new JsonFormat(compact).encode(baos, data);
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode config request", e);
        }
        return Utf8.toString(baos.toByteArray());
    }

    public ConfigKey<?> getConfigKey() {
        return requestData.getConfigKey();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("request='").append(getConfigKey())
                .append(",").append(getClientHostName())
                .append(",").append(getRequestConfigMd5())
                .append(",").append(getRequestGeneration())
                .append(",").append(getTimeout())
                .append(",").append(getVespaVersion()).append("'\n");
        sb.append("response='").append(getNewConfigMd5())
                .append(",").append(getNewGeneration())
                .append(",").append(isInternalRedeploy())
                .append("'\n");
        return sb.toString();
    }

    @Override
    public String getClientHostName() {
        return requestData.getClientHostName();
    }

    @Override
    public long getWantedGeneration() {
        return requestData.getWantedGeneration();
    }

    @Override
    public Request getRequest() {
        return request;
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

    @Override
    public boolean hasUpdatedGeneration() {
        long prevGen = getRequestGeneration();
        long newGen = getNewGeneration();
        return ConfigUtils.isGenerationNewer(newGen, prevGen);
    }

    @Override
    public long getTimeout() {
        return requestData.getTimeout();
    }

    protected String newConfMd5() {
        String newMd5 = getNewConfigMd5();
        if ("".equals(newMd5)) return getRequestConfigMd5();
        return newMd5;
    }

    protected long newGen() {
        long newGen = getNewGeneration();
        if (newGen==0) return getRequestGeneration();
        return newGen;
    }

    @Override
    public DefContent getDefContent() {
        return requestData.getSchema();
    }

    @Override
    public boolean isError() {
        return request.isError();
    }

    @Override
    public boolean containsPayload() {
        return false;
    }

    @Override
    public boolean hasUpdatedConfig() {
        String respMd5 = getNewConfigMd5();
        return !respMd5.equals("") && !getRequestConfigMd5().equals(respMd5);
    }

    @Override
    public Trace getResponseTrace() {
        return responseData.getResponseTrace();
    }

    @Override
    public String getRequestConfigMd5() {
        return requestData.getRequestConfigMd5();
    }

    @Override
    public boolean validateResponse() {
        if (request.isError()) {
            return false;
        } else if (request.returnValues().size() == 0) {
            return false;
        } else if (!checkReturnTypes(request)) {
            log.warning("Invalid return types for config response: " + errorMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean validateParameters() {
        int errorCode = RequestValidation.validateRequest(this);
        return (errorCode == 0);
    }

    protected abstract boolean checkReturnTypes(Request request);

    @Override
    public String getNewConfigMd5() {
        return responseData.getResponseConfigMd5();
    }

    @Override
    public long getNewGeneration() {
        return responseData.getResponseConfigGeneration();
    }

    @Override
    public boolean isInternalRedeploy() {
        return responseData.getResponseInternalRedeployment();
    }

    @Override
    public long getRequestGeneration() {
        return requestData.getRequestGeneration();
    }

    protected SlimeResponseData getResponseData() {
        return responseData;
    }

    public Optional<VespaVersion> getVespaVersion() {
        return requestData.getVespaVersion();
    }

}
