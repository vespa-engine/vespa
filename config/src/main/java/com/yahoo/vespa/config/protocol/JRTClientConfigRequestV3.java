// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.JRTMethods;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents version 3 config request for config clients. Provides methods for inspecting request and response
 * values.
 * See {@link JRTServerConfigRequestV3} for protocol details.
 *
 * @author Ulf Lilleengen
 */
public final class JRTClientConfigRequestV3 implements JRTClientConfigRequest {

    private static final Logger log = Logger.getLogger(JRTClientConfigRequestV3.class.getName());

    private final SlimeRequestData requestData;
    private final Request request;
    private final SlimeResponseData responseData;

    JRTClientConfigRequestV3(ConfigKey<?> key,
                             String hostname,
                             DefContent defSchema,
                             PayloadChecksum payloadChecksum,
                             long generation,
                             long timeout,
                             Trace trace,
                             CompressionType compressionType,
                             Optional<VespaVersion> vespaVersion) {
        Slime data = SlimeRequestData.encodeRequest(key,
                                                    hostname,
                                                    defSchema,
                                                    payloadChecksum,
                                                    generation,
                                                    timeout,
                                                    trace,
                                                    getProtocolVersion(),
                                                    compressionType,
                                                    vespaVersion);
        Request jrtReq = new Request(getJRTMethodName());
        jrtReq.parameters().add(new StringValue(encodeAsUtf8String(data)));

        this.requestData = new SlimeRequestData(jrtReq, data);
        this.responseData = new SlimeResponseData(jrtReq);
        this.request = jrtReq;
    }

    static String encodeAsUtf8String(Slime data) {
        ByteArrayOutputStream baos = new NoCopyByteArrayOutputStream();
        try {
            new JsonFormat(true /* compact format */).encode(baos, data);
        } catch (IOException e) {
            throw new RuntimeException("Unable to encode config request", e);
        }
        return Utf8.toString(baos.toByteArray());
    }

    String getJRTMethodName() {
        return JRTMethods.configV3getConfigMethodName;
    }

    boolean checkReturnTypes(Request request) {
        return JRTMethods.checkV3ReturnTypes(request);
    }

    @Override
    public Payload getNewPayload() {
        CompressionInfo compressionInfo = getResponseData().getCompressionInfo();
        Utf8Array payload = new Utf8Array(request.returnValues().get(1).asData());
        return Payload.from(payload, compressionInfo);
    }

    @Override
    public long getProtocolVersion() {
        return 3;
    }

    @Override
    public JRTClientConfigRequest nextRequest(long timeout) {
        return new JRTClientConfigRequestV3(getConfigKey(),
                getClientHostName(),
                getDefContent(),
                isError() ? getRequestConfigChecksum() : newConfigChecksum(),
                isError() ? getRequestGeneration() : newGen(),
                timeout,
                Trace.createNew(),
                requestData.getCompressionType(),
                requestData.getVespaVersion());
    }

    public static <T extends ConfigInstance> JRTClientConfigRequest createFromSub(JRTConfigSubscription<T> sub,
                                                                                  Trace trace,
                                                                                  CompressionType compressionType,
                                                                                  Optional<VespaVersion> vespaVersion) {
        ConfigSubscription.ConfigState<T> configState = sub.getConfigState();
        return createWithParams(sub.getKey(),
                                sub.getDefContent(),
                                ConfigUtils.getCanonicalHostName(),
                                configState.getChecksum(),
                                configState.getGeneration(),
                                sub.timingValues().getSubscribeTimeout(),
                                trace,
                                compressionType,
                                vespaVersion);
    }

    public static JRTClientConfigRequest createFromRaw(RawConfig config,
                                                       long serverTimeout,
                                                       Trace trace,
                                                       CompressionType compressionType,
                                                       Optional<VespaVersion> vespaVersion) {
        String hostname = ConfigUtils.getCanonicalHostName();
        return createWithParams(config.getKey(),
                                DefContent.fromList(config.getDefContent()),
                                hostname,
                                config.getPayloadChecksum(),
                                config.getGeneration(),
                                serverTimeout,
                                trace,
                                compressionType,
                                vespaVersion);
    }

    public static JRTClientConfigRequest createWithParams(ConfigKey<?> reqKey,
                                                          DefContent defContent,
                                                          String hostname,
                                                          PayloadChecksum payloadChecksum,
                                                          long generation,
                                                          long serverTimeout,
                                                          Trace trace,
                                                          CompressionType compressionType,
                                                          Optional<VespaVersion> vespaVersion) {
        return new JRTClientConfigRequestV3(reqKey,
                                            hostname,
                                            defContent,
                                            payloadChecksum,
                                            generation,
                                            serverTimeout,
                                            trace,
                                            compressionType,
                                            vespaVersion);
    }

    @Override
    public Optional<VespaVersion> getVespaVersion() {
        return requestData.getVespaVersion();
    }

    public ConfigKey<?> getConfigKey() {
        return requestData.getConfigKey();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("request='").append(getConfigKey())
                .append(",").append(getClientHostName())
                .append(",").append(getRequestConfigChecksum())
                .append(",").append(getRequestGeneration())
                .append(",").append(getTimeout())
                .append(",").append(getVespaVersion().map(VespaVersion::toString).orElse(""))
                .append("'\n");
                 sb.append("response='").append(getNewChecksum())
                .append(",").append(getNewGeneration())
                .append(",").append(responseIsApplyOnRestart())
                .append("'\n");
        return sb.toString();
    }

    @Override
    public String getClientHostName() {
        return requestData.getClientHostName();
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

    PayloadChecksum newConfigChecksum() {
        PayloadChecksum newChecksum = getNewChecksum();
        if (PayloadChecksum.empty().equals(newChecksum)) return getRequestConfigChecksum();
        return newChecksum;
    }

    long newGen() {
        long newGen = getNewGeneration();
        if (newGen == 0) return getRequestGeneration();
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
    public boolean hasUpdatedConfig() {
        PayloadChecksum requestConfigChecksum = getRequestConfigChecksum();
        log.log(Level.FINE, () -> "request checksum for " + getConfigKey() + ":"  + requestConfigChecksum);

        PayloadChecksum newChecksum = getNewChecksum();
        log.log(Level.FINE, () -> "new checksum for " + getConfigKey() + ": " + newChecksum);
        if (newChecksum.isEmpty()) return false;

        return ! requestConfigChecksum.equals(newChecksum);
    }

    @Override
    public Trace getResponseTrace() {
        return responseData.getResponseTrace();
    }

    @Override
    public String getRequestDefMd5() {
        return requestData.getRequestDefMd5();
    }

    public PayloadChecksum getRequestConfigChecksum() {
        return requestData.getRequestConfigChecksum();
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

    @Override
    public PayloadChecksum getNewChecksum() {
        return responseData.getResponseConfigChecksum();
    }

    @Override
    public long getNewGeneration() {
        return responseData.getResponseConfigGeneration();
    }

    @Override
    public boolean responseIsApplyOnRestart() {
        return responseData.getResponseApplyOnRestart();
    }

    @Override
    public long getRequestGeneration() {
        return requestData.getRequestGeneration();
    }

    SlimeResponseData getResponseData() {
        return responseData;
    }
}
