// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for retrieving {@link TransportSecurityOptions} from the system.
 *
 * @author bjorncs
 */
public class TransportSecurityUtils {

    private static ConfigFileBasedTlsContext systemTlsContext;

    public static final String CONFIG_FILE_ENVIRONMENT_VARIABLE = "VESPA_TLS_CONFIG_FILE";
    public static final String INSECURE_MIXED_MODE_ENVIRONMENT_VARIABLE = "VESPA_TLS_INSECURE_MIXED_MODE";
    public static final String INSECURE_AUTHORIZATION_MODE_ENVIRONMENT_VARIABLE = "VESPA_TLS_INSECURE_AUTHORIZATION_MODE";

    private TransportSecurityUtils() {}

    public static boolean isTransportSecurityEnabled() {
        return isTransportSecurityEnabled(System.getenv());
    }

    public static boolean isTransportSecurityEnabled(Map<String, String> envVariables) {
        return getConfigFile(envVariables).isPresent();
    }

    public static MixedMode getInsecureMixedMode() {
        return getInsecureMixedMode(System.getenv());
    }

    public static MixedMode getInsecureMixedMode(Map<String, String> envVariables) {
        return getEnvironmentVariable(envVariables, INSECURE_MIXED_MODE_ENVIRONMENT_VARIABLE)
                .map(MixedMode::fromConfigValue)
                .orElse(MixedMode.defaultValue());
    }

    public static AuthorizationMode getInsecureAuthorizationMode() {
        return getInsecureAuthorizationMode(System.getenv());
    }

    public static AuthorizationMode getInsecureAuthorizationMode(Map<String, String> envVariables) {
        return getEnvironmentVariable(envVariables, INSECURE_AUTHORIZATION_MODE_ENVIRONMENT_VARIABLE)
                .map(AuthorizationMode::fromConfigValue)
                .orElse(AuthorizationMode.defaultValue());
    }

    public static Optional<Path> getConfigFile() {
        return getConfigFile(System.getenv());
    }

    public static Optional<Path> getConfigFile(Map<String, String> envVariables) {
        return getEnvironmentVariable(envVariables, CONFIG_FILE_ENVIRONMENT_VARIABLE).map(Paths::get);
    }

    public static Optional<TransportSecurityOptions> getOptions() {
        return getOptions(System.getenv());
    }

    public static Optional<TransportSecurityOptions> getOptions(Map<String, String> envVariables) {
        return getConfigFile(envVariables)
                .map(TransportSecurityOptions::fromJsonFile);
    }

    /**
     * @return The shared {@link TlsContext} for the Vespa system environment
     */
    public static Optional<TlsContext> getSystemTlsContext() {
        synchronized (TransportSecurityUtils.class) {
            Path configFile = getConfigFile().orElse(null);
            if (configFile == null) return Optional.empty();
            if (systemTlsContext == null) {
                systemTlsContext = new SystemTlsContext(configFile);
            }
            return Optional.of(systemTlsContext);
        }
    }

    private static Optional<String> getEnvironmentVariable(Map<String, String> environmentVariables, String variableName) {
        return Optional.ofNullable(environmentVariables.get(variableName))
                .filter(var -> !var.isEmpty());
    }

    private static class SystemTlsContext extends ConfigFileBasedTlsContext {
        SystemTlsContext(Path tlsOptionsConfigFile) {
            super(tlsOptionsConfigFile, getInsecureAuthorizationMode());
        }

        @Override public void close() { throw new UnsupportedOperationException("Shared TLS context cannot be closed"); }
    }
}
