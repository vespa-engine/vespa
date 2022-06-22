// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/transport_security_options_reading.h>
#include <vespa/vespalib/test/peer_policy_utils.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace vespalib;
using namespace vespalib::net::tls;

TEST("can load TLS credentials via config file") {
    auto opts = read_options_from_json_file("ok_config.json");
    ASSERT_TRUE(opts.get() != nullptr);
    // Obviously we'd need to change this to actual PEM data if config reading started
    // actually verifying the _content_ of files, not just reading them.
    EXPECT_EQUAL("My private key\n", opts->private_key_pem());
    EXPECT_EQUAL("My CA certificates\n", opts->ca_certs_pem());
    EXPECT_EQUAL("My certificate chain\n", opts->cert_chain_pem());
}

TEST("copying options without private key does, in fact, not include private key") {
    auto opts = read_options_from_json_file("ok_config.json");
    auto cloned = opts->copy_without_private_key();
    EXPECT_EQUAL("", cloned.private_key_pem());
    EXPECT_EQUAL("My CA certificates\n", cloned.ca_certs_pem());
    EXPECT_EQUAL("My certificate chain\n", cloned.cert_chain_pem());
}

TEST("missing JSON file throws exception") {
    EXPECT_EXCEPTION(read_options_from_json_file("missing_config.json"), IllegalArgumentException,
                     "TLS config file 'missing_config.json' could not be read");
}

TEST("bad JSON content throws exception") {
    const char* bad_json = "hello world :D";
    EXPECT_EXCEPTION(read_options_from_json_string(bad_json), IllegalArgumentException,
                     "Provided TLS config file is not valid JSON");
}

TEST("missing 'files' field throws exception") {
    const char* incomplete_json = R"({})";
    EXPECT_EXCEPTION(read_options_from_json_string(incomplete_json), IllegalArgumentException,
                     "TLS config root field 'files' is missing or empty");
}

TEST("missing 'private-key' field throws exception") {
    const char* incomplete_json = R"({"files":{"certificates":"dummy_certs.txt","ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_EXCEPTION(read_options_from_json_string(incomplete_json), IllegalArgumentException,
                     "TLS config field 'private-key' has not been set");
}

TEST("missing 'certificates' field throws exception") {
    const char* incomplete_json = R"({"files":{"private-key":"dummy_privkey.txt","ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_EXCEPTION(read_options_from_json_string(incomplete_json), IllegalArgumentException,
                     "TLS config field 'certificates' has not been set");
}

TEST("missing 'ca-certificates' field throws exception") {
    const char* incomplete_json = R"({"files":{"private-key":"dummy_privkey.txt","certificates":"dummy_certs.txt"}})";
    EXPECT_EXCEPTION(read_options_from_json_string(incomplete_json), IllegalArgumentException,
                     "TLS config field 'ca-certificates' has not been set");
}

TEST("missing file referenced by field throws exception") {
    const char* incomplete_json = R"({"files":{"private-key":"missing_privkey.txt",
                                               "certificates":"dummy_certs.txt",
                                               "ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_EXCEPTION(read_options_from_json_string(incomplete_json), IllegalArgumentException,
                     "File 'missing_privkey.txt' referenced by TLS config does not exist");
}

vespalib::string json_with_policies(const vespalib::string& policies) {
    const char* fmt = R"({"files":{"private-key":"dummy_privkey.txt",
                                   "certificates":"dummy_certs.txt",
                                   "ca-certificates":"dummy_ca_certs.txt"},
                          "authorized-peers":[%s]})";
    return vespalib::make_string(fmt, policies.c_str());
}

TransportSecurityOptions parse_policies(const vespalib::string& policies) {
    return *read_options_from_json_string(json_with_policies(policies));
}

TEST("config file without authorized-peers accepts all pre-verified certificates") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_TRUE(read_options_from_json_string(json)->authorized_peers().allows_all_authenticated());
}

// Instead of contemplating what the semantics of an empty allow list should be,
// we do the easy way out and just say it's not allowed in the first place.
TEST("empty policy array throws exception") {
    EXPECT_EXCEPTION(parse_policies(""), vespalib::IllegalArgumentException,
                     "\"authorized-peers\" must either be not present (allows "
                     "all peers with valid certificates) or a non-empty array");
}

TEST("can parse single peer policy with single requirement") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"}
      ]
    })";
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("hello.world")})}),
                 parse_policies(json).authorized_peers());
}

