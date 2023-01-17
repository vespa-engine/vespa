// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils;

import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * Misc utility methods for SIA provided credentials
 *
 * @author bjorncs
 */
public class SiaUtils {
    public static final Path DEFAULT_SIA_DIRECTORY = Paths.get("/var/lib/sia");

    private SiaUtils() {}

    public static Path getPrivateKeyFile(AthenzIdentity service) {
        return getPrivateKeyFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Path getPrivateKeyFile(Path root, AthenzIdentity service) {
        return root
                .resolve("keys")
                .resolve(String.format("%s.%s.key.pem", service.getDomainName(), service.getName()));
    }

    public static Path getCertificateFile(AthenzIdentity service) {
        return getCertificateFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Path getCertificateFile(Path root, AthenzIdentity service) {
        return root
                .resolve("certs")
                .resolve(String.format("%s.%s.cert.pem", service.getDomainName(), service.getName()));
    }

    public static Path getCaCertificatesFile() {
        return getCaCertificatesFile(DEFAULT_SIA_DIRECTORY);
    }

    public static Path getCaCertificatesFile(Path root) {
        return root.resolve("certs").resolve("ca.cert.pem");
    }

    public static Optional<PrivateKey> readPrivateKeyFile(AthenzIdentity service) {
        return readPrivateKeyFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Optional<PrivateKey> readPrivateKeyFile(Path root, AthenzIdentity service) {
        try {
            Path privateKeyFile = getPrivateKeyFile(root, service);
            if (Files.notExists(privateKeyFile)) return Optional.empty();
            return Optional.of(KeyUtils.fromPemEncodedPrivateKey(new String(Files.readAllBytes(privateKeyFile))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Optional<X509Certificate> readCertificateFile(AthenzIdentity service) {
        return readCertificateFile(DEFAULT_SIA_DIRECTORY, service);
    }

    public static Optional<X509Certificate> readCertificateFile(Path root, AthenzIdentity service) {
        try {
            Path certificateFile = getCertificateFile(root, service);
            if (Files.notExists(certificateFile)) return Optional.empty();
            return Optional.of(X509CertificateUtils.fromPem(new String(Files.readAllBytes(certificateFile))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writePrivateKeyFile(AthenzIdentity service, PrivateKey privateKey) {
        writePrivateKeyFile(DEFAULT_SIA_DIRECTORY, service, privateKey);
    }

    public static void writePrivateKeyFile(Path root, AthenzIdentity service, PrivateKey privateKey) {
        try {
            Path privateKeyFile = getPrivateKeyFile(root, service);
            Files.createDirectories(privateKeyFile.getParent());
            Path tempFile = toTempFile(privateKeyFile);
            Files.write(tempFile, KeyUtils.toPem(privateKey).getBytes());
            Files.move(tempFile, privateKeyFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeCertificateFile(AthenzIdentity service, X509Certificate certificate) {
        writeCertificateFile(DEFAULT_SIA_DIRECTORY, service, certificate);
    }

    public static void writeCertificateFile(Path root, AthenzIdentity service, X509Certificate certificate) {
        try {
            Path certificateFile = getCertificateFile(root, service);
            Files.createDirectories(certificateFile.getParent());
            Path tempFile = toTempFile(certificateFile);
            Files.write(tempFile, X509CertificateUtils.toPem(certificate).getBytes());
            Files.move(tempFile, certificateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<AthenzIdentity> findSiaServices() {
        return findSiaServices(DEFAULT_SIA_DIRECTORY);
    }

    public static List<AthenzIdentity> findSiaServices(Path root) {
        String keyFileSuffix = ".key.pem";
        Path keysDirectory = root.resolve("keys");
        if ( ! Files.exists(keysDirectory))
            return List.of();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(keysDirectory)) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                    .map(path -> path.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(keyFileSuffix))
                    .map(fileName -> fileName.substring(0, fileName.length() - keyFileSuffix.length()))
                    .map(AthenzService::new)
                    .collect(toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path toTempFile(Path file) {
        return file.getParent().resolve(file.getFileName().toString() + ".tmp");
    }
}
