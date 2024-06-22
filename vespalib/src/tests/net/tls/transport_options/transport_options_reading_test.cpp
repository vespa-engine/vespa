// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/util/exceptions.h>
#include <gmock/gmock.h>
#include <filesystem>
#include <fstream>
#include <optional>
#include <sstream>

using namespace vespalib;
using namespace vespalib::net::tls;

namespace {

class ConfigWriter {
    std::optional<std::string> _private_key;
    std::optional<std::string> _ca_certificates;
    std::optional<std::string> _certificates;
    std::optional<std::string> _accepted_ciphers;
    std::optional<std::string> _authorized_peers;
    std::optional<std::string> _disable_hostname_validation;
    std::optional<std::string> _flipper_the_dolphin;
public:
    ConfigWriter();
    ConfigWriter(ConfigWriter&&);
    ~ConfigWriter();
    ConfigWriter private_key(std::optional<std::string> value) && { _private_key = value; return std::move(*this); }
    ConfigWriter ca_certificates(std::optional<std::string> value) && { _ca_certificates = value; return std::move(*this); }
    ConfigWriter certificates(std::optional<std::string> value) && { _certificates = value; return std::move(*this); }
    ConfigWriter accepted_ciphers(std::optional<std::string> value) && { _accepted_ciphers = value; return std::move(*this); }
    ConfigWriter authorized_peers(std::optional<std::string> value) && { _authorized_peers = value; return std::move(*this); }
    ConfigWriter disable_hostname_validation(std::optional<std::string> value) && { _disable_hostname_validation = value; return std::move(*this); }
    ConfigWriter flipper_the_dolphin(std::optional<std::string> value) && { _flipper_the_dolphin = value; return std::move(*this); }
    void write(std::ostream& os);
    std::string write();
};

ConfigWriter::ConfigWriter()
    : _private_key(TEST_PATH("dummy_privkey.txt")),
      _ca_certificates(TEST_PATH("dummy_ca_certs.txt")),
      _certificates(TEST_PATH("dummy_certs.txt")),
      _accepted_ciphers(),
      _authorized_peers(),
      _disable_hostname_validation(),
      _flipper_the_dolphin()
{
}

ConfigWriter::ConfigWriter(ConfigWriter&&) = default;

ConfigWriter::~ConfigWriter() = default;

void
ConfigWriter::write(std::ostream& os)
{
    os << "{\n" <<
        R"(  "files": {)";
    bool had_files_entry = false;
    if (_private_key.has_value()) {
        os << "\n" << R"(    "private-key": ")" << _private_key.value() << R"(")";
        had_files_entry = true;
    }
    if (_ca_certificates.has_value()) {
        if (had_files_entry) {
            os << ",";
        }
        os << "\n" << R"(    "ca-certificates": ")" << _ca_certificates.value() << R"(")";
        had_files_entry = true;
    }
    if (_certificates.has_value()) {
        if (had_files_entry) {
            os << ",";
        }
        os << "\n" << R"(    "certificates": ")" << _certificates.value() << R"(")";
        had_files_entry = true;
    }
    os << "\n  }";
    if (_accepted_ciphers.has_value()) {
        os << ",\n" << R"(  "accepted-ciphers" : )" << _accepted_ciphers.value();
    }
    if (_authorized_peers.has_value()) {
        os << ",\n" << R"(  "authorized-peers": )" << _authorized_peers.value();
    }
    if (_disable_hostname_validation.has_value()) {
        os << ",\n" << R"(  "disable-hostname-validation": )" << _disable_hostname_validation.value();
    }
    if (_flipper_the_dolphin.has_value()) {
        os << ",\n" << R"(  "flipper-the-dolphin": )" << _flipper_the_dolphin.value();
    }
    os << "\n}" << std::endl;
}

std::string
ConfigWriter::write()
{
    std::ostringstream os;
    write(os);
    return os.str();
}

}

