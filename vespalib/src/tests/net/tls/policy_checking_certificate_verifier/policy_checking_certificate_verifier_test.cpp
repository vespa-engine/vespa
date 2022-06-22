// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/policy_checking_certificate_verifier.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;
using namespace vespalib::net::tls;

bool dns_glob_matches(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    auto glob = CredentialMatchPattern::create_from_dns_glob(pattern);
    return glob->matches(string_to_check);
}

bool uri_glob_matches(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    auto glob = CredentialMatchPattern::create_from_uri_glob(pattern);
    return glob->matches(string_to_check);
}

void verify_all_glob_types_match(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    EXPECT_TRUE(dns_glob_matches(pattern, string_to_check));
    EXPECT_TRUE(uri_glob_matches(pattern, string_to_check));
}

void verify_all_glob_types_mismatch(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    EXPECT_FALSE(dns_glob_matches(pattern, string_to_check));
    EXPECT_FALSE(uri_glob_matches(pattern, string_to_check));
}

TEST("glob without wildcards matches entire string") {
    verify_all_glob_types_match("foo", "foo");
    verify_all_glob_types_mismatch("foo", "fooo");
    verify_all_glob_types_mismatch("foo", "ffoo");
}

TEST("wildcard glob can match prefix") {
    verify_all_glob_types_match("foo*", "foo");
    verify_all_glob_types_match("foo*", "foobar");
    verify_all_glob_types_mismatch("foo*", "ffoo");
}

TEST("wildcard glob can match suffix") {
    verify_all_glob_types_match("*foo", "foo");
    verify_all_glob_types_match("*foo", "ffoo");
    verify_all_glob_types_mismatch("*foo", "fooo");
}

TEST("wildcard glob can match substring") {
    verify_all_glob_types_match("f*o", "fo");
    verify_all_glob_types_match("f*o", "foo");
    verify_all_glob_types_match("f*o", "ffoo");
    verify_all_glob_types_mismatch("f*o", "boo");
}

TEST("single char DNS glob matches single character") {
    EXPECT_TRUE(dns_glob_matches("f?o", "foo"));
    EXPECT_FALSE(dns_glob_matches("f?o", "fooo"));
    EXPECT_FALSE(dns_glob_matches("f?o", "ffoo"));
}

// Due to URIs being able to contain '?' characters as a query separator, don't use it for wildcarding.
TEST("URI glob matching treats question mark character as literal match") {
    EXPECT_TRUE(uri_glob_matches("f?o", "f?o"));
    EXPECT_FALSE(uri_glob_matches("f?o", "foo"));
    EXPECT_FALSE(uri_glob_matches("f?o", "f?oo"));
}

TEST("wildcard DNS glob does not cross multiple dot delimiter boundaries") {
    EXPECT_TRUE(dns_glob_matches("*.bar.baz", "foo.bar.baz"));
    EXPECT_TRUE(dns_glob_matches("*.bar.baz", ".bar.baz"));
    EXPECT_FALSE(dns_glob_matches("*.bar.baz", "zoid.foo.bar.baz"));
    EXPECT_TRUE(dns_glob_matches("foo.*.baz", "foo.bar.baz"));
    EXPECT_FALSE(dns_glob_matches("foo.*.baz", "foo.bar.zoid.baz"));
}

TEST("wildcard URI glob does not cross multiple fwd slash delimiter boundaries") {
    EXPECT_TRUE(uri_glob_matches("*/bar/baz", "foo/bar/baz"));
    EXPECT_TRUE(uri_glob_matches("*/bar/baz", "/bar/baz"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "bar/baz"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "/bar/baz/"));
    EXPECT_FALSE(uri_glob_matches("*/bar/baz", "zoid/foo/bar/baz"));
    EXPECT_TRUE(uri_glob_matches("foo/*/baz", "foo/bar/baz"));
    EXPECT_FALSE(uri_glob_matches("foo/*/baz", "foo/bar/zoid/baz"));
    EXPECT_TRUE(uri_glob_matches("foo/*/baz", "foo/bar.zoid/baz")); // No special handling of dots
}

