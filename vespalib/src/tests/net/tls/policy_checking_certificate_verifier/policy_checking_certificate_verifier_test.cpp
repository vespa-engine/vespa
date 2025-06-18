// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/policy_checking_certificate_verifier.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::net::tls;

bool dns_glob_matches(std::string_view pattern, std::string_view string_to_check) {
    auto glob = CredentialMatchPattern::create_from_dns_glob(pattern);
    return glob->matches(string_to_check);
}

bool uri_glob_matches(std::string_view pattern, std::string_view string_to_check) {
    auto glob = CredentialMatchPattern::create_from_uri_glob(pattern);
    return glob->matches(string_to_check);
}

void verify_all_glob_types_match(std::string_view pattern, std::string_view string_to_check) {
    EXPECT_TRUE(dns_glob_matches(pattern, string_to_check));
    EXPECT_TRUE(uri_glob_matches(pattern, string_to_check));
}

void verify_all_glob_types_mismatch(std::string_view pattern, std::string_view string_to_check) {
    EXPECT_FALSE(dns_glob_matches(pattern, string_to_check));
    EXPECT_FALSE(uri_glob_matches(pattern, string_to_check));
}

TEST(PolicyCheckingCertificateVerifierTest, glob_without_wildcards_matches_entire_string) {
    verify_all_glob_types_match("foo", "foo");
    verify_all_glob_types_mismatch("foo", "fooo");
    verify_all_glob_types_mismatch("foo", "ffoo");
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_glob_can_match_prefix) {
    verify_all_glob_types_match("foo*", "foo");
    verify_all_glob_types_match("foo*", "foobar");
    verify_all_glob_types_mismatch("foo*", "ffoo");
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_glob_can_match_suffix) {
    verify_all_glob_types_match("*foo", "foo");
    verify_all_glob_types_match("*foo", "ffoo");
    verify_all_glob_types_mismatch("*foo", "fooo");
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_glob_can_match_substring) {
    verify_all_glob_types_match("f*o", "fo");
    verify_all_glob_types_match("f*o", "foo");
    verify_all_glob_types_match("f*o", "ffoo");
    verify_all_glob_types_mismatch("f*o", "boo");
}

TEST(PolicyCheckingCertificateVerifierTest, single_char_DNS_glob_matches_single_character) {
    EXPECT_TRUE(dns_glob_matches("f?o", "foo"));
    EXPECT_FALSE(dns_glob_matches("f?o", "fooo"));
    EXPECT_FALSE(dns_glob_matches("f?o", "ffoo"));
}

// Due to URIs being able to contain '?' characters as a query separator, don't use it for wildcarding.
TEST(PolicyCheckingCertificateVerifierTest, test_URI_glob_matching_treats_question_mark_character_as_literal_match) {
    EXPECT_TRUE(uri_glob_matches("f?o", "f?o"));
    EXPECT_FALSE(uri_glob_matches("f?o", "foo"));
    EXPECT_FALSE(uri_glob_matches("f?o", "f?oo"));
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_DNS_glob_does_not_cross_multiple_dot_delimiter_boundaries) {
    EXPECT_TRUE(dns_glob_matches("*.bar.baz", "foo.bar.baz"));
    EXPECT_TRUE(dns_glob_matches("*.bar.baz", ".bar.baz"));
    EXPECT_FALSE(dns_glob_matches("*.bar.baz", "zoid.foo.bar.baz"));
    EXPECT_TRUE(dns_glob_matches("foo.*.baz", "foo.bar.baz"));
    EXPECT_FALSE(dns_glob_matches("foo.*.baz", "foo.bar.zoid.baz"));
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_URI_glob_does_not_cross_multiple_fwd_slash_delimiter_boundaries) {
    EXPECT_TRUE(uri_glob_matches("*/bar/baz", "foo/bar/baz"));
    EXPECT_TRUE(uri_glob_matches("*/bar/baz", "/bar/baz"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "bar/baz"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "/bar/baz/"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "zoid/foo/bar/baz"));
    EXPECT_TRUE(uri_glob_matches("foo/*/baz", "foo/bar/baz"));
    EXPECT_FALSE(uri_glob_matches("foo/*/baz", "foo/bar/zoid/baz"));
    EXPECT_TRUE(uri_glob_matches("foo/*/baz", "foo/bar.zoid/baz")); // No special handling of dots
}

