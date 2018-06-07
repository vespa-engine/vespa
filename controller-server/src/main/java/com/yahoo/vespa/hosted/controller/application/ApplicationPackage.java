// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;

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
    
    /** 
     * Creates an application package from its zipped content.
     * This <b>assigns ownership</b> of the given byte array to this class:
     * it must not be further changed by the caller.
     */
    public ApplicationPackage(byte[] zippedContent) {
        this.zippedContent = Objects.requireNonNull(zippedContent, "The application package content cannot be null");
        this.contentHash = DigestUtils.shaHex(zippedContent);
        this.deploymentSpec = extractFile("deployment.xml", zippedContent).map(DeploymentSpec::fromXml).orElse(DeploymentSpec.empty);
        this.validationOverrides = extractFile("validation-overrides.xml", zippedContent).map(ValidationOverrides::fromXml).orElse(ValidationOverrides.empty);
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

    private static Optional<Reader> extractFile(String fileName, byte[] zippedContent) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(zippedContent)) {
            ZipStreamReader reader = new ZipStreamReader(stream);
            for (ZipStreamReader.ZipEntryWithContent entry : reader.entries())
                if (entry.zipEntry().getName().equals(fileName) || entry.zipEntry().getName().equals("application/" + fileName)) // TODO: Remove application/ directory support
                    return Optional.of(new InputStreamReader(new ByteArrayInputStream(entry.content())));
            return Optional.empty();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Exception reading application package", e);
        }
    }

}