TEST("single char DNS glob matches non dot characters only") {
    EXPECT_FALSE(dns_glob_matches("f?o", "f.o"));
}

TEST("special basic regex characters are escaped") {
    verify_all_glob_types_match("$[.\\^", "$[.\\^");
}

TEST("special extended regex characters are ignored") {
    verify_all_glob_types_match("{)(+|]}", "{)(+|]}");
}

// TODO CN + SANs
PeerCredentials creds_with_sans(std::vector<vespalib::string> dns_sans, std::vector<vespalib::string> uri_sans) {
    PeerCredentials creds;
    creds.dns_sans = std::move(dns_sans);
    creds.uri_sans = std::move(uri_sans);
    return creds;
}

PeerCredentials creds_with_dns_sans(std::vector<vespalib::string> dns_sans) {
    PeerCredentials creds;
    creds.dns_sans = std::move(dns_sans);
    return creds;
}

PeerCredentials creds_with_uri_sans(std::vector<vespalib::string> uri_sans) {
    PeerCredentials creds;
    creds.uri_sans = std::move(uri_sans);
    return creds;
}

PeerCredentials creds_with_cn(vespalib::stringref cn) {
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

TEST("Default-constructed AuthorizedPeers does not allow all authenticated peers") {
    EXPECT_FALSE(AuthorizedPeers().allows_all_authenticated());
}

TEST("Specially constructed set of policies allows all authenticated peers") {
    auto allow_all = AuthorizedPeers::allow_all_authenticated();
    EXPECT_TRUE(allow_all.allows_all_authenticated());
    EXPECT_TRUE(verify(allow_all, creds_with_dns_sans({{"anything.goes"}})));
}

TEST("specially constructed set of policies returns full capability set") {
    auto allow_all = AuthorizedPeers::allow_all_authenticated();
    EXPECT_EQUAL(verify_capabilities(allow_all, creds_with_dns_sans({{"anything.goes"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST("policy without explicit capability set implicitly returns full capability set") {
    auto authorized = authorized_peers({policy_with({required_san_dns("yolo.swag")})});
    EXPECT_EQUAL(verify_capabilities(authorized, creds_with_dns_sans({{"yolo.swag"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST("Non-empty policies do not allow all authenticated peers") {
    auto allow_not_all = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_FALSE(allow_not_all.allows_all_authenticated());
}

TEST("DNS SAN requirement without glob pattern is matched as exact string") {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.worlds"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hhello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.world.bar"}})));
}

TEST("DNS SAN requirement can include glob wildcards, delimited by dot character") {
    auto authorized = authorized_peers({policy_with({required_san_dns("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"greetings.w0rld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.wrld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"world"}})));
}

// TODO consider making this RFC 2459-compliant with case insensitivity for scheme and host
TEST("URI SAN requirement without glob pattern is matched as exact string") {
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
TEST("URI SAN requirement can include glob wildcards, delimited by fwd slash character") {
    auto authorized = authorized_peers({policy_with({required_san_uri("myscheme://my/*/uri")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/cool/uri"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/really.cool/uri"}}))); // Not delimited by dots
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"theirscheme://my/cool/uri"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://their/cool/uri"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/cool/uris"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/swag/uri/"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/uri"}})));
}

TEST("URI SAN requirement can include query part even though it's rather silly to do so") {
    auto authorized = authorized_peers({policy_with({required_san_uri("myscheme://my/fancy/*?magic")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/fancy/uri?magic"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"myscheme://my/fancy/?magic"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"myscheme://my/fancy/urimagic"}})));
}

TEST("multi-SAN policy requires all SANs to be present in certificate") {
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

TEST("wildcard DNS SAN in certificate is not treated as a wildcard match by policy") {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"*.world"}})));
}

TEST("wildcard URI SAN in certificate is not treated as a wildcard match by policy") {
    auto authorized = authorized_peers({policy_with({required_san_uri("hello://world")})});
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"hello://*"}})));
}

// TODO this is just by coincidence since we match '*' as any other character, not because we interpret
//  the wildcard in the SAN as anything special during matching. Consider if we need/want to handle explicitly.
TEST("wildcard DNS SAN in certificate is still matched by wildcard policy SAN") {
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

TEST_F("peer verifies if it matches at least 1 policy of multiple", MultiPolicyMatchFixture) {
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"zoid.berg"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_uri_sans({{"zoid://be.rg/"}})));
}

TEST_F("capability set is returned for single matched policy", MultiPolicyMatchFixture) {
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}})),
                 CapabilitySet::of({cap_1()}));
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"foo.bar"}})),
                 CapabilitySet::of({cap_2()}));
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"zoid.berg"}})),
                 CapabilitySet::of({cap_2(), cap_3()}));
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"secret.sauce"}})),
                 CapabilitySet::make_with_all_capabilities());
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_uri_sans({{"zoid://be.rg/"}})),
                 CapabilitySet::of({cap_4()}));
}

