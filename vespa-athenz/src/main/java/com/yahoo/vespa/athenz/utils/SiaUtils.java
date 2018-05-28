// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzService;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Misc utility methods for SIA provided credentials
 *
 * @author bjorncs
 */
public class SiaUtils {
    public static final Path DEFAULT_SIA_DIRECTORY = Paths.get("/var/lib/sia");

    private SiaUtils() {}

    public static Path getPrivateKeyFile(AthenzService service) {
        return getPrivateKeyFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Path getPrivateKeyFile(Path root, AthenzService service) {
        return root
                .resolve("keys")
                .resolve(String.format("%s.%s.key.pem", service.getDomainName(), service.getName()));
    }

    public static Path getCertificateFile(AthenzService service) {
        return getCertificateFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Path getCertificateFile(Path root, AthenzService service) {
        return root
                .resolve("certs")
                .resolve(String.format("%s.%s.cert.pem", service.getDomainName(), service.getName()));
    }

}
