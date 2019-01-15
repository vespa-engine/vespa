// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Utility class for retrieving {@link TransportSecurityOptions} from the system.
 *
 * @author bjorncs
 */
public class TransportSecurityUtils {

    public static final String CONFIG_FILE_ENVIRONMENT_VARIABLE = "VESPA_TLS_CONFIG_FILE";
    public static final String INSECURE_MIXED_MODE_ENVIRONMENT_VARIABLE = "VESPA_TLS_INSECURE_MIXED_MODE";
    public static final String INSECURE_AUTHORIZATION_MODE_ENVIRONMENT_VARIABLE = "VESPA_TLS_INSECURE_AUTHORIZATION_MODE";

    private TransportSecurityUtils() {}

    public static boolean isTransportSecurityEnabled() {
        return getConfigFile().isPresent();
    }

    public static MixedMode getInsecureMixedMode() {
        return getEnvironmentVariable(INSECURE_MIXED_MODE_ENVIRONMENT_VARIABLE)
                .map(MixedMode::fromConfigValue)
                .orElse(MixedMode.defaultValue());
    }

    public static AuthorizationMode getInsecureAuthorizationMode() {
        return getEnvironmentVariable(INSECURE_AUTHORIZATION_MODE_ENVIRONMENT_VARIABLE)
                .map(AuthorizationMode::fromConfigValue)
                .orElse(AuthorizationMode.defaultValue());
    }

    public static Optional<Path> getConfigFile() {
        return getEnvironmentVariable(CONFIG_FILE_ENVIRONMENT_VARIABLE).map(Paths::get);
    }

    public static Optional<TransportSecurityOptions> getOptions() {
        return getConfigFile()
                .map(TransportSecurityOptions::fromJsonFile);
    }

    private static Optional<String> getEnvironmentVariable(String environmentVariable) {
        return Optional.ofNullable(System.getenv(environmentVariable))
                .filter(var -> !var.isEmpty());
    }
}