TEST_F("peer verifies if it matches multiple policies", MultiPolicyMatchFixture) {
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}, {"zoid.berg"}})));
}

TEST_F("union capability set is returned if multiple policies match", MultiPolicyMatchFixture) {
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}, {"foo.bar"}, {"zoid.berg"}})),
                 CapabilitySet::of({cap_1(), cap_2(), cap_3()}));
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"hello.world"}, {"foo.bar"}, {"secret.sauce"}})),
                 CapabilitySet::make_with_all_capabilities());
}

TEST_F("peer must match at least 1 of multiple policies", MultiPolicyMatchFixture) {
    EXPECT_FALSE(verify(f.authorized, creds_with_dns_sans({{"does.not.exist"}})));
}

TEST_F("empty capability set is returned if no policies match", MultiPolicyMatchFixture) {
    EXPECT_EQUAL(verify_capabilities(f.authorized, creds_with_dns_sans({{"does.not.exist"}})),
                 CapabilitySet::make_empty());
}

TEST("CN requirement without glob pattern is matched as exact string") {
    auto authorized = authorized_peers({policy_with({required_cn("hello.world")})});
    EXPECT_TRUE(verify(authorized,  creds_with_cn("hello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("foo.bar")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.worlds")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hhello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("foo.hello.world")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.world.bar")));
}

TEST("CN requirement can include glob wildcards") {
    auto authorized = authorized_peers({policy_with({required_cn("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_cn("hello.world")));
    EXPECT_TRUE(verify(authorized,  creds_with_cn("greetings.w0rld")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("hello.wrld")));
    EXPECT_FALSE(verify(authorized, creds_with_cn("world")));
}

TEST("VerificationResult is not authorized by default") {
    VerificationResult result;
    EXPECT_FALSE(result.success());
    EXPECT_TRUE(result.granted_capabilities().empty());
}

TEST("VerificationResult can be explicitly created as not authorized") {
    auto result = VerificationResult::make_not_authorized();
    EXPECT_FALSE(result.success());
    EXPECT_TRUE(result.granted_capabilities().empty());
}

TEST("VerificationResult can be pre-authorized with all capabilities") {
    auto result = VerificationResult::make_authorized_with_all_capabilities();
    EXPECT_TRUE(result.success());
    EXPECT_FALSE(result.granted_capabilities().empty());
    EXPECT_EQUAL(result.granted_capabilities(), CapabilitySet::make_with_all_capabilities());
}

TEST("VerificationResult can be pre-authorized for an explicit set of capabilities") {
    auto result = VerificationResult::make_authorized_with_capabilities(CapabilitySet::of({cap_2(), cap_3()}));
    EXPECT_TRUE(result.success());
    EXPECT_FALSE(result.granted_capabilities().empty());
    EXPECT_TRUE(result.granted_capabilities().contains(cap_2()));
    EXPECT_TRUE(result.granted_capabilities().contains(cap_3()));
    EXPECT_FALSE(result.granted_capabilities().contains(cap_1()));
}

// TODO test CN _and_ SAN

TEST_MAIN() { TEST_RUN_ALL(); }

