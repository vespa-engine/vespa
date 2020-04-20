// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/policy_checking_certificate_verifier.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;
using namespace vespalib::net::tls;

bool glob_matches(vespalib::stringref pattern, vespalib::stringref string_to_check) {
    auto glob = HostGlobPattern::create_from_glob(pattern);
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
PeerCredentials creds_with_dns_sans(std::vector<vespalib::string> dns_sans) {
    PeerCredentials creds;
    creds.dns_sans = std::move(dns_sans);
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

TEST("SAN requirement without glob pattern is matched as exact string") {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.worlds"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hhello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.world.bar"}})));
}

TEST("SAN requirement can include glob wildcards") {
    auto authorized = authorized_peers({policy_with({required_san_dns("*.w?rld")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"greetings.w0rld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.wrld"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"world"}})));
}

TEST("multi-SAN policy requires all SANs to be present in certificate") {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world"),
                                                     required_san_dns("foo.bar")})});
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}, {"foo.bar"}})));
    // Need both
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"hello.world"}})));
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"foo.bar"}})));
    // OK with more SANs that strictly required
    EXPECT_TRUE(verify(authorized,  creds_with_dns_sans({{"hello.world"}, {"foo.bar"}, {"baz.blorg"}})));
}

TEST("wildcard SAN in certificate is not treated as a wildcard match by policy") {
    auto authorized = authorized_peers({policy_with({required_san_dns("hello.world")})});
    EXPECT_FALSE(verify(authorized, creds_with_dns_sans({{"*.world"}})));
}

TEST("wildcard SAN in certificate is still matched by wildcard policy SAN") {
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
                                   policy_with({required_san_dns("zoid.berg")})}))
{}

MultiPolicyMatchFixture::~MultiPolicyMatchFixture() = default;

TEST_F("peer verifies if it matches at least 1 policy of multiple", MultiPolicyMatchFixture) {
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"hello.world"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"foo.bar"}})));
    EXPECT_TRUE(verify(f.authorized, creds_with_dns_sans({{"zoid.berg"}})));
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

