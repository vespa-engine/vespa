// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.impl.JRTConfigSubscription;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.util.ConfigUtils;

import java.util.Optional;

/**
 * To hide JRT implementations.
 *
 * @author Ulf Lilleengen
 */
public class JRTConfigRequestFactory {

    private static final CompressionType compressionType = getCompressionType();
    private static final String VESPA_CONFIG_PROTOCOL_COMPRESSION = "VESPA_CONFIG_PROTOCOL_COMPRESSION";

    public static <T extends ConfigInstance> JRTClientConfigRequest createFromSub(JRTConfigSubscription<T> sub) {
        // TODO: Get trace from caller
        return JRTClientConfigRequestV3.createFromSub(sub, Trace.createNew(), compressionType, getVespaVersion());
    }

    public static JRTClientConfigRequest createFromRaw(RawConfig config, long serverTimeout) {
        // TODO: Get trace from caller
        return JRTClientConfigRequestV3.createFromRaw(config, serverTimeout, Trace.createNew(), compressionType, getVespaVersion());
    }

    public static CompressionType getCompressionType() {
        return getCompressionType(System.getenv(VESPA_CONFIG_PROTOCOL_COMPRESSION),
                                  System.getProperty(VESPA_CONFIG_PROTOCOL_COMPRESSION));
    }

    static CompressionType getCompressionType(String env, String property) {
        return CompressionType.valueOf(ConfigUtils.getEnvValue("LZ4", env, property));
    }

    static Optional<VespaVersion> getVespaVersion() {
        return Optional.of(getCompiledVespaVersion());
    }

    static VespaVersion getCompiledVespaVersion() {
        return VespaVersion.fromString(String.format("%d.%d.%d",
                com.yahoo.vespa.config.VespaVersion.major,
                com.yahoo.vespa.config.VespaVersion.minor,
                com.yahoo.vespa.config.VespaVersion.micro));
    }
}
