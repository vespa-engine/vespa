// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.subscription.ConfigInstanceSerializer;
import com.yahoo.config.subscription.ConfigInstanceUtil;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeFormat;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A config payload.
 *
 * @author Ulf Lilleengen
 */
public class ConfigPayload {

    private final Slime slime;

    public ConfigPayload(Slime slime) {
        this.slime = slime;
    }

    public static ConfigPayload fromInstance(ConfigInstance instance) {
        Slime slime = new Slime();
        ConfigInstanceSerializer serializer = new ConfigInstanceSerializer(slime);
        ConfigInstance.serialize(instance, serializer);
        return new ConfigPayload(slime);
    }

    public static ConfigPayload fromBuilder(ConfigPayloadBuilder builder) {
        Slime slime = new Slime();
        builder.resolve(slime.setObject());
        return new ConfigPayload(slime);
    }

    public Slime getSlime() {
        return slime;
    }

    public void serialize(OutputStream os, SlimeFormat format) throws IOException {
        format.encode(os, slime);
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean compact) {
        return toUtf8Array(compact).toString();
    }

    public ConfigPayload applyDefaultsFromDef(InnerCNode clientDef) {
        DefaultValueApplier defaultValueApplier = new DefaultValueApplier();
        defaultValueApplier.applyDefaults(slime, clientDef);
        return this;
    }

    public static ConfigPayload empty() {
        Slime slime = new Slime();
        slime.setObject();
        return new ConfigPayload(slime);
    }

    public static ConfigPayload fromString(String jsonString) {
        return fromUtf8Array(new Utf8String(jsonString));
    }

    public boolean isEmpty() {
        return !slime.get().valid() || slime.get().children() == 0;
    }

    public Utf8Array toUtf8Array(boolean compact) {
        ByteArrayOutputStream os = new ByteArrayOutputStream(10000);
        try {
            new JsonFormat(compact).encode(os, slime);
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Utf8Array(os.toByteArray());
    }

    public static ConfigPayload fromUtf8Array(Utf8Array payload) {
        return new ConfigPayload(new JsonDecoder().decode(new Slime(), payload.getBytes()));
    }

    public <ConfigType extends ConfigInstance> ConfigType toInstance(Class<ConfigType> clazz, String configId) {
        return ConfigInstanceUtil.getNewInstance(clazz, configId, this);
    }
}
