// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Utilities and common definitions of system properties defining a Vespa application project.
 *
 * @author jonmv
 */
public class Properties {

    /**
     * Returns the relevant application ID. This is the 'tenant', 'application' and 'instance' properties.
     * The instance defaults to the user name of the current user, if not explicitly set.
     */
    public static ApplicationId application() {
        return ApplicationId.from(requireNonBlankProperty("tenant"),
                                  requireNonBlankProperty("application"),
                                  requireNonBlankProperty("instance"));
    }

    /** Returns the relevant environment, if this is set with the 'environment' property */
    public static Optional<Environment> environment() {
        return getNonBlankProperty("environment").map(Environment::from);
    }

    /** Returns the relevant region, if this is set with the 'region' property */
    public static Optional<RegionName> region() {
        return getNonBlankProperty("region").map(RegionName::from);
    }

    /** Returns the URL of the API endpoint of the Vespa cloud. This must be set with the 'endpoint' property. */
    public static URI apiEndpoint() {
        return URI.create(requireNonBlankProperty("endpoint"));
    }

    /** Returns the path of the API private key. This must be set with the 'privateKeyFile' property. */
    public static Path apiKeyFile() {
        return Paths.get(requireNonBlankProperty("apiKeyFile"));
    }

    /** Returns the path of the API certificate, if this is set with the 'certificateFile' property. */
    public static Optional<Path> apiCertificateFile() {
        return getNonBlankProperty("apiCertificateFile").map(Paths::get);
    }

    /** Returns the actual private key as a string. */
    public static Optional<String> apiKey() {
        return getNonBlankProperty("apiKey");
    }

    /** Returns the path of the data plane certificate file, if this is set with the 'dataPlaneCertificateFile' property. */
    public static Optional<Path> dataPlaneCertificateFile() {
        return getNonBlankProperty("dataPlaneCertificateFile").map(Paths::get);
    }

    /** Returns the path of the data plane private key file, if this is set with the 'dataPlaneKeyFile' property. */
    public static Optional<Path> dataPlaneKeyFile() {
        return getNonBlankProperty("dataPlaneKeyFile").map(Paths::get);
    }

    /** Returns the user name of the current user. This is set with the 'user.name' property. */
    public static String user() {
        return System.getProperty("user.name");
    }

    /** Returns the system property with the given name if it is set, or empty. */
    public static Optional<String> getNonBlankProperty(String name) {
        return Optional.ofNullable(System.getProperty(name)).filter(value -> ! value.isBlank());
    }

    /** Returns the system property with the given name if it is set, or throws an IllegalStateException. */
    public static String requireNonBlankProperty(String name) {
        return getNonBlankProperty(name).orElseThrow(() -> new IllegalStateException("Missing required property '" + name + "'"));
    }

}
