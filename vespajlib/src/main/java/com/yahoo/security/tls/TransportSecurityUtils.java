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

    private TransportSecurityUtils() {}

    public static Optional<Path> getConfigFile() {
        return Optional.ofNullable(System.getenv(CONFIG_FILE_ENVIRONMENT_VARIABLE))
                .filter(var -> !var.isEmpty())
                .map(Paths::get);
    }

    public static Optional<TransportSecurityOptions> getOptions() {
        return getConfigFile()
                .map(TransportSecurityOptions::fromJsonFile);
    }
}
