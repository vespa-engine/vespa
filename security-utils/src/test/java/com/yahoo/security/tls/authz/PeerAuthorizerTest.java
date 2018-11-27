// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls.authz;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.policy.AuthorizedPeers;
import com.yahoo.security.tls.policy.HostGlobPattern;
import com.yahoo.security.tls.policy.PeerPolicy;
import com.yahoo.security.tls.policy.RequiredPeerCredential;
import com.yahoo.security.tls.policy.RequiredPeerCredential.Field;
import com.yahoo.security.tls.policy.Role;
import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.tls.policy.RequiredPeerCredential.Field.CN;
import static com.yahoo.security.tls.policy.RequiredPeerCredential.Field.SAN_DNS;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class PeerAuthorizerTest {

    private static final KeyPair KEY_PAIR = KeyUtils.generateKeypair(KeyAlgorithm.EC);
    private static final String ROLE_1 = "role-1", ROLE_2 = "role-2", ROLE_3 = "role-3", POLICY_1 = "policy-1", POLICY_2 = "policy-2";

    @Test
    public void certificate_must_match_both_san_and_cn_pattern() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanRequirement = createRequiredCredential(SAN_DNS, "*.matching.san");
        PeerAuthorizer authorizer = createPeerAuthorizer(createPolicy(POLICY_1, createRoles(ROLE_1), cnRequirement, sanRequirement));

        AuthorizationResult result = authorizer.authorizePeer(createCertificate("foo.matching.cn", "foo.matching.san", "foo.invalid.san"));
        assertAuthorized(result);
        assertThat(result.assumedRoles()).extracting(Role::name).containsOnly(ROLE_1);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_1);

        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.invalid.cn", "foo.matching.san")));
        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.invalid.cn", "foo.matching.san", "foo.invalid.san")));
        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.matching.cn", "foo.invalid.san")));
    }

    @Test
    public void can_match_multiple_policies() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanRequirement = createRequiredCredential(SAN_DNS, "*.matching.san");

        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, createRoles(ROLE_1, ROLE_2), cnRequirement, sanRequirement),
                createPolicy(POLICY_2, createRoles(ROLE_2, ROLE_3), cnRequirement, sanRequirement));

        AuthorizationResult result = peerAuthorizer
                .authorizePeer(createCertificate("foo.matching.cn", "foo.matching.san"));
        assertAuthorized(result);
        assertThat(result.assumedRoles()).extracting(Role::name).containsOnly(ROLE_1, ROLE_2, ROLE_3);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_1, POLICY_2);
    }

    @Test
    public void can_match_subset_of_policies() {
        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, createRoles(ROLE_1), createRequiredCredential(CN, "*.matching.cn")),
                createPolicy(POLICY_2, createRoles(ROLE_1, ROLE_2), createRequiredCredential(SAN_DNS, "*.matching.san")));

        AuthorizationResult result = peerAuthorizer.authorizePeer(createCertificate("foo.invalid.cn", "foo.matching.san"));
        assertAuthorized(result);
        assertThat(result.assumedRoles()).extracting(Role::name).containsOnly(ROLE_1, ROLE_2);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_2);
    }

    @Test
    public void must_match_all_cn_and_san_patterns() {
        RequiredPeerCredential cnSuffixRequirement = createRequiredCredential(CN, "*.*.matching.suffix.cn");
        RequiredPeerCredential cnPrefixRequirement = createRequiredCredential(CN, "matching.prefix.*.*.*");
        RequiredPeerCredential sanPrefixRequirement = createRequiredCredential(SAN_DNS, "*.*.matching.suffix.san");
        RequiredPeerCredential sanSuffixRequirement = createRequiredCredential(SAN_DNS, "matching.prefix.*.*.*");
        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, emptySet(), cnSuffixRequirement, cnPrefixRequirement, sanPrefixRequirement, sanSuffixRequirement));

        assertAuthorized(peerAuthorizer.authorizePeer(createCertificate("matching.prefix.matching.suffix.cn", "matching.prefix.matching.suffix.san")));
        assertUnauthorized(peerAuthorizer.authorizePeer(createCertificate("matching.prefix.matching.suffix.cn", "matching.prefix.invalid.suffix.san")));
        assertUnauthorized(peerAuthorizer.authorizePeer(createCertificate("invalid.prefix.matching.suffix.cn", "matching.prefix.matching.suffix.san")));
    }

    private static X509Certificate createCertificate(String subjectCn, String... sanCns) {
        X509CertificateBuilder builder =
                X509CertificateBuilder.fromKeypair(
                        KEY_PAIR,
                        new X500Principal("CN=" + subjectCn),
                        Instant.EPOCH,
                        Instant.EPOCH.plus(100000, ChronoUnit.DAYS),
                        SHA256_WITH_ECDSA,
                        BigInteger.ONE);
        for (String sanCn : sanCns) {
            builder.addSubjectAlternativeName(sanCn);
        }
        return builder.build();
    }

    private static RequiredPeerCredential createRequiredCredential(Field field, String pattern) {
        return new RequiredPeerCredential(field, new HostGlobPattern(pattern));
    }

    private static Set<Role> createRoles(String... roleNames) {
        return Arrays.stream(roleNames).map(Role::new).collect(toSet());
    }

    private static PeerAuthorizer createPeerAuthorizer(PeerPolicy... policies) {
        return new PeerAuthorizer(new AuthorizedPeers(Arrays.stream(policies).collect(toSet())));
    }

    private static PeerPolicy createPolicy(String name, Set<Role> roles, RequiredPeerCredential... requiredCredentials) {
        return new PeerPolicy(name, roles, Arrays.asList(requiredCredentials));
    }

    private static void assertAuthorized(AuthorizationResult result) {
        assertTrue(result.succeeded());
    }

    private static void assertUnauthorized(AuthorizationResult result) {
        assertFalse(result.succeeded());
    }

}
