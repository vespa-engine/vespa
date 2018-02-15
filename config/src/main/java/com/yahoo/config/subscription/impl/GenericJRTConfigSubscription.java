// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.util.List;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.DefContent;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;

/**
 * A JRT subscription which does not use the config class, but {@link com.yahoo.vespa.config.RawConfig} instead.
 * Used by config proxy.
 * @author vegardh
 *
 */
@SuppressWarnings("rawtypes")
public class GenericJRTConfigSubscription extends JRTConfigSubscription {

    private RawConfig config;
    private final List<String> defContent;

    @SuppressWarnings("unchecked")
    public GenericJRTConfigSubscription(ConfigKey<?> key,
                                        List<String> defContent,
            ConfigSubscriber subscriber,
            ConfigSource source,
            TimingValues timingValues) {
        super(key, subscriber, source, timingValues);
        this.defContent = defContent;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void setNewConfig(JRTClientConfigRequest jrtReq) {
        setConfig(jrtReq.getNewGeneration(), null );
        this.config = RawConfig.createFromResponseParameters(jrtReq);
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "in setNewConfig, config=" + this.config);
        }
    }

    // This method is overridden because config needs to have its generation
    // updated if _only_ generation has changed
    @Override
    void setGeneration(Long generation) {
        super.setGeneration(generation);
        if (this.config != null) {
            this.config.setGeneration(generation);
        }
    }

    public RawConfig getRawConfig() {
        return config;
    }

    /**
     * The config definition schema
     *
     * @return the config definition for this subscription
     */
    @Override
    public DefContent getDefContent() {
        return (DefContent.fromList(defContent));
    }
}
