// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.jrt.Request;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksum;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.protocol.Trace;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static com.yahoo.vespa.config.PayloadChecksum.Type.XXHASH64;

/**
 * @author bratseth
 */
public class ConfigTester {

    private static final long defaultTimeout = 10000;
    private static final List<String> defContent = Collections.singletonList("bar string");

    static RawConfig fooConfig;
    static RawConfig barConfig;
    static Payload fooPayload;

    static {
        String defName = "foo";
        String configId = "id";
        String namespace = "bar";
        ConfigKey<?> configKey = new ConfigKey<>(defName, configId, namespace);

        ConfigPayload fooConfigPayload = createConfigPayload("bar", "value");
        fooPayload = Payload.from(fooConfigPayload);

        long generation = 1;
        String defMd5 = ConfigUtils.getDefMd5(defContent);
        PayloadChecksums payloadChecksums = PayloadChecksums.from(PayloadChecksum.empty(MD5),
                                                                  PayloadChecksum.fromPayload(Payload.from(fooConfigPayload), XXHASH64));
        fooConfig = new RawConfig(configKey, defMd5, fooPayload, payloadChecksums,
                                  generation, false, defContent, Optional.empty());

        String defName2 = "bar";
        barConfig = new RawConfig(new ConfigKey<>(defName2, configId, namespace), defMd5, fooPayload, payloadChecksums,
                                  generation, false, defContent, Optional.empty());
    }

    JRTServerConfigRequest createRequest(RawConfig config) {
        return createRequest(config, defaultTimeout);
    }

    JRTServerConfigRequest createRequest(RawConfig config, long timeout) {
        return createRequest(config.getName(), config.getConfigId(), config.getNamespace(),
                             config.getPayloadChecksums(), config.getGeneration(), timeout);
    }

    JRTServerConfigRequest createRequest(String configName, String configId, String namespace, long timeout) {
        return createRequest(configName, configId, namespace, PayloadChecksums.empty(), 0, timeout);
    }

    private JRTServerConfigRequest createRequest(String configName,
                                                 String configId,
                                                 String namespace,
                                                 PayloadChecksums payloadChecksums,
                                                 long generation,
                                                 long timeout) {
        Request request = JRTClientConfigRequestV3.
                createWithParams(new ConfigKey<>(configName, configId, namespace, null),
                                 DefContent.fromList(defContent),
                                 "fromHost",
                                 payloadChecksums,
                                 generation,
                                 timeout,
                                 Trace.createDummy(),
                                 CompressionType.UNCOMPRESSED,
                                 Optional.empty()).getRequest();
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

    static ConfigPayload createConfigPayload(String key, String value) {
        Slime slime = new Slime();
        slime.setObject().setString(key, value);
        return new ConfigPayload(slime);
    }

}
