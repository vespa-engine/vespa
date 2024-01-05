// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;
import com.yahoo.path.Path;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CloudDataPlaneFilterValidator implements Validator {

    private static final Logger log = Logger.getLogger(CloudDataPlaneFilterValidator.class.getName());

    @Override
    public void validate(Context context) {
        if (!context.deployState().isHosted()) return;
        if (!context.deployState().zone().system().isPublic()) return;

        validateUniqueCertificates(context);
    }

    private void validateUniqueCertificates(Context context) {
        List<NamedReader> certFiles = context.deployState().getApplicationPackage().getFiles(ApplicationPackage.SECURITY_DIR, ".pem");

        Map<String, List<X509Certificate>> configuredCertificates = certFiles.stream()
                .collect(Collectors.toMap(NamedReader::getName, reader -> readCertificates(context, reader)));

        Set<X509Certificate> duplicates = new HashSet<>();
        Set<X509Certificate> globalUniqueCerts = new HashSet<>();
        for (Map.Entry<String, List<X509Certificate>> certificateFile : configuredCertificates.entrySet()) {
            List<X509Certificate> duplicatesInFile = certificateFile.getValue().stream()
                    .filter(i -> !globalUniqueCerts.add(i))
                    .toList();
            duplicates.addAll(duplicatesInFile);
        }
        if (!duplicates.isEmpty()) {
            List<String> filesWithDuplicates = configuredCertificates.entrySet().stream()
                    .filter(entry -> entry.getValue().stream().anyMatch(duplicates::contains))
                    .map(Map.Entry::getKey)
                    .map(Path::fromString)
                    .map(Path::getName)
                    .map(p -> ApplicationPackage.SECURITY_DIR.append(p).getRelative())
                    .sorted()
                    .toList();
            context.illegal("Duplicate certificate(s) detected in files: %s. Certificate subject of duplicates: %s"
                                    .formatted(filesWithDuplicates.toString(),
                                               duplicates.stream().map(cert -> cert.getSubjectX500Principal().getName()).toList().toString()));
        }
    }

    private static List<X509Certificate> readCertificates(Context context, NamedReader reader) {
        try {
            return X509CertificateUtils.certificateListFromPem(IOUtils.readAll(reader));
        } catch (Exception e) {
            log.warning("Exception reading certificate list from application package. File: %s, exception message: %s"
                                .formatted(reader.getName(), e.getMessage()));
            context.illegal("Error reading certificates from application package", e);
            return List.of();
        }
    }

}
