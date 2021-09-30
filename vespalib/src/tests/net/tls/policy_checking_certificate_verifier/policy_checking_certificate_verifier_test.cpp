// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/policy_checking_certificate_verifier.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;
using namespace vespalib::net::tls;

bool glob_matches(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    auto glob = CredentialMatchPattern::create_from_glob(pattern);
    return glob->matches(string_to_check);
}

TEST("glob without wildcards matches entire string") {
    EXPECT_TRUE(glob_matches("foo", "foo"));
    EXPECT_FALSE(glob_matches("foo", "fooo"));
    EXPECT_FALSE(glob_matches("foo", "ffoo"));
}

TEST("wildcard glob can match prefix") {
    EXPECT_TRUE(glob_matches("foo*", "foo"));
    EXPECT_TRUE(glob_matches("foo*", "foobar"));
    EXPECT_FALSE(glob_matches("foo*", "ffoo"));
}

TEST("wildcard glob can match suffix") {
    EXPECT_TRUE(glob_matches("*foo", "foo"));
    EXPECT_TRUE(glob_matches("*foo", "ffoo"));
    EXPECT_FALSE(glob_matches("*foo", "fooo"));
}

TEST("wildcard glob can match substring") {
    EXPECT_TRUE(glob_matches("f*o", "fo"));
    EXPECT_TRUE(glob_matches("f*o", "foo"));
    EXPECT_TRUE(glob_matches("f*o", "ffoo"));
    EXPECT_FALSE(glob_matches("f*o", "boo"));
}

TEST("wildcard glob does not cross multiple dot delimiter boundaries") {
    EXPECT_TRUE(glob_matches("*.bar.baz", "foo.bar.baz"));
    EXPECT_TRUE(glob_matches("*.bar.baz", ".bar.baz"));
    EXPECT_FALSE(glob_matches("*.bar.baz", "zoid.foo.bar.baz"));
    EXPECT_TRUE(glob_matches("foo.*.baz", "foo.bar.baz"));
    EXPECT_FALSE(glob_matches("foo.*.baz", "foo.bar.zoid.baz"));
}

TEST("single char glob matches non dot characters") {
    EXPECT_TRUE(glob_matches("f?o", "foo"));
    EXPECT_FALSE(glob_matches("f?o", "fooo"));
    EXPECT_FALSE(glob_matches("f?o", "ffoo"));
    EXPECT_FALSE(glob_matches("f?o", "f.o"));
}

TEST("special basic regex characters are escaped") {
    EXPECT_TRUE(glob_matches("$[.\\^", "$[.\\^"));
}

TEST("special extended regex characters are ignored") {
    EXPECT_TRUE(glob_matches("{)(+|]}", "{)(+|]}"));
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
    return verifier->verify(peer_creds);
}

TEST("Default-constructed AuthorizedPeers does not allow all authenticated peers") {
    EXPECT_FALSE(AuthorizedPeers().allows_all_authenticated());
}

TEST("Specially constructed set of policies allows all authenticated peers") {
    auto allow_all = AuthorizedPeers::allow_all_authenticated();
    EXPECT_TRUE(allow_all.allows_all_authenticated());
    EXPECT_TRUE(verify(allow_all, creds_with_dns_sans({{"anything.goes"}})));
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

TEST("DNS SAN requirement can include glob wildcards") {
    auto authorized = authorized_peers({policy_with({required_san_dns("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"greetings.w0rld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.wrld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"world"}})));
}

// FIXME make this RFC 2459-compliant with subdomain matching, case insensitity for host etc
TEST("URI SAN requirement is matched as exact string in cheeky, pragmatic violation of RFC 2459") {
    auto authorized = authorized_peers({policy_with({required_san_uri("foo://bar.baz/zoid")})});
    EXPECT_TRUE(verify(authorized,  creds_with_uri_sans({{"foo://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://bar.baz/zoi"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"oo://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"bar://bar.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://bar.baz"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://.baz/zoid"}})));
    EXPECT_FALSE(verify(authorized, creds_with_uri_sans({{"foo://BAR.baz/zoid"}})));
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
    : authorized(authorized_peers({policy_with({required_san_dns("hello.world")}),
                                   policy_with({required_san_dns("foo.bar")}),
                                   policy_with({required_san_dns("zoid.berg")}),
                                   policy_with({required_san_uri("zoid://be.rg/")})}))
{}

MultiPolicyMatchFixture::~MultiPolicyMatchFixture() = default;

TEST_F("peer verifies if it matches at least 1 policy of multiple", MultiPolicyMatchFixture) {
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"zoid.berg"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_uri_sans({{"zoid://be.rg/"}})));
}

TEST_F("peer verifies if it matches multiple policies", MultiPolicyMatchFixture) {
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}, {"zoid.berg"}})));
}

TEST_F("peer must match at least 1 of multiple policies", MultiPolicyMatchFixture) {
    EXPECT_FALSE(verify(f.authorized, creds_with_dns_sans({{"does.not.exist"}})));
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

// TODO test CN _and_ SAN

TEST_MAIN() { TEST_RUN_ALL(); }

