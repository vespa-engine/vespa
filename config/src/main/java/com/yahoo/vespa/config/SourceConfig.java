// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import java.util.List;

/**
 * Interface for config instances that use a {@link Source} to retrieve config values.
 *
 * @author <a href="gv@yahoo-inc.com">Gj\u00F8ran Voldengen</a>
 */
public interface SourceConfig {

    /**
     * Notify subscribers that this config has been initialized by the {@link Source}.
     */
    public void notifyInitMonitor();

    /**
     * Sets the fields in the config object from the payload in the given request and updates subscribers
     * with the new config. The given request can be an error response with an error code and no payload.
     *
     * @param req  Config request containing return values, or an error response.
     */
    public void setConfig(com.yahoo.vespa.config.protocol.JRTClientConfigRequest req);

    /**
     * Sets this config's generation.
     *
     * @param generation The new generation (usually from the source).
     */
    public void setGeneration(long generation);

    public String getDefName();
    public String getDefNamespace();
    public String getDefVersion();
    public List<String> getDefContent();
    public String getDefMd5();

    public String getConfigId();
    public ConfigKey<?> getKey();

    public String getConfigMd5();

    public long getGeneration();

    public RawConfig getConfig();

}
