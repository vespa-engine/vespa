// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SubjectAlternativeName.Type;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.RequiredPeerCredential.Field;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.security.tls.RequiredPeerCredential.Field.CN;
import static com.yahoo.security.tls.RequiredPeerCredential.Field.SAN_DNS;
import static com.yahoo.security.tls.RequiredPeerCredential.Field.SAN_URI;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class PeerAuthorizerTest {

    private static final KeyPair KEY_PAIR = KeyUtils.generateKeypair(KeyAlgorithm.EC);
    private static final String POLICY_1 = "policy-1", POLICY_2 = "policy-2";

    @Test
    void certificate_must_match_both_san_and_cn_pattern() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanRequirement = createRequiredCredential(SAN_DNS, "*.matching.san");
        PeerAuthorizer authorizer = createPeerAuthorizer(createPolicy(POLICY_1, cnRequirement, sanRequirement));

        ConnectionAuthContext result = authorizer.authorizePeer(createCertificate("foo.matching.cn", asList("foo.matching.san", "foo.invalid.san"), emptyList()));
        assertAuthorized(result);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_1);

        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.invalid.cn", singletonList("foo.matching.san"), emptyList())));
        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.invalid.cn", asList("foo.matching.san", "foo.invalid.san"), emptyList())));
        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.matching.cn", singletonList("foo.invalid.san"), emptyList())));
    }

    @Test
    void can_match_multiple_policies() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanRequirement = createRequiredCredential(SAN_DNS, "*.matching.san");

        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, cnRequirement, sanRequirement),
                createPolicy(POLICY_2, cnRequirement, sanRequirement));

        ConnectionAuthContext result = peerAuthorizer
                .authorizePeer(createCertificate("foo.matching.cn", singletonList("foo.matching.san"), emptyList()));
        assertAuthorized(result);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_1, POLICY_2);
    }

    @Test
    void can_match_subset_of_policies() {
        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, createRequiredCredential(CN, "*.matching.cn")),
                createPolicy(POLICY_2, createRequiredCredential(SAN_DNS, "*.matching.san")));

        ConnectionAuthContext result = peerAuthorizer.authorizePeer(createCertificate("foo.invalid.cn", singletonList("foo.matching.san"), emptyList()));
        assertAuthorized(result);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_2);
    }

    @Test
    void must_match_all_cn_and_san_patterns() {
        RequiredPeerCredential cnSuffixRequirement = createRequiredCredential(CN, "*.*.matching.suffix.cn");
        RequiredPeerCredential cnPrefixRequirement = createRequiredCredential(CN, "matching.prefix.*.*.*");
        RequiredPeerCredential sanPrefixRequirement = createRequiredCredential(SAN_DNS, "*.*.matching.suffix.san");
        RequiredPeerCredential sanSuffixRequirement = createRequiredCredential(SAN_DNS, "matching.prefix.*.*.*");
        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, cnSuffixRequirement, cnPrefixRequirement, sanPrefixRequirement, sanSuffixRequirement));

        assertAuthorized(peerAuthorizer.authorizePeer(createCertificate("matching.prefix.matching.suffix.cn", singletonList("matching.prefix.matching.suffix.san"), emptyList())));
        assertUnauthorized(peerAuthorizer.authorizePeer(createCertificate("matching.prefix.matching.suffix.cn", singletonList("matching.prefix.invalid.suffix.san"), emptyList())));
        assertUnauthorized(peerAuthorizer.authorizePeer(createCertificate("invalid.prefix.matching.suffix.cn", singletonList("matching.prefix.matching.suffix.san"), emptyList())));
    }

    @Test
    void can_match_policy_with_san_uri_pattern() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanUriRequirement = createRequiredCredential(SAN_URI, "myscheme://my/*/uri");
        PeerAuthorizer authorizer = createPeerAuthorizer(createPolicy(POLICY_1, cnRequirement, sanUriRequirement));

        ConnectionAuthContext result = authorizer.authorizePeer(createCertificate("foo.matching.cn", singletonList("foo.irrelevant.san"), singletonList("myscheme://my/matching/uri")));
        assertAuthorized(result);
        assertThat(result.matchedPolicies()).containsOnly(POLICY_1);

        assertUnauthorized(authorizer.authorizePeer(createCertificate("foo.matching.cn", emptyList(), singletonList("myscheme://my/nonmatching/url"))));
    }

    @Test
    void auth_context_contains_union_of_granted_capabilities_from_policies() {
        RequiredPeerCredential cnRequirement = createRequiredCredential(CN, "*.matching.cn");
        RequiredPeerCredential sanRequirement = createRequiredCredential(SAN_DNS, "*.matching.san");

        PeerAuthorizer peerAuthorizer = createPeerAuthorizer(
                createPolicy(POLICY_1, List.of(Capability.SLOBROK__API, Capability.CONTENT__DOCUMENT_API), List.of(cnRequirement)),
                createPolicy(POLICY_2, List.of(Capability.SLOBROK__API, Capability.CONTENT__SEARCH_API), List.of(sanRequirement)));

        var result = peerAuthorizer
                .authorizePeer(createCertificate("foo.matching.cn", List.of("foo.matching.san"), List.of()));
        assertAuthorized(result);
        assertCapabiltiesGranted(result, Set.of(Capability.SLOBROK__API, Capability.CONTENT__DOCUMENT_API, Capability.CONTENT__SEARCH_API));
    }

    private static X509Certificate createCertificate(String subjectCn, List<String> sanDns, List<String> sanUri) {
        X509CertificateBuilder builder =
                X509CertificateBuilder.fromKeypair(
                        KEY_PAIR,
                        new X500Principal("CN=" + subjectCn),
                        Instant.EPOCH,
                        Instant.EPOCH.plus(100000, ChronoUnit.DAYS),
                        SHA256_WITH_ECDSA,
                        BigInteger.ONE);
        sanDns.forEach(san -> builder.addSubjectAlternativeName(Type.DNS, san));
        sanUri.forEach(san -> builder.addSubjectAlternativeName(Type.URI, san));
        return builder.build();
    }

    private static RequiredPeerCredential createRequiredCredential(Field field, String pattern) {
        return RequiredPeerCredential.of(field, pattern);
    }

    private static PeerAuthorizer createPeerAuthorizer(PeerPolicy... policies) {
        return new PeerAuthorizer(new AuthorizedPeers(Arrays.stream(policies).collect(toSet())));
    }

    private static PeerPolicy createPolicy(String name, RequiredPeerCredential... requiredCredentials) {
        return new PeerPolicy(name, asList(requiredCredentials));
    }

    private static PeerPolicy createPolicy(String name, List<Capability> caps, List<RequiredPeerCredential> creds) {
        return new PeerPolicy(name, Optional.empty(), CapabilitySet.of(caps), creds);
    }

    private static void assertAuthorized(ConnectionAuthContext result) {
        assertTrue(result.authorized());
    }

    private static void assertUnauthorized(ConnectionAuthContext result) {
        assertFalse(result.authorized());
    }

    private static void assertCapabiltiesGranted(ConnectionAuthContext ctx, Set<Capability> expected) {
        assertThat(ctx.capabilities().asSet()).containsOnly(expected.toArray(new Capability[0]));
    }

}
