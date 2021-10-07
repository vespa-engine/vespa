// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.google.common.hash.Hashing;
import com.yahoo.component.Version;
import com.yahoo.config.application.FileSystemWrapper;
import com.yahoo.config.application.FileSystemWrapper.FileWrapper;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.deployment.ZipBuilder;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

/**
 * A representation of the content of an application package.
 * Only the deployment.xml content can be accessed as anything other than compressed data.
 * A package is identified by a hash of the content.
 * 
 * This is immutable.
 * 
 * @author bratseth
 * @author jonmv
 */
public class ApplicationPackage {

    private static final String trustedCertificatesFile = "security/clients.pem";
    private static final String buildMetaFile = "build-meta.json";
    private static final String deploymentFile = "deployment.xml";
    private static final String validationOverridesFile = "validation-overrides.xml";
    private static final String servicesFile = "services.xml";

    private final String contentHash;
    private final byte[] zippedContent;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final ZipArchiveCache files;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;
    private final List<X509Certificate> trustedCertificates;

    /**
     * Creates an application package from its zipped content.
     * This <b>assigns ownership</b> of the given byte array to this class;
     * it must not be further changed by the caller.
     */
    public ApplicationPackage(byte[] zippedContent) {
        this(zippedContent, false);
    }

    /**
     * Creates an application package from its zipped content.
     * This <b>assigns ownership</b> of the given byte array to this class;
     * it must not be further changed by the caller.
     * If 'requireFiles' is true, files needed by deployment orchestration must be present.
     */
    public ApplicationPackage(byte[] zippedContent, boolean requireFiles) {
        this.zippedContent = Objects.requireNonNull(zippedContent, "The application package content cannot be null");
        this.contentHash = Hashing.sha1().hashBytes(zippedContent).toString();
        this.files = new ZipArchiveCache(zippedContent, Set.of(deploymentFile, validationOverridesFile, servicesFile, buildMetaFile, trustedCertificatesFile));

        Optional<DeploymentSpec> deploymentSpec = files.get(deploymentFile).map(bytes -> new String(bytes, UTF_8)).map(DeploymentSpec::fromXml);
        if (requireFiles && deploymentSpec.isEmpty())
            throw new IllegalArgumentException("Missing required file '" + deploymentFile + "'");
        this.deploymentSpec = deploymentSpec.orElse(DeploymentSpec.empty);

        this.validationOverrides = files.get(validationOverridesFile).map(bytes -> new String(bytes, UTF_8)).map(ValidationOverrides::fromXml).orElse(ValidationOverrides.empty);

        Optional<Inspector> buildMetaObject = files.get(buildMetaFile).map(SlimeUtils::jsonToSlime).map(Slime::get);
        if (requireFiles && buildMetaObject.isEmpty())
            throw new IllegalArgumentException("Missing required file '" + buildMetaFile + "'");
        this.compileVersion = buildMetaObject.flatMap(object -> parse(object, "compileVersion", field -> Version.fromString(field.asString())));
        this.buildTime = buildMetaObject.flatMap(object -> parse(object, "buildTime", field -> Instant.ofEpochMilli(field.asLong())));

        this.trustedCertificates = files.get(trustedCertificatesFile).map(bytes -> X509CertificateUtils.certificateListFromPem(new String(bytes, UTF_8))).orElse(List.of());
    }

    /** Returns a copy of this with the given certificate appended. */
    public ApplicationPackage withTrustedCertificate(X509Certificate certificate) {
        List<X509Certificate> trustedCertificates = new ArrayList<>(this.trustedCertificates);
        trustedCertificates.add(certificate);
        byte[] certificatesBytes = X509CertificateUtils.toPem(trustedCertificates).getBytes(UTF_8);

        ByteArrayOutputStream modified = new ByteArrayOutputStream(zippedContent.length + certificatesBytes.length);
        ZipStreamReader.transferAndWrite(modified, new ByteArrayInputStream(zippedContent), trustedCertificatesFile, certificatesBytes);
        return new ApplicationPackage(modified.toByteArray());
    }

    /** Returns a hash of the content of this package */
    public String hash() { return contentHash; }
    
    /** Returns the content of this package. The content <b>must not</b> be modified. */
    public byte[] zippedContent() { return zippedContent; }

    /** 
     * Returns the deployment spec from the deployment.xml file of the package content.
     * This is the DeploymentSpec.empty instance if this package does not contain a deployment.xml file.
     */
    public DeploymentSpec deploymentSpec() { return deploymentSpec; }

    /**
     * Returns the validation overrides from the validation-overrides.xml file of the package content.
     * This is the ValidationOverrides.empty instance if this package does not contain a validation-overrides.xml file.
     */
    public ValidationOverrides validationOverrides() { return validationOverrides; }

    /** Returns the platform version which package was compiled against, if known. */
    public Optional<Version> compileVersion() { return compileVersion; }

    /** Returns the time this package was built, if known. */
    public Optional<Instant> buildTime() { return buildTime; }

