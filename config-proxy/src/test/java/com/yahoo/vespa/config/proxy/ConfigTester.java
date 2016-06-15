// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.jrt.Request;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequestV3;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequestV3;
import com.yahoo.vespa.config.protocol.Trace;

import java.util.Collections;
import java.util.Optional;

/**
 * @author bratseth
 */
public class ConfigTester {

    private final long defaultTimeout = 10000;

    public JRTServerConfigRequest createRequest(RawConfig config) {
        return createRequest(config, defaultTimeout);
    }

    public JRTServerConfigRequest createRequest(RawConfig config, long timeout) {
        return createRequest(config.getName(), config.getConfigId(), config.getNamespace(),
                             config.getConfigMd5(), config.getDefMd5(), config.getGeneration(), timeout);
    }

    public JRTServerConfigRequest createRequest(String configName, String configId, String namespace) {
        return createRequest(configName, configId, namespace, defaultTimeout);
    }

    public JRTServerConfigRequest createRequest(String configName, String configId, String namespace, long timeout) {
        return createRequest(configName, configId, namespace, "", null, 0, timeout);
    }

    public JRTServerConfigRequest createRequest(String configName, String configId, String namespace, String md5, String defMd5, long generation, long timeout) {
        Request request = JRTClientConfigRequestV3.
                createWithParams(new ConfigKey<>(configName, configId, namespace, defMd5, null), DefContent.fromList(Collections.emptyList()),
                                 "fromHost", md5, generation, timeout, Trace.createDummy(), CompressionType.UNCOMPRESSED,
                                 Optional.empty()).getRequest();
        return JRTServerConfigRequestV3.createFromRequest(request);
    }

}
