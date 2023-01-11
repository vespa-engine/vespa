// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application.pkg;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.yahoo.component.Version;
import com.yahoo.vespa.archive.ArchiveStreamReader;
import com.yahoo.vespa.archive.ArchiveStreamReader.ArchiveFile;
import com.yahoo.vespa.archive.ArchiveStreamReader.Options;
import com.yahoo.config.application.FileSystemWrapper;
import com.yahoo.config.application.FileSystemWrapper.FileWrapper;
import com.yahoo.config.application.XmlPreProcessor;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Tags;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.deployment.ZipBuilder;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.yahoo.slime.Type.NIX;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

/**
 * A representation of the content of an application package.
 * Only meta-data content can be accessed as anything other than compressed data.
 * A package is identified by a hash of the content.
 *
 * @author bratseth
 * @author jonmv
 */
public class ApplicationPackage {

    static final String trustedCertificatesFile = "security/clients.pem";
    static final String buildMetaFile = "build-meta.json";
    static final String deploymentFile = "deployment.xml";
    static final String validationOverridesFile = "validation-overrides.xml";
    static final String servicesFile = "services.xml";
    static final Set<String> prePopulated = Set.of(deploymentFile, validationOverridesFile, servicesFile, buildMetaFile, trustedCertificatesFile);

    private static Hasher hasher() { return Hashing.murmur3_128().newHasher(); }

    private final String bundleHash;
    private final byte[] zippedContent;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final ZipArchiveCache files;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;
    private final Optional<Version> parentVersion;

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
        this.files = new ZipArchiveCache(zippedContent, prePopulated);

        Optional<DeploymentSpec> deploymentSpec = files.get(deploymentFile).map(bytes -> new String(bytes, UTF_8)).map(DeploymentSpec::fromXml);
        if (requireFiles && deploymentSpec.isEmpty())
            throw new IllegalArgumentException("Missing required file '" + deploymentFile + "'");
        this.deploymentSpec = deploymentSpec.orElse(DeploymentSpec.empty);

        this.validationOverrides = files.get(validationOverridesFile).map(bytes -> new String(bytes, UTF_8)).map(ValidationOverrides::fromXml).orElse(ValidationOverrides.empty);

        Optional<Inspector> buildMetaObject = files.get(buildMetaFile).map(SlimeUtils::jsonToSlime).map(Slime::get);
        this.compileVersion = buildMetaObject.flatMap(object -> parse(object, "compileVersion", field -> Version.fromString(field.asString())));
        this.buildTime = buildMetaObject.flatMap(object -> parse(object, "buildTime", field -> Instant.ofEpochMilli(field.asLong())));
        this.parentVersion = buildMetaObject.flatMap(object -> parse(object, "parentVersion", field -> Version.fromString(field.asString())));

        this.bundleHash = calculateBundleHash(zippedContent);

        preProcessAndPopulateCache();
    }

    /** Hash of all files and settings that influence what is deployed to config servers. */
    public String bundleHash() {
        return bundleHash;
    }
    
    /** Returns the content of this package. The content <b>must not</b> be modified. */
    public byte[] zippedContent() { return zippedContent; }

    /** 
     * Returns the deployment spec from the deployment.xml file of the package content.<br>
     * This is the DeploymentSpec.empty instance if this package does not contain a deployment.xml file.<br>
     * <em>NB: <strong>Always</strong> read deployment spec from the {@link Application}, for deployment orchestration.</em>
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

    /** Returns the parent version used to compile the package, if known. */
    public Optional<Version> parentVersion() { return parentVersion; }

    private static <Type> Optional<Type> parse(Inspector buildMetaObject, String fieldName, Function<Inspector, Type> mapper) {
        Inspector field = buildMetaObject.field(fieldName);
        if ( ! field.valid() || field.type() == NIX)
            return Optional.empty();
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
                                    RegionName.defaultName(),
                                    Tags.empty())
                        .run(); // Populates the zip archive cache with files that would be included.
            }
            catch (IllegalArgumentException e) {
                throw e;
            }
            catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
    }

    private byte[] cacheZip() {
        return filesZip(files.cache.entrySet().stream()
                                   .filter(entry -> entry.getValue().isPresent())
                                   .collect(toMap(entry -> entry.getKey().toString(),
                                                  entry -> entry.getValue().get())));
    }

    public static byte[] filesZip(Map<String, byte[]> files) {
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

    // Hashes all files and settings that require a deployment to be forwarded to configservers
    private String calculateBundleHash(byte[] zippedContent) {
        Predicate<String> entryMatcher = name -> ! name.endsWith(deploymentFile) && ! name.endsWith(buildMetaFile);
        Options options = Options.standard().pathPredicate(entryMatcher);
        HashingOutputStream hashOut = new HashingOutputStream(Hashing.murmur3_128(-1), OutputStream.nullOutputStream());
        ArchiveFile file;
        try (ArchiveStreamReader reader = ArchiveStreamReader.ofZip(new ByteArrayInputStream(zippedContent), options)) {
            while ((file = reader.readNextTo(hashOut)) != null) {
                hashOut.write(file.path().toString().getBytes(UTF_8));
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hasher().putLong(hashOut.hash().asLong())
                       .putInt(deploymentSpec.deployableHashCode())
                       .hash().toString();
    }

    public static String calculateHash(byte[] bytes) {
        return hasher().putBytes(bytes)
                       .hash().toString();
    }


    /** Maps normalized paths to cached content read from a zip archive. */
    private static class ZipArchiveCache {

        /** Max size of each extracted file */
        private static final int maxSize = 10 << 20; // 10 Mb

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
            return FileSystemWrapper.ofFiles(Path.of("./"), // zip archive root
                                             path -> get(path).isPresent(), // Assume content asked for will also be read ...
                                             path -> get(path).orElseThrow(() -> new NoSuchFileException(path.toString())));
        }

        private Map<Path, Optional<byte[]>> read(Collection<String> names) {
            var entries = ZipEntries.from(zip,
                                          names::contains,
                                          maxSize,
                                          true)
                                    .asList().stream()
                                    .collect(toMap(entry -> Paths.get(entry.name()).normalize(),
                                                   ZipEntries.ZipEntryWithContent::content));
            names.stream().map(Paths::get).forEach(path -> entries.putIfAbsent(path.normalize(), Optional.empty()));
            return entries;
        }

    }

}
