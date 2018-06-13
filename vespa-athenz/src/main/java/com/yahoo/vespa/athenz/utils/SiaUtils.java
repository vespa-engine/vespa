// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.athenz.tls.KeyUtils;
import com.yahoo.vespa.athenz.tls.X509CertificateUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Optional;

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

    public static Optional<PrivateKey> readPrivateKeyFile(AthenzService service) {
        return readPrivateKeyFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Optional<PrivateKey> readPrivateKeyFile(Path root, AthenzService service) {
        try {
            Path privateKeyFile = getPrivateKeyFile(root, service);
            if (Files.notExists(privateKeyFile)) return Optional.empty();
            return Optional.of(KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<X509Certificate> readCertificateFile(AthenzService service) {
        return readCertificateFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Optional<X509Certificate> readCertificateFile(Path root, AthenzService service) {
        try {
            Path certificateFile = getCertificateFile(root, service);
            if (Files.notExists(certificateFile)) return Optional.empty();
            return Optional.of(X509CertificateUtils.fromPem(new String(Files.readAllBytes(certificateFile))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writePrivateKeyFile(AthenzService service, PrivateKey privateKey) {
        writePrivateKeyFile(DEFAULT_SIA_DIRECTORY, service, privateKey);
    }

    public static void writePrivateKeyFile(Path root, AthenzService service, PrivateKey privateKey) {
        try {
            Path privateKeyFile = getPrivateKeyFile(root, service);
            Files.createDirectories(privateKeyFile.getParent());
            Path tempFile = toTempFile(privateKeyFile);
            Files.write(tempFile, KeyUtils.toPem(privateKey).getBytes());
            Files.move(tempFile, privateKeyFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeCertificateFile(AthenzService service, X509Certificate certificate) {
        writeCertificateFile(DEFAULT_SIA_DIRECTORY, service, certificate);
    }

    public static void writeCertificateFile(Path root, AthenzService service, X509Certificate certificate) {
        try {
            Path certificateFile = getCertificateFile(root, service);
            Files.createDirectories(certificateFile.getParent());
            Path tempFile = toTempFile(certificateFile);
            Files.write(tempFile, X509CertificateUtils.toPem(certificate).getBytes());
            Files.move(tempFile, certificateFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path toTempFile(Path file) {
        return Paths.get(file.toAbsolutePath().toString() + ".tmp");
    }

}
