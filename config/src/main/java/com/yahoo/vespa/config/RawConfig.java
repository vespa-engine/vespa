// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.text.Utf8String;
import com.yahoo.vespa.config.protocol.CompressionInfo;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.JRTConfigRequest;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;
import com.yahoo.vespa.config.protocol.VespaVersion;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Encapsulates config, usually associated with a {@link JRTConfigRequest}. An instance of this class can represent
 * either a config that is not yet resolved, a successfully resolved config, or an error.
 *
 * @author hmusum
 */
public class RawConfig extends ConfigInstance {

    private final ConfigKey<?> key;
    private final String defMd5;
    private final List<String> defContent;
    private final Payload payload;
    private final int errorCode;
    private final String configMd5;
    private final Optional<VespaVersion> vespaVersion;
    private long generation;
    private boolean internalRedeploy;

    /**
     * Constructor for an empty config (not yet resolved).
     * @param key     The ConfigKey
     * @param defMd5  The md5 sum of the .def-file.
     */
    public RawConfig(ConfigKey<?> key, String defMd5) {
        this(key, defMd5, null, "", 0L, false, 0, Collections.emptyList(), Optional.empty());
    }

    public RawConfig(ConfigKey<?> key, String defMd5, Payload payload, String configMd5, long generation,
                     boolean internalRedeploy, List<String> defContent, Optional<VespaVersion> vespaVersion) {
        this(key, defMd5, payload, configMd5, generation, internalRedeploy, 0, defContent, vespaVersion);
    }

    /** Copy constructor */
    public RawConfig(RawConfig rawConfig) {
        this(rawConfig.key, rawConfig.defMd5, rawConfig.payload, rawConfig.configMd5,
             rawConfig.generation, rawConfig.internalRedeploy, rawConfig.errorCode,
             rawConfig.defContent, rawConfig.getVespaVersion());
    }

    public RawConfig(ConfigKey<?> key, String defMd5, Payload payload, String configMd5, long generation,
                     boolean internalRedeploy, int errorCode, List<String> defContent, Optional<VespaVersion> vespaVersion) {
        this.key = key;
        this.defMd5 = ConfigUtils.getDefMd5FromRequest(defMd5, defContent);
        this.payload = payload;
        this.configMd5 = configMd5;
        this.generation = generation;
        this.internalRedeploy = internalRedeploy;
        this.errorCode = errorCode;
        this.defContent = defContent;
        this.vespaVersion = vespaVersion;
    }

    /**
     * Creates a new Config from the given request, with the values in the response parameters.
     * @param req a {@link JRTClientConfigRequest}
     */
    public static RawConfig createFromResponseParameters(JRTClientConfigRequest req) {
        return new RawConfig(req.getConfigKey(),
                             req.getConfigKey().getMd5(),
                             req.getNewPayload(),
                             req.getNewConfigMd5(),
                             req.getNewGeneration(),
                             req.isInternalRedeploy(),
                             0,
                             req.getDefContent().asList(),
                             req.getVespaVersion());
    }

    /**
     * Creates a new Config from the given request, with the values in the response parameters.
     * @param req a {@link JRTClientConfigRequest}
     */
    public static RawConfig createFromServerRequest(JRTServerConfigRequest req) {
        return new RawConfig(req.getConfigKey(),
                             req.getConfigKey().getMd5() ,
                             Payload.from(new Utf8String(""), CompressionInfo.uncompressed()),
                             req.getRequestConfigMd5(),
                             req.getRequestGeneration(),
                             req.isInternalRedeploy(),
                             0,
                             req.getDefContent().asList(),
                             req.getVespaVersion());
    }


    public ConfigKey<?> getKey() { return key; }

    public String getName() { return key.getName(); }

    public String getNamespace() { return key.getNamespace(); }

    public String getConfigId() { return key.getConfigId(); }

    public String getConfigMd5() { return configMd5; }

    public String getDefMd5() { return defMd5; }

    public long getGeneration() { return generation; }

    public void setGeneration(long generation) { this.generation = generation; }

    /**
     * Returns whether this config generation was created by a system internal redeploy, not an
     * application package change.
     */
    public boolean isInternalRedeploy() { return internalRedeploy; }

    public Payload getPayload() { return payload; }

    public int errorCode() { return errorCode; }

    public String getDefNamespace() { return key.getNamespace(); }

    public Optional<VespaVersion> getVespaVersion() { return vespaVersion; }

    /**
     * Returns true if this config is equal to the config (same payload md5) in the given request.
     *
     * @param req  The request for which to compare config payload with this config.
     * @return  true if this config is equal to the config in the given request.
     */
    public boolean hasEqualConfig(JRTServerConfigRequest req) {
        return (getConfigMd5().equals(req.getRequestConfigMd5()));
    }

    /**
     * Returns true if this config has a more recent generation than the config in the given request.
     *
     * @param req  The request for which to compare generation with this config.
     * @return true if this config has a more recent generation than the config in the given request.
     */
    public boolean hasNewerGeneration(JRTServerConfigRequest req) {
        return (getGeneration() > req.getRequestGeneration());
    }

    /**
     * Convenience method.
     * @return true if errorCode() returns 0, false otherwise.
     */
    public boolean isError() {
        return (errorCode() != 0);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (! (o instanceof RawConfig)) {
            return false;
        }
        RawConfig other = (RawConfig) o;
        if (! (key.equals(other.key) &&
                defMd5.equals(other.defMd5) &&
                (errorCode == other.errorCode)) ) {
            return false;
        }
        // Need to check error codes before isError, since unequal error codes always means unequal requests,
        // while non-zero and equal error codes means configs are equal.
        if (isError())
            return true;
        if (generation != other.generation)
            return false;
        if (configMd5 != null) {
            return configMd5.equals(other.configMd5);
        } else {
            return (other.configMd5 == null);
        }
    }

    public int hashCode() {
        int hash = 17;
        if (key != null) {
            hash = 31 * hash + key.hashCode();
        }
        if (defMd5 != null) {
            hash = 31 * hash + defMd5.hashCode();
        }
        hash = 31 * hash + errorCode;
        if (! isError()) {
            // configMd5 and generation only matter when the RawConfig is not an error.
            hash = 31 * hash + (int)(generation ^(generation >>>32));
            if (configMd5 != null) {
                hash = 31 * hash + configMd5.hashCode();
            }
        }
        return hash;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(key.getNamespace()).append(".").append(key.getName());
        sb.append(",");
        sb.append(getDefMd5());
        sb.append(",");
        sb.append(key.getConfigId());
        sb.append(",");
        sb.append(getConfigMd5());
        sb.append(",");
        sb.append(getGeneration());
        sb.append(",");
        sb.append(getPayload());
        return sb.toString();
    }

    public List<String> getDefContent() {
        return defContent;
    }
}