class TransportSecurityOptionsTest : public ::testing::Test {
protected:
    TransportSecurityOptionsTest();
    ~TransportSecurityOptionsTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

TransportSecurityOptionsTest::TransportSecurityOptionsTest()
    : ::testing::Test()
{
}

TransportSecurityOptionsTest::~TransportSecurityOptionsTest() = default;

void
TransportSecurityOptionsTest::SetUpTestSuite()
{
    std::ofstream ok_config("ok_config.json");
    ConfigWriter().write(ok_config);
    ok_config.close();
}

void
TransportSecurityOptionsTest::TearDownTestSuite()
{
    std::filesystem::remove("ok_config.json");
}

TEST_F(TransportSecurityOptionsTest, can_load_tls_credentials_via_config_file)
{
    auto opts = read_options_from_json_file("ok_config.json");
    ASSERT_TRUE(opts.get() != nullptr);
    // Obviously we'd need to change this to actual PEM data if config reading started
    // actually verifying the _content_ of files, not just reading them.
    EXPECT_EQ("My private key\n", opts->private_key_pem());
    EXPECT_EQ("My CA certificates\n", opts->ca_certs_pem());
    EXPECT_EQ("My certificate chain\n", opts->cert_chain_pem());
}

TEST_F(TransportSecurityOptionsTest, copying_options_without_private_key_does_in_fact_not_include_private_key)
{
    auto opts = read_options_from_json_file("ok_config.json");
    auto cloned = opts->copy_without_private_key();
    EXPECT_EQ("", cloned.private_key_pem());
    EXPECT_EQ("My CA certificates\n", cloned.ca_certs_pem());
    EXPECT_EQ("My certificate chain\n", cloned.cert_chain_pem());
}

TEST_F(TransportSecurityOptionsTest, missing_json_file_throws_exception)
{
    EXPECT_THAT([]() { read_options_from_json_file("missing_config.json"); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("TLS config file 'missing_config.json' could not be read")));
}

TEST_F(TransportSecurityOptionsTest, bad_json_content_throws_exception)
{
    const char* bad_json = "hello world :D";
    EXPECT_THAT([bad_json]() { read_options_from_json_string(bad_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("Provided TLS config file is not valid JSON")));
}

TEST_F(TransportSecurityOptionsTest, missing_files_field_throws_exception)
{
    const char* incomplete_json = R"({})";
    EXPECT_THAT([incomplete_json]() { read_options_from_json_string(incomplete_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("TLS config root field 'files' is missing or empty")));
}

TEST_F(TransportSecurityOptionsTest, missing_private_key_field_throws_exception)
{
    auto incomplete_json = ConfigWriter().private_key(std::nullopt).write();
    EXPECT_THAT([incomplete_json]() { read_options_from_json_string(incomplete_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("TLS config field 'private-key' has not been set")));
}

TEST_F(TransportSecurityOptionsTest, missing_certificates_field_throws_exception)
{
    auto incomplete_json = ConfigWriter().certificates(std::nullopt).write();
    EXPECT_THAT([incomplete_json]() { read_options_from_json_string(incomplete_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("TLS config field 'certificates' has not been set")));
}

TEST_F(TransportSecurityOptionsTest, missing_ca_certificates_field_throws_exception)
{
    auto incomplete_json = ConfigWriter().ca_certificates(std::nullopt).write();
    EXPECT_THAT([incomplete_json]() { read_options_from_json_string(incomplete_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("TLS config field 'ca-certificates' has not been set")));
}

TEST_F(TransportSecurityOptionsTest, missing_file_referenced_by_field_throws_exception)
{
    auto incomplete_json = ConfigWriter().private_key("missing_privkey.txt").write();
    EXPECT_THAT([incomplete_json]() { read_options_from_json_string(incomplete_json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("File 'missing_privkey.txt' referenced by TLS config does not exist")));
}

vespalib::string json_with_policies(const vespalib::string& policies) {
    return ConfigWriter().authorized_peers(std::string("[") + policies + "]").write();
}

TransportSecurityOptions parse_policies(const vespalib::string& policies)
{
    return *read_options_from_json_string(json_with_policies(policies));
}

TEST_F(TransportSecurityOptionsTest, config_file_without_authorized_peers_accepts_all_pre_verified_certificates)
{
    auto json = ConfigWriter().write();
    EXPECT_TRUE(read_options_from_json_string(json)->authorized_peers().allows_all_authenticated());
}

// Instead of contemplating what the semantics of an empty allow list should be,
// we do the easy way out and just say it's not allowed in the first place.
TEST_F(TransportSecurityOptionsTest, empty_policy_array_throws_exception)
{
    EXPECT_THAT([]() { parse_policies(""); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("\"authorized-peers\" must either be not present (allows "
                                                                                    "all peers with valid certificates) or a non-empty array")));
}

TEST_F(TransportSecurityOptionsTest, can_parse_single_peer_policy_with_single_requirement)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"}
      ]
    })";
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("hello.world")})}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, can_parse_single_peer_policy_with_multiple_requirements)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"},
         {"field": "SAN_URI", "must-match": "foo://bar/baz"},
         {"field": "CN", "must-match": "goodbye.moon"}
      ]
    })";
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("hello.world"),
                                             required_san_uri("foo://bar/baz"),
                                             required_cn("goodbye.moon")})}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, unknown_field_type_throws_exception)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "winnie the pooh", "must-match": "piglet"}
      ]
    })";
    EXPECT_THAT([json]() { parse_policies(json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("Unsupported credential field type: 'winnie the pooh'. Supported are: CN, SAN_DNS")));
}

