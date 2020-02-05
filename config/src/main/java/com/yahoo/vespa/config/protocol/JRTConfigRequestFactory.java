// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.*;

/**
 * To hide JRT implementations.
 *
 * @author Ulf Lilleengen
 */
public class JRTConfigRequestFactory {

    public static final String VESPA_CONFIG_PROTOCOL_VERSION = "VESPA_CONFIG_PROTOCOL_VERSION"; // Unused, but should be used if we add a new version
    private static final CompressionType compressionType = getCompressionType();
    private static final String VESPA_CONFIG_PROTOCOL_COMPRESSION = "VESPA_CONFIG_PROTOCOL_COMPRESSION";
    public static final String VESPA_VERSION = "VESPA_VERSION";

    public static <T extends ConfigInstance> JRTClientConfigRequest createFromSub(JRTConfigSubscription<T> sub) {
        // TODO: Get trace from caller
        return JRTClientConfigRequestV3.createFromSub(sub, Trace.createNew(), compressionType, getVespaVersion());
    }

    public static JRTClientConfigRequest createFromRaw(RawConfig config, long serverTimeout) {
        // TODO: Get trace from caller
        return JRTClientConfigRequestV3.createFromRaw(config, serverTimeout, Trace.createNew(), compressionType, getVespaVersion());
    }

    public static String getProtocolVersion() {
        return "3";
    }

    static String getProtocolVersion(String env, String alternateEnv, String property) {
        return ConfigUtils.getEnvValue("3", env, alternateEnv, property);
    }

    public static Set<Long> supportedProtocolVersions() {
        return Collections.singleton(3L);
    }

    public static CompressionType getCompressionType() {
        return getCompressionType(System.getenv(VESPA_CONFIG_PROTOCOL_COMPRESSION),
                System.getenv("services__config_protocol_compression"),
                System.getProperty(VESPA_CONFIG_PROTOCOL_COMPRESSION));
    }

    static CompressionType getCompressionType(String env, String alternateEnv, String property) {
        return CompressionType.valueOf(ConfigUtils.getEnvValue("LZ4", env, alternateEnv, property));
    }

    static Optional<VespaVersion> getVespaVersion() {
        final String envValue = ConfigUtils.getEnvValue("", System.getenv(VESPA_VERSION), System.getProperty(VESPA_VERSION));
        if (envValue != null && !envValue.isEmpty()) {
            return Optional.of(VespaVersion.fromString(envValue));
        }
        return Optional.of(getCompiledVespaVersion());
    }

    static VespaVersion getCompiledVespaVersion() {
        return VespaVersion.fromString(String.format("%d.%d.%d",
                com.yahoo.vespa.config.VespaVersion.major,
                com.yahoo.vespa.config.VespaVersion.minor,
                com.yahoo.vespa.config.VespaVersion.micro));
    }
}