TEST("can parse single peer policy with multiple requirements") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"},
         {"field": "SAN_URI", "must-match": "foo://bar/baz"},
         {"field": "CN", "must-match": "goodbye.moon"}
      ]
    })";
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("hello.world"),
                                                required_san_uri("foo://bar/baz"),
                                                required_cn("goodbye.moon")})}),
                 parse_policies(json).authorized_peers());
}

TEST("unknown field type throws exception") {
    const char* json = R"({
      "required-credentials":[
         {"field": "winnie the pooh", "must-match": "piglet"}
      ]
    })";
    EXPECT_EXCEPTION(parse_policies(json), vespalib::IllegalArgumentException,
                     "Unsupported credential field type: 'winnie the pooh'. Supported are: CN, SAN_DNS");
}

TEST("empty required-credentials array throws exception") {
    const char* json = R"({
      "required-credentials":[]
    })";
    EXPECT_EXCEPTION(parse_policies(json), vespalib::IllegalArgumentException,
                     "\"required-credentials\" array can't be empty (would allow all peers)");
}

TEST("accepted cipher list is empty if not specified") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_TRUE(read_options_from_json_string(json)->accepted_ciphers().empty());
}

TEST("accepted cipher list is populated if specified") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"},
                           "accepted-ciphers":["foo", "bar"]})";
    auto ciphers = read_options_from_json_string(json)->accepted_ciphers();
    ASSERT_EQUAL(2u, ciphers.size());
    EXPECT_EQUAL("foo", ciphers[0]);
    EXPECT_EQUAL("bar", ciphers[1]);
}

// FIXME this is temporary until we know enabling it by default won't break the world!
TEST("hostname validation is DISABLED by default when creating options from config file") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"}})";
    EXPECT_TRUE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST("TransportSecurityOptions builder does not disable hostname validation by default") {
    auto ts_builder = vespalib::net::tls::TransportSecurityOptions::Params().
            ca_certs_pem("foo").
            cert_chain_pem("bar").
            private_key_pem("fantonald");
    TransportSecurityOptions ts_opts(std::move(ts_builder));
    EXPECT_FALSE(ts_opts.disable_hostname_validation());
}

TEST("hostname validation can be explicitly disabled") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"},
                           "disable-hostname-validation": true})";
    EXPECT_TRUE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST("hostname validation can be explicitly enabled") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"},
                           "disable-hostname-validation": false})";
    EXPECT_FALSE(read_options_from_json_string(json)->disable_hostname_validation());
}

TEST("unknown fields are ignored at parse-time") {
    const char* json = R"({"files":{"private-key":"dummy_privkey.txt",
                                    "certificates":"dummy_certs.txt",
                                    "ca-certificates":"dummy_ca_certs.txt"},
                           "flipper-the-dolphin": "*weird dolphin noises*"})";
    EXPECT_TRUE(read_options_from_json_string(json).get() != nullptr); // And no exception thrown.
}

TEST("policy without explicit capabilities implicitly get all capabilities") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "hello.world"}
      ]
    })";
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("hello.world")},
                                               CapabilitySet::make_with_all_capabilities())}),
                 parse_policies(json).authorized_peers());
}

TEST("specifying a capability set adds all its underlying capabilities") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content_node"]
    })";
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")},
                                               CapabilitySet::content_node())}),
                 parse_policies(json).authorized_peers());
}

TEST("can specify single leaf capabilities") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content.metrics_api", "vespa.slobrok.api"]
    })";
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")},
                                               CapabilitySet::of({Capability::content_metrics_api(),
                                                                  Capability::slobrok_api()}))}),
                 parse_policies(json).authorized_peers());
}

TEST("specifying multiple capability sets adds union of underlying capabilities") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": ["vespa.content_node", "vespa.container_node"]
    })";
    CapabilitySet caps;
    caps.add_all(CapabilitySet::content_node());
    caps.add_all(CapabilitySet::container_node());
    EXPECT_EQUAL(authorized_peers({policy_with({required_san_dns("*.cool-content-clusters.example")}, caps)}),
                 parse_policies(json).authorized_peers());
}

TEST("empty capabilities array is not allowed") {
    const char* json = R"({
      "required-credentials":[
         {"field": "SAN_DNS", "must-match": "*.cool-content-clusters.example" }
      ],
      "capabilities": []
    })";
    EXPECT_EXCEPTION(parse_policies(json), vespalib::IllegalArgumentException,
                     "\"capabilities\" array must either be not present (implies "
                     "all capabilities) or contain at least one capability name");
}

// TODO test parsing of multiple policies

TEST_MAIN() { TEST_RUN_ALL(); }

