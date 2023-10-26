// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final PayloadChecksums payloadChecksums;
    private final Optional<VespaVersion> vespaVersion;
    private long generation;
    private boolean applyOnRestart;

    /**
     * Constructor for an empty config (not yet resolved).
     *
     * @param key     The ConfigKey
     * @param defMd5  The md5 sum of the .def-file.
     */
    public RawConfig(ConfigKey<?> key, String defMd5) {
        this(key, defMd5, null, PayloadChecksums.empty(), 0L, false, 0, Collections.emptyList(), Optional.empty());
    }

    public RawConfig(ConfigKey<?> key, String defMd5, Payload payload, PayloadChecksums payloadChecksums, long generation,
                     boolean applyOnRestart, List<String> defContent, Optional<VespaVersion> vespaVersion) {
        this(key, defMd5, payload, payloadChecksums, generation, applyOnRestart, 0, defContent, vespaVersion);
    }

    /** Copy constructor */
    public RawConfig(RawConfig rawConfig) {
        this(rawConfig.key, rawConfig.defMd5, rawConfig.payload, rawConfig.payloadChecksums,
             rawConfig.generation, rawConfig.applyOnRestart,
             rawConfig.errorCode, rawConfig.defContent, rawConfig.getVespaVersion());
    }

    public RawConfig(ConfigKey<?> key, String defMd5, Payload payload, PayloadChecksums payloadChecksums, long generation,
                     boolean applyOnRestart, int errorCode, List<String> defContent,
                     Optional<VespaVersion> vespaVersion) {
        this.key = key;
        this.defMd5 = ConfigUtils.getDefMd5FromRequest(defMd5, defContent);
        this.payload = payload;
        this.payloadChecksums = payloadChecksums;
        this.generation = generation;
        this.applyOnRestart = applyOnRestart;
        this.errorCode = errorCode;
        this.defContent = defContent;
        this.vespaVersion = vespaVersion;
    }

    /**
     * Creates a new Config from the given request, with the values in the response parameters.
     *
     * @param req a {@link JRTClientConfigRequest}
     */
    public static RawConfig createFromResponseParameters(JRTClientConfigRequest req) {
        return new RawConfig(req.getConfigKey(),
                             ConfigUtils.getDefMd5(req.getDefContent().asList()),
                             req.getNewPayload(),
                             req.getNewChecksums(),
                             req.getNewGeneration(),
                             req.responseIsApplyOnRestart(),
                             0,
                             req.getDefContent().asList(),
                             req.getVespaVersion());
    }

    /**
     * Creates a new Config from the given request, with the values in the response parameters.
     *
     * @param req a {@link JRTClientConfigRequest}
     */
    public static RawConfig createFromServerRequest(JRTServerConfigRequest req) {
        return new RawConfig(req.getConfigKey(),
                             ConfigUtils.getDefMd5(req.getDefContent().asList()),
                             Payload.from(new Utf8String(""), CompressionInfo.uncompressed()),
                             req.getRequestConfigChecksums(),
                             req.getRequestGeneration(),
                             req.applyOnRestart(),
                             0,
                             req.getDefContent().asList(),
                             req.getVespaVersion());
    }

    public ConfigKey<?> getKey() { return key; }

    public String getName() { return key.getName(); }

    public String getNamespace() { return key.getNamespace(); }

    public String getConfigId() { return key.getConfigId(); }

    public String getDefMd5() { return defMd5; }

    public long getGeneration() { return generation; }

    public void setGeneration(long generation) { this.generation = generation; }

    public void setApplyOnRestart(boolean applyOnRestart) { this.applyOnRestart = applyOnRestart; }

    public boolean applyOnRestart() { return applyOnRestart; }

    public Payload getPayload() { return payload; }

    public int errorCode() { return errorCode; }

    public String getDefNamespace() { return key.getNamespace(); }

    public Optional<VespaVersion> getVespaVersion() { return vespaVersion; }

    public PayloadChecksums getPayloadChecksums() { return payloadChecksums; }

    /**
     * Returns true if this config is equal to the config (same payload md5) in the given request.
     *
     * @param req the request for which to compare config payload with this config.
     * @return  true if this config is equal to the config in the given request.
     */
    public boolean hasEqualConfig(JRTServerConfigRequest req) {
        PayloadChecksums payloadChecksums = getPayloadChecksums();
        PayloadChecksum xxhash64 = payloadChecksums.getForType(PayloadChecksum.Type.XXHASH64);
        PayloadChecksum md5 = payloadChecksums.getForType(PayloadChecksum.Type.MD5);
        if (xxhash64 != null)
            return xxhash64.equals(req.getRequestConfigChecksums().getForType(PayloadChecksum.Type.XXHASH64));
        if (md5 != null)
            return md5.equals(req.getRequestConfigChecksums().getForType(PayloadChecksum.Type.MD5));

        return true;
    }

    /**
     * Returns true if this config has a more recent generation than the config in the given request.
     *
     * @param req the request for which to compare generation with this config.
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

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof RawConfig)) return false;

        RawConfig other = (RawConfig) o;
        if (! (key.equals(other.key) && defMd5.equals(other.defMd5) && (errorCode == other.errorCode)) )
            return false;

        // Need to check error codes before isError, since unequal error codes always means unequal requests,
        // while non-zero and equal error codes means configs are equal.
        if (isError()) return true;
        if (generation != other.generation) return false;
        return (payloadChecksums.equals(((RawConfig) o).payloadChecksums));
    }

    @Override
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
            // checksum and generation only matter when the RawConfig is not an error.
            hash = 31 * hash + (int)(generation ^(generation >>>32));
            hash = 31 * hash + payloadChecksums.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(key.getNamespace()).append(".").append(key.getName());
        sb.append(",");
        sb.append(getDefMd5());
        sb.append(",");
        sb.append(key.getConfigId());
        sb.append(",");
        sb.append(payloadChecksums);
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