TEST(PolicyCheckingCertificateVerifierTest, single_char_DNS_glob_matches_non_dot_characters_only) {
    EXPECT_FALSE(dns_glob_matches("f?o", "f.o"));
}

TEST(PolicyCheckingCertificateVerifierTest, special_basic_regex_characters_are_escaped) {
    verify_all_glob_types_match("$[.\\^", "$[.\\^");
}

TEST(PolicyCheckingCertificateVerifierTest, special_extended_regex_characters_are_ignored) {
    verify_all_glob_types_match("{)(+|]}", "{)(+|]}");
}

// TODO CN + SANs
PeerCredentials creds_with_sans(std::vector<std::string> dns_sans, std::vector<std::string> uri_sans) {
    PeerCredentials creds;
    creds.dns_sans = std::move(dns_sans);
    creds.uri_sans = std::move(uri_sans);
    return creds;
}

PeerCredentials creds_with_dns_sans(std::vector<std::string> dns_sans) {
    PeerCredentials creds;
    creds.dns_sans = std::move(dns_sans);
    return creds;
}

PeerCredentials creds_with_uri_sans(std::vector<std::string> uri_sans) {
    PeerCredentials creds;
    creds.uri_sans = std::move(uri_sans);
    return creds;
}

PeerCredentials creds_with_cn(std::string_view cn) {
    PeerCredentials creds;
    creds.common_name = cn;
    return creds;
}

bool verify(AuthorizedPeers authorized_peers, const PeerCredentials& peer_creds) {
    auto verifier = create_verify_callback_from(std::move(authorized_peers));
    return verifier->verify(peer_creds).success();
}

CapabilitySet verify_capabilities(AuthorizedPeers authorized_peers, const PeerCredentials& peer_creds) {
    auto verifier = create_verify_callback_from(std::move(authorized_peers));
    return verifier->verify(peer_creds).granted_capabilities();
}

TEST(PolicyCheckingCertificateVerifierTest, default_constructed_AuthorizedPeers_does_not_allow_all_authenticated_peers) {
    EXPECT_FALSE(AuthorizedPeers().allows_all_authenticated());
}

