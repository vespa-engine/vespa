// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.jrt.Request;
import com.yahoo.text.Utf8Array;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.JRTMethods;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Optional;

/**
 * Represents version 3 config request for config clients. Provides methods for inspecting request and response
 * values.
 *
 * See {@link JRTServerConfigRequestV3} for protocol details.
 *
 * @author Ulf Lilleengen
 */
public class JRTClientConfigRequestV3 extends SlimeClientConfigRequest {

    protected JRTClientConfigRequestV3(ConfigKey<?> key,
                                       String hostname,
                                       DefContent defSchema,
                                       String configMd5,
                                       long generation,
                                       long timeout,
                                       Trace trace,
                                       CompressionType compressionType,
                                       Optional<VespaVersion> vespaVersion) {
        super(key, hostname, defSchema, configMd5, generation, timeout, trace, compressionType, vespaVersion);
    }

    @Override
    protected String getJRTMethodName() {
        return JRTMethods.configV3getConfigMethodName;
    }

    @Override
    protected boolean checkReturnTypes(Request request) {
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
                isError() ? getRequestConfigMd5() : newConfMd5(),
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
        String hostname = ConfigUtils.getCanonicalHostName();
        ConfigKey<T> key = sub.getKey();
        ConfigSubscription.ConfigState<T> configState = sub.getConfigState();
        T i = configState.getConfig();
        return createWithParams(key,
                sub.getDefContent(),
                hostname,
                i != null ? i.getConfigMd5() : "",
                configState.getGeneration() != null ? configState.getGeneration() : 0L,
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
                config.getConfigMd5(),
                config.getGeneration(),
                serverTimeout,
                trace,
                compressionType,
                vespaVersion);
    }


    public static JRTClientConfigRequest createWithParams(ConfigKey<?> reqKey,
                                                          DefContent defContent,
                                                          String hostname,
                                                          String configMd5,
                                                          long generation,
                                                          long serverTimeout,
                                                          Trace trace,
                                                          CompressionType compressionType,
                                                          Optional<VespaVersion> vespaVersion) {
        return new JRTClientConfigRequestV3(reqKey,
                hostname,
                defContent,
                configMd5,
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

}
