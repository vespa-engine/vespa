// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.*;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author hmusum
 * @since 5.1.9
 */
// TODO: Move into ConfigTester and delete this class
public class Helper {

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

        List<String> defContent = Collections.singletonList("bar string");
        long generation = 1;
        String defMd5 = ConfigUtils.getDefMd5(defContent);
        String configMd5 = ConfigUtils.getMd5(fooConfigPayload);
        fooConfig = new RawConfig(configKey, defMd5, fooPayload, configMd5,
                                  generation, defContent, Optional.empty());

        String defName2 = "bar";
        barConfig = new RawConfig(new ConfigKey<>(defName2, configId, namespace), defMd5, fooPayload, configMd5,
                                  generation, defContent, Optional.empty());
    }

    static ConfigPayload createConfigPayload(String key, String value) {
        Slime slime = new Slime();
        slime.setObject().setString(key, value);
        return new ConfigPayload(slime);
    }



}