TEST_F(TransportSecurityOptionsTest, empty_required_credentials_array_throws_exception)
{
    const char* json = R"({
      "required-credentials":[]
    })";
    EXPECT_THAT([json]() { parse_policies(json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("\"required-credentials\" array can't be empty (would allow all peers)")));
}

TEST_F(TransportSecurityOptionsTest, accepted_cipher_list_is_empty_if_not_specified)
{
    auto json = ConfigWriter().write();
    EXPECT_TRUE(read_options_from_json_string(json)->accepted_ciphers().empty());
}

TEST_F(TransportSecurityOptionsTest, accepted_cipher_list_is_populated_if_specified)
{
    auto json = ConfigWriter().accepted_ciphers(R"(["foo", "bar"])").write();
    auto ciphers = read_options_from_json_string(json)->accepted_ciphers();
    ASSERT_EQ(2u, ciphers.size());
    EXPECT_EQ("foo", ciphers[0]);
    EXPECT_EQ("bar", ciphers[1]);
}

// FIXME this is temporary until we know enabling it by default won't break the world!
TEST_F(TransportSecurityOptionsTest, hostname_validation_is_DISABLED_by_default_when_creating_options_from_config_file)
{
    auto json = ConfigWriter().write();
    EXPECT_TRUE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST_F(TransportSecurityOptionsTest, transport_security_options_builder_does_not_disable_hostname_validation_by_default)
{
    auto ts_builder = vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem("foo").
            cert_chain_pem("bar").
            private_key_pem("fantonald");
    TransportSecurityOptions ts_opts(std::move(ts_builder));
    EXPECT_FALSE(ts_opts.disable_hostname_validation());
}

TEST_F(TransportSecurityOptionsTest, hostname_validation_can_be_explicitly_disabled)
{
    auto json = ConfigWriter().disable_hostname_validation("true").write();
    EXPECT_TRUE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST_F(TransportSecurityOptionsTest, hostname_validation_can_be_explicitly_enabled)
{
    auto json = ConfigWriter().disable_hostname_validation("false").write();
    EXPECT_FALSE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST_F(TransportSecurityOptionsTest, unknown_fields_are_ignored_at_parse_time)
{
    auto json = ConfigWriter().flipper_the_dolphin(R"("*weird dolphin noises*")").write();
    EXPECT_TRUE(read_options_from_json_string(json).get() != nullptr); // And no exception thrown.
}

TEST_F(TransportSecurityOptionsTest, policy_without_explicit_capabilities_implicitly_get_all_capabilities)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"}
      ]
    })";
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("hello.world")},
                                            CapabilitySet::make_with_all_capabilities())}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, specifying_a_capability_set_adds_all_its_underlying_capabilities)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content_node"]
    })";
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")},
                                            CapabilitySet::content_node())}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, can_specify_single_leaf_capabilities)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content.metrics_api", "vespa.slobrok.api"]
    })";
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")},
                                            CapabilitySet::of({Capability::content_metrics_api(),
                                                               Capability::slobrok_api()}))}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, specifying_multiple_capability_sets_adds_union_of_underlying_capabilities)
{
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content_node", "vespa.container_node"]
    })";
    CapabilitySet caps;
    caps.add_all(CapabilitySet::content_node());
    caps.add_all(CapabilitySet::container_node());
    EXPECT_EQ(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")}, caps)}),
              parse_policies(json).authorized_peers());
}

TEST_F(TransportSecurityOptionsTest, empty_capabilities_array_is_not_allowed) {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": []
    })";
    EXPECT_THAT([json]() { parse_policies(json); },
                testing::ThrowsMessage<IllegalArgumentException>(testing::HasSubstr("\"capabilities\" array must either be not present (implies "
                                                                                              "all capabilities) or contain at least one capability name")));
}

// TODO test parsing of multiple policies

GTEST_MAIN_RUN_ALL_TESTS()