TEST(PolicyCheckingCertificateVerifierTest, specially_constructed_set_of_policies_allows_all_authenticated_peers) {
    auto allow_all = AuthorizedPeers::allow_all_authenticated();
    EXPECT_TRUE(allow_all.allows_all_authenticated());
    EXPECT_TRUE(verify(allow_all, creds_with_dns_sans({{"anything.goes"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, specially_constructed_set_of_policies_returns_full_capability_set) {
    auto allow_all = AuthorizedPeers::allow_all_authenticated();
    EXPECT_EQ(verify_capabilities(allow_all, creds_with_dns_sans({{"anything.goes"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST(PolicyCheckingCertificateVerifierTest, policy_without_explicit_capability_set_implicitly_returns_full_capability_set) {
    auto authorized = authorized_peers({policy_with({required_san_dns("yolo.swag")})});
    EXPECT_EQ(verify_capabilities(authorized, creds_with_dns_sans({{"yolo.swag"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST(PolicyCheckingCertificateVerifierTest, non_empty_policies_do_not_allow_all_authenticated_peers) {
    auto allow_not_all = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_FALSE(allow_not_all.allows_all_authenticated());
}

TEST(PolicyCheckingCertificateVerifierTest, test_DNS_SAN_requirement_without_glob_pattern_is_matched_as_exact_string) {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.worlds"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hhello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.world.bar"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, test_DNS_SAN_requirement_can_include_glob_wildcards_delimited_by_dot_character) {
    auto authorized = authorized_peers({policy_with({required_san_dns("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"greetings.w0rld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.wrld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"world"}})));
}

// TODO consider making this RFC 2459-compliant with case insensitivity for scheme and host
TEST(PolicyCheckingCertificateVerifierTest, test_URI_SAN_requirement_without_glob_pattern_is_matched_as_exact_string) {
    auto authorized = authorized_peers({policy_with({required_san_uri("foo://bar.baz/zoid")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"foo://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://bar.baz/zoi"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"oo://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"bar://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://bar.baz"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://BAR.baz/zoid"}})));
}

// TODO consider making this RFC 2459-compliant with case insensitivity for scheme and host
TEST(PolicyCheckingCertificateVerifierTest, test_URI_SAN_requirement_can_include_glob_wildcards_delimited_by_fwd_slash_character) {
    auto authorized = authorized_peers({policy_with({required_san_uri("myscheme://my/*/uri")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/cool/uri"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/really.cool/uri"}}))); // Not delimited by dots
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"theirscheme://my/cool/uri"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://their/cool/uri"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/cool/uris"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/swag/uri/"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/uri"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, test_URI_SAN_requirement_can_include_query_part_even_though_it_is_rather_silly_to_do_so) {
    auto authorized = authorized_peers({policy_with({required_san_uri("myscheme://my/fancy/*?magic")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/fancy/uri?magic"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/fancy/?magic"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/fancy/urimagic"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, multi_SAN_policy_requires_all_SANs_to_be_present_in_certificate) {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world"),
                                                     required_san_dns("foo.bar"),
                                                     required_san_uri("foo://bar/baz")})});
    EXPECT_TRUE(verify(authorized, creds_with_sans({{"hello.world"}, {"foo.bar"}}, {{"foo://bar/baz"}})));
    // Need all
    EXPECT_FALSE(verify(authorized, creds_with_sans({{"hello.world"}, {"foo.bar"}}, {})));
    EXPECT_FALSE(verify(authorized, creds_with_sans({{"hello.world"}}, {{"foo://bar/baz"}})));
    EXPECT_FALSE(verify(authorized, creds_with_sans({{"hello.world"}}, {})));
    EXPECT_FALSE(verify(authorized, creds_with_sans({{"foo.bar"}}, {})));
    EXPECT_FALSE(verify(authorized, creds_with_sans({}, {{"foo://bar/baz"}})));
    // OK with more SANs that strictly required
    EXPECT_TRUE(verify(authorized,  creds_with_sans({{"hello.world"}, {"foo.bar"}, {"baz.blorg"}},
                                                    {{"foo://bar/baz"}, {"hello://world/"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_DNS_SAN_in_certificate_is_not_treated_as_a_wildcard_match_by_policy) {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"*.world"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, wildcard_URI_SAN_in_certificate_is_not_treated_as_a_wildcard_match_by_policy) {
    auto authorized = authorized_peers({policy_with({required_san_uri("hello://world")})});
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"hello://*"}})));
}

// TODO this is just by coincidence since we match '*' as any other character, not because we interpret
//  the wildcard in the SAN as anything special during matching. Consider if we need/want to handle explicitly.
TEST(PolicyCheckingCertificateVerifierTest, wildcard_DNS_SAN_in_certificate_is_still_matched_by_wildcard_policy_SAN) {
    auto authorized = authorized_peers({policy_with({required_san_dns("*.world")})});
    EXPECT_TRUE(verify(authorized, creds_with_dns_sans({{"*.world"}})));
}

struct MultiPolicyMatchFixture {
    AuthorizedPeers authorized;
    MultiPolicyMatchFixture();
    ~MultiPolicyMatchFixture();
};

MultiPolicyMatchFixture::MultiPolicyMatchFixture()
    : authorized(authorized_peers({policy_with({required_san_dns("hello.world")},   CapabilitySet::of({cap_1()})),
                                   policy_with({required_san_dns("foo.bar")},       CapabilitySet::of({cap_2()})),
                                   policy_with({required_san_dns("zoid.berg")},     CapabilitySet::of({cap_2(), cap_3()})),
                                   policy_with({required_san_dns("secret.sauce")},  CapabilitySet::make_with_all_capabilities()),
                                   policy_with({required_san_uri("zoid://be.rg/")}, CapabilitySet::of({cap_4()}))}))
{}

MultiPolicyMatchFixture::~MultiPolicyMatchFixture() = default;

TEST(PolicyCheckingCertificateVerifierTest, peer_verifies_if_it_matches_at_least_1_policy_of_multiple) {
    MultiPolicyMatchFixture f;
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"zoid.berg"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_uri_sans({{"zoid://be.rg/"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, capability_set_is_returned_for_single_matched_policy) {
    MultiPolicyMatchFixture f;
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}})),
                 CapabilitySet::of({cap_1()}));
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"foo.bar"}})),
                 CapabilitySet::of({cap_2()}));
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"zoid.berg"}})),
                 CapabilitySet::of({cap_2(), cap_3()}));
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"secret.sauce"}})),
                 CapabilitySet::make_with_all_capabilities());
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_uri_sans({{"zoid://be.rg/"}})),
                 CapabilitySet::of({cap_4()}));
}

