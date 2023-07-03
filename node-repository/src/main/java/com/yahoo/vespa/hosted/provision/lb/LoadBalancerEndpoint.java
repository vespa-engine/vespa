package com.yahoo.vespa.hosted.provision.lb;

import ai.vespa.validation.Validation;
import com.google.common.hash.Hashing;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * An endpoint for a load balancer, with a pre-generated ID. This ID may be included in the endpoint DNS name.
 *
 * @author mpolden
 */
public record LoadBalancerEndpoint(String id, AuthMethod authMethod) {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-f][a-f0-9]{7}$");

    public LoadBalancerEndpoint {
        Validation.requireMatch(id, "Endpoint ID", ID_PATTERN);
        Objects.requireNonNull(authMethod);
    }

    public static List<LoadBalancerEndpoint> createAll(LoadBalancerId loadBalancer, Environment environment, RegionName region) {
        SecureRandom random = new SecureRandom();
        return Arrays.stream(AuthMethod.values())
                     .map(method -> create(loadBalancer, environment, region, method, random))
                     .toList();
    }

    static LoadBalancerEndpoint create(LoadBalancerId loadBalancer, Environment environment, RegionName region, AuthMethod method, RandomGenerator random) {
        String hexLetters = "abcdef";
        StringBuilder id = new StringBuilder();
        id.append(hexLetters.charAt(random.nextInt(hexLetters.length()))); // First character must be a letter
        String hash = Hashing.sha256().newHasher()
                             .putString(loadBalancer.cluster().value(), StandardCharsets.UTF_8)
                             .putString(loadBalancer.application().serializedForm(), StandardCharsets.UTF_8)
                             .putString(environment.value(), StandardCharsets.UTF_8)
                             .putString(region.value(), StandardCharsets.UTF_8)
                             .putString(method.name(), StandardCharsets.UTF_8)
                             .putLong(random.nextLong()) // Salt
                             .hash()
                             .toString();
        id.append(hash, 0, 7);
        return new LoadBalancerEndpoint(id.toString(), method);
    }

    public enum AuthMethod {
        // Endpoint requires mTLS to authenticate
        mtls,
        // Endpoint requires a user-supplied token to authenticate
        token,
    }

}