    /** Returns the list of certificates trusted by this application, or an empty list if no trust configured. */
    public List<X509Certificate> trustedCertificates() {
        return trustedCertificates;
    }

    private static <Type> Optional<Type> parse(Inspector buildMetaObject, String fieldName, Function<Inspector, Type> mapper) {
        if ( ! buildMetaObject.field(fieldName).valid())
            throw new IllegalArgumentException("Missing value '" + fieldName + "' in '" + buildMetaFile + "'");
        try {
            return Optional.of(mapper.apply(buildMetaObject.field(fieldName)));
        }
        catch (RuntimeException e) {
            throw new IllegalArgumentException("Failed parsing \"" + fieldName + "\" in '" + buildMetaFile + "': " + Exceptions.toMessageString(e));
        }
    }

    /** Creates a valid application package that will remove all application's deployments */
    public static ApplicationPackage deploymentRemoval() {
        return new ApplicationPackage(filesZip(Map.of(validationOverridesFile, allValidationOverrides().xmlForm().getBytes(UTF_8),
                                                      deploymentFile, DeploymentSpec.empty.xmlForm().getBytes(UTF_8))));
    }

    /** Returns a zip containing meta data about deployments of this package by the given job. */
    public byte[] metaDataZip() {
        preProcessAndPopulateCache();
        return cacheZip();
    }

    private void preProcessAndPopulateCache() {
        FileWrapper servicesXml = files.wrapper().wrap(Paths.get(servicesFile));
        if (servicesXml.exists())
            try {
                new XmlPreProcessor(files.wrapper().wrap(Paths.get("./")),
                                    new InputStreamReader(new ByteArrayInputStream(servicesXml.content()), UTF_8),
                                    InstanceName.defaultName(),
                                    Environment.prod,
                                    RegionName.defaultName())
                        .run(); // Populates the zip archive cache with files that would be included.
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
    }

    private byte[] cacheZip() {
        return filesZip(files.cache.entrySet().stream()
                                   .filter(entry -> entry.getValue().isPresent())
                                   .collect(toMap(entry -> entry.getKey().toString(),
                                                  entry -> entry.getValue().get())));
    }

    static byte[] filesZip(Map<String, byte[]> files) {
        try (ZipBuilder zipBuilder = new ZipBuilder(files.values().stream().mapToInt(bytes -> bytes.length).sum() + 512)) {
            files.forEach(zipBuilder::add);
            zipBuilder.close();
            return zipBuilder.toByteArray();
        }
    }

    private static ValidationOverrides allValidationOverrides() {
        String until = DateTimeFormatter.ISO_LOCAL_DATE.format(Instant.now().plus(Duration.ofDays(25)).atZone(ZoneOffset.UTC));
        StringBuilder validationOverridesContents = new StringBuilder(1000);
        validationOverridesContents.append("<validation-overrides version=\"1.0\">\n");
        for (ValidationId validationId: ValidationId.values())
            validationOverridesContents.append("\t<allow until=\"").append(until).append("\">").append(validationId.value()).append("</allow>\n");
        validationOverridesContents.append("</validation-overrides>\n");

        return ValidationOverrides.fromXml(validationOverridesContents.toString());
    }


    /** Maps normalized paths to cached content read from a zip archive. */
    private static class ZipArchiveCache {

        /** Max size of each extracted file */
        private static final int maxSize = 10 << 20; // 10 Mb

        // TODO: Vespa 8: Remove application/ directory support
        private static final String applicationDir = "application/";

        private static String withoutLegacyDir(String name) {
            if (name.startsWith(applicationDir)) return name.substring(applicationDir.length());
            return name;
        }

        private final byte[] zip;
        private final Map<Path, Optional<byte[]>> cache;

        public ZipArchiveCache(byte[] zip, Collection<String> prePopulated) {
            this.zip = zip;
            this.cache = new ConcurrentSkipListMap<>();
            this.cache.putAll(read(prePopulated));
        }

        public Optional<byte[]> get(String path) {
            return get(Paths.get(path));
        }

        public Optional<byte[]> get(Path path) {
            return cache.computeIfAbsent(path.normalize(), read(List.of(path.normalize().toString()))::get);
        }

        public FileSystemWrapper wrapper() {
            return FileSystemWrapper.ofFiles(path -> get(path).isPresent(), // Assume content asked for will also be read ...
                                             path -> get(path).orElseThrow(() -> new NoSuchFileException(path.toString())));
        }

        private Map<Path, Optional<byte[]>> read(Collection<String> names) {
            var entries = new ZipStreamReader(new ByteArrayInputStream(zip),
                                              name -> names.contains(withoutLegacyDir(name)),
                                              maxSize,
                                              true)
                    .entries().stream()
                    .collect(toMap(entry -> Paths.get(withoutLegacyDir(entry.zipEntry().getName())).normalize(),
                             ZipStreamReader.ZipEntryWithContent::content));
            names.stream().map(Paths::get).forEach(path -> entries.putIfAbsent(path.normalize(), Optional.empty()));
            return entries;
        }

    }

}