TEST(PolicyCheckingCertificateVerifierTest, peer_verifies_if_it_matches_multiple_policies) {
    MultiPolicyMatchFixture f;
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}, {"zoid.berg"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, union_capability_set_is_returned_if_multiple_policies_match) {
    MultiPolicyMatchFixture f;
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}, {"foo.bar"}, {"zoid.berg"}})),
                 CapabilitySet::of({cap_1(), cap_2(), cap_3()}));
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}, {"foo.bar"}, {"secret.sauce"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST(PolicyCheckingCertificateVerifierTest, peer_must_match_at_least_1_of_multiple_policies) {
    MultiPolicyMatchFixture f;
    EXPECT_FALSE(verify(f.authorized, creds_with_dns_sans({{"does.not.exist"}})));
}

TEST(PolicyCheckingCertificateVerifierTest, empty_capability_set_is_returned_if_no_policies_match) {
    MultiPolicyMatchFixture f;
    EXPECT_EQ(verify_capabilities(f.authorized, creds_with_dns_sans({{"does.not.exist"}})),
                 CapabilitySet::make_empty());
}

TEST(PolicyCheckingCertificateVerifierTest, test_CN_requirement_without_glob_pattern_is_matched_as_exact_string) {
    auto authorized = authorized_peers({policy_with({required_cn("hello.world")})});
    EXPECT_TRUE(verify(authorized,  creds_with_cn("hello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("foo.bar")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.worlds")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hhello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("foo.hello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.world.bar")));
}

TEST(PolicyCheckingCertificateVerifierTest, test_CN_requirement_can_include_glob_wildcards) {
    auto authorized = authorized_peers({policy_with({required_cn("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_cn("hello.world")));
    EXPECT_TRUE(verify(authorized,  creds_with_cn("greetings.w0rld")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.wrld")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("world")));
}

TEST(PolicyCheckingCertificateVerifierTest, test_VerificationResult_is_not_authorized_by_default) {
    VerificationResult result;
    EXPECT_FALSE(result.success());
    EXPECT_TRUE(result.granted_capabilities().empty());
}

TEST(PolicyCheckingCertificateVerifierTest, test_VerificationResult_can_be_explicitly_created_as_not_authorized) {
    auto result = VerificationResult::make_not_authorized();
    EXPECT_FALSE(result.success());
    EXPECT_TRUE(result.granted_capabilities().empty());
}

TEST(PolicyCheckingCertificateVerifierTest, test_VerificationResult_can_be_pre_authorized_with_all_capabilities) {
    auto result = VerificationResult::make_authorized_with_all_capabilities();
    EXPECT_TRUE(result.success());
    EXPECT_FALSE(result.granted_capabilities().empty());
    EXPECT_EQ(result.granted_capabilities(), CapabilitySet::make_with_all_capabilities());
}

TEST(PolicyCheckingCertificateVerifierTest, test_VerificationResult_can_be_pre_authorized_for_an_explicit_set_of_capabilities) {
    auto result = VerificationResult::make_authorized_with_capabilities(CapabilitySet::of({cap_2(), cap_3()}));
    EXPECT_TRUE(result.success());
    EXPECT_FALSE(result.granted_capabilities().empty());
    EXPECT_TRUE(result.granted_capabilities().contains(cap_2()));
    EXPECT_TRUE(result.granted_capabilities().contains(cap_3()));
    EXPECT_FALSE(result.granted_capabilities().contains(cap_1()));
}

// TODO test CN _and_ SAN

GTEST_MAIN_RUN_ALL_TESTS()

