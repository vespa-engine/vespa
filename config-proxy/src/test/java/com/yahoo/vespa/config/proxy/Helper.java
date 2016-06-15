// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author <a href="mailto:musum@yahoo-inc.com">Harald Musum</a>
 * @since 5.1.9
 */
public class Helper {

    static final long serverTimeout = 100000;
    static RawConfig fooConfig;
    static RawConfig fooConfigV2;
    static RawConfig barConfig;
    static Payload fooConfigPayload;

    static JRTServerConfigRequest fooConfigRequest;
    static JRTServerConfigRequest barConfigRequest;

    static ConfigCacheKey fooConfigConfigCacheKey;
    static ConfigCacheKey barConfigConfigCacheKey;

    static {
        ConfigTester tester = new ConfigTester();
        String defName = "foo";
        String configId = "id";
        String namespace = "bar";
        ConfigKey<?> configKey = new ConfigKey<>(defName, configId, namespace);
        Payload payloadV1 = Payload.from("bar \"value\"");
        Slime slime = new Slime();
        slime.setString("bar \"value\"");
        fooConfigPayload = Payload.from(new ConfigPayload(slime));

        List<String> defContent = Collections.singletonList("bar string");
        long generation = 1;
        String defMd5 = ConfigUtils.getDefMd5(defContent);
        String configMd5 = "5752ad0f757d7e711e32037f29940b73";
        fooConfig = new RawConfig(configKey, defMd5, payloadV1, configMd5, generation, defContent, Optional.empty());
        fooConfigV2 = new RawConfig(configKey, defMd5, fooConfigPayload, configMd5, generation, defContent, Optional.empty());
        fooConfigRequest = tester.createRequest(defName, configId, namespace, serverTimeout);
        fooConfigConfigCacheKey = new ConfigCacheKey(fooConfig.getKey(), fooConfig.getDefMd5());

        String defName2 = "bar";
        barConfig = new RawConfig(new ConfigKey<>(defName2, configId, namespace), defMd5, payloadV1, configMd5, generation, defContent, Optional.empty());
        barConfigRequest = tester.createRequest(defName2, configId, namespace, serverTimeout);
        barConfigConfigCacheKey = new ConfigCacheKey(barConfig.getKey(), barConfig.getDefMd5());
    }

}
