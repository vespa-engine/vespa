// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A representation of the content of an application package.
 * Only the deployment.xml content can be accessed as anything other than compressed data.
 * A package is identified by a hash of the content.
 * 
 * This is immutable.
 * 
 * @author bratseth
 */
public class ApplicationPackage {

    private final String contentHash;
    private final byte[] zippedContent;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Optional<Version> compileVersion;
    private final Optional<Instant> buildTime;
    
    /** 
     * Creates an application package from its zipped content.
     * This <b>assigns ownership</b> of the given byte array to this class;
     * it must not be further changed by the caller.
     */
    public ApplicationPackage(byte[] zippedContent) {
        this.zippedContent = Objects.requireNonNull(zippedContent, "The application package content cannot be null");
        this.contentHash = Hashing.sha1().hashBytes(zippedContent).toString();

        Files files = Files.extract(Set.of("deployment.xml", "validation-overrides.xml", "build-meta.json"), zippedContent);
        this.deploymentSpec = files.getAsReader("deployment.xml").map(DeploymentSpec::fromXml).orElse(DeploymentSpec.empty);
        this.validationOverrides = files.getAsReader("validation-overrides.xml").map(ValidationOverrides::fromXml).orElse(ValidationOverrides.empty);
        Optional<Inspector> buildMetaObject = files.get("build-meta.json").map(SlimeUtils::jsonToSlime).map(Slime::get);
        this.compileVersion = buildMetaObject.map(object -> Version.fromString(object.field("compileVersion").asString()));
        this.buildTime = buildMetaObject.map(object -> Instant.ofEpochMilli(object.field("buildTime").asLong()));
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


    private static class Files {

        /** Max size of each extracted file */
        private static final int maxSize = 10 * 1024 * 1024; // 10 MiB

        // TODO: Vespa 8: Remove application/ directory support
        private static final String applicationDir = "application/";

        private final ImmutableMap<String, byte[]> files;

        private Files(ImmutableMap<String, byte[]> files) {
            this.files = files;
        }

        public static Files extract(Set<String> filesToExtract, byte[] zippedContent) {
            ImmutableMap.Builder<String, byte[]> builder = ImmutableMap.builder();
            try (ByteArrayInputStream stream = new ByteArrayInputStream(zippedContent)) {
                ZipStreamReader reader = new ZipStreamReader(stream,
                                                             (name) -> filesToExtract.contains(withoutLegacyDir(name)),
                                                             maxSize);
                for (ZipStreamReader.ZipEntryWithContent entry : reader.entries()) {
                    builder.put(withoutLegacyDir(entry.zipEntry().getName()), entry.content());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Exception reading application package", e);
            }
            return new Files(builder.build());
        }

        /** Get content of given file name */
        public Optional<byte[]> get(String name) {
            return Optional.ofNullable(files.get(name));
        }

        /** Get reader for the content of given file name */
        public Optional<Reader> getAsReader(String name) {
            return get(name).map(ByteArrayInputStream::new).map(InputStreamReader::new);
        }

        private static String withoutLegacyDir(String name) {
            if (name.startsWith(applicationDir)) return name.substring(applicationDir.length());
            return name;
        }

    }

}
