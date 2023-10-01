package com.yahoo.vespa.hosted.controller.application;

import ai.vespa.validation.Validation;
import com.yahoo.config.provision.zone.AuthMethod;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * A system-generated endpoint, where the cluster and application parts are randomly generated. These become the
 * first and second part of an endpoint name. See {@link Endpoint}.
 *
 * @author mpolden
 */
public record GeneratedEndpoint(String clusterPart, String applicationPart, AuthMethod authMethod, Optional<EndpointId> endpoint) {

    private static final Pattern PART_PATTERN = Pattern.compile("^[a-f][a-f0-9]{7}$");

    public GeneratedEndpoint {
        Objects.requireNonNull(clusterPart);
        Objects.requireNonNull(applicationPart);
        Objects.requireNonNull(authMethod);
        Objects.requireNonNull(endpoint);

        Validation.requireMatch(clusterPart, "Cluster part", PART_PATTERN);
        Validation.requireMatch(applicationPart, "Application part", PART_PATTERN);
    }

    /** Returns whether this was generated for an endpoint declared in {@link com.yahoo.config.application.api.DeploymentSpec} */
    public boolean declared() {
        return endpoint.isPresent();
    }

    /** Returns whether this was generated for a cluster declared in {@link com.yahoo.vespa.hosted.controller.application.pkg.BasicServicesXml} */
    public boolean cluster() {
        return !declared();
    }

    /** Returns a copy of this with cluster part set to given value */
    public GeneratedEndpoint withClusterPart(String clusterPart) {
        return new GeneratedEndpoint(clusterPart, applicationPart, authMethod, endpoint);
    }

    /** Create a new endpoint part, using random as a source of randomness */
    public static String createPart(RandomGenerator random) {
        String alphabet = "abcdef0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(alphabet.charAt(random.nextInt(6))); // Start with letter
        for (int i = 0; i < 7; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

}
