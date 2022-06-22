// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "transport_security_options_reading.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/memory_input.h>
#include <vespa/vespalib/net/tls/capability_set.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace vespalib::net::tls {

/*

 Proposed JSON format for TLS configuration file:

{
  "files": {
    "private-key": "myhost.key",
    "ca-certificates": "my_cas.pem",
    "certificates": "certs.pem"
  },
  "authorized-peers": [
    {
      "required-credentials":[
        { "field":"CN", "must-match": "*.config.blarg"},
        { "field":"SAN_DNS", "must-match": "*.fancy.config.blarg"}
      ],
      "name": "funky config servers",
      "capabilities": ["vespa.content.coolstuff"]
    }
  ]
}

 */

using namespace slime::convenience;

namespace {

void verify_referenced_file_exists(const vespalib::string& file_path) {
    if (!fileExists(file_path)) {
        throw IllegalArgumentException(make_string("File '%s' referenced by TLS config does not exist", file_path.c_str()));
    }
}

vespalib::string load_file_referenced_by_field(const Inspector& cursor, const char* field) {
    auto file_path = cursor[field].asString().make_string();
    if (file_path.empty()) {
        throw IllegalArgumentException(make_string("TLS config field '%s' has not been set", field));
    }
    verify_referenced_file_exists(file_path);
    return File::readAll(file_path);
}

RequiredPeerCredential parse_peer_credential(const Inspector& req_entry) {
    auto field_string = req_entry["field"].asString().make_string();
    RequiredPeerCredential::Field field;
    if (field_string == "CN") {
        field = RequiredPeerCredential::Field::CN;
    } else if (field_string == "SAN_DNS") {
        field = RequiredPeerCredential::Field::SAN_DNS;
    } else if (field_string == "SAN_URI") {
        field = RequiredPeerCredential::Field::SAN_URI;
    } else {
        throw IllegalArgumentException(make_string(
                "Unsupported credential field type: '%s'. Supported are: CN, SAN_DNS",
                field_string.c_str()));
    }
    auto match = req_entry["must-match"].asString().make_string();
    return RequiredPeerCredential(field, std::move(match));
}

std::vector<RequiredPeerCredential> parse_peer_credentials(const Inspector& creds) {
    if (creds.children() == 0) {
        throw IllegalArgumentException("\"required-credentials\" array can't be empty (would allow all peers)");
    }
    std::vector<RequiredPeerCredential> required_creds;
    for (size_t i = 0; i < creds.children(); ++i) {
        required_creds.emplace_back(parse_peer_credential(creds[i]));
    }
    return required_creds;
}

CapabilitySet parse_capabilities(const Inspector& caps) {
    CapabilitySet capabilities;
    if (caps.valid() && (caps.children() == 0)) {
        throw IllegalArgumentException("\"capabilities\" array must either be not present (implies "
                                       "all capabilities) or contain at least one capability name");
    } else if (caps.valid()) {
        for (size_t i = 0; i < caps.children(); ++i) {
            // TODO warn if resolve_and_add returns false; means capability is unknown!
            (void)capabilities.resolve_and_add(caps[i].asString().make_string());
        }
    } else {
        // If no capabilities are specified, all are implicitly granted.
        // This avoids breaking every legacy mTLS app ever.
        capabilities = CapabilitySet::make_with_all_capabilities();
    }
    return capabilities;
}

PeerPolicy parse_peer_policy(const Inspector& peer_entry) {
    auto required_creds = parse_peer_credentials(peer_entry["required-credentials"]);
    auto capabilities   = parse_capabilities(peer_entry["capabilities"]);
    return {std::move(required_creds), std::move(capabilities)};
}

AuthorizedPeers parse_authorized_peers(const Inspector& authorized_peers) {
    if (!authorized_peers.valid()) {
        // If there's no "authorized-peers" object, valid CA signing is sufficient.
        return AuthorizedPeers::allow_all_authenticated();
    }
    if (authorized_peers.children() == 0) {
        throw IllegalArgumentException("\"authorized-peers\" must either be not present (allows "
                                       "all peers with valid certificates) or a non-empty array");
    }
    std::vector<PeerPolicy> policies;
    for (size_t i = 0; i < authorized_peers.children(); ++i) {
        policies.emplace_back(parse_peer_policy(authorized_peers[i]));
    }
    return AuthorizedPeers(std::move(policies));
}

std::vector<vespalib::string> parse_accepted_ciphers(const Inspector& accepted_ciphers) {
    if (!accepted_ciphers.valid()) {
        return {};
    }
    std::vector<vespalib::string> ciphers;
    for (size_t i = 0; i < accepted_ciphers.children(); ++i) {
        ciphers.emplace_back(accepted_ciphers[i].asString().make_string());
    }
    return ciphers;
}

std::unique_ptr<TransportSecurityOptions> load_from_input(Input& input) {
    Slime root;
    auto parsed = slime::JsonFormat::decode(input, root);
    if (parsed == 0) {
        throw IllegalArgumentException("Provided TLS config file is not valid JSON");
    }
    auto& files = root["files"];
    if (files.fields() == 0) {
        throw IllegalArgumentException("TLS config root field 'files' is missing or empty");
    }
    // Note: we do no look at the _contents_ of the files; this is deferred to the
    // TLS context code which actually tries to extract key and certificate material
    // from them.
    auto ca_certs = load_file_referenced_by_field(files, "ca-certificates");
    auto certs    = load_file_referenced_by_field(files, "certificates");
    auto priv_key = load_file_referenced_by_field(files, "private-key");
    auto authorized_peers = parse_authorized_peers(root["authorized-peers"]);
    auto accepted_ciphers = parse_accepted_ciphers(root["accepted-ciphers"]);
    // FIXME this is temporary until we know it won't break a bunch of things!
    // It's still possible to explicitly enable hostname validation by setting this to false.
    bool disable_hostname_validation = true;
    if (root["disable-hostname-validation"].valid()) {
        disable_hostname_validation = root["disable-hostname-validation"].asBool();
    }

    auto options = std::make_unique<TransportSecurityOptions>(
            TransportSecurityOptions::Params()
                .ca_certs_pem(ca_certs)
                .cert_chain_pem(certs)
                .private_key_pem(priv_key)
                .authorized_peers(std::move(authorized_peers))
                .accepted_ciphers(std::move(accepted_ciphers))
                .disable_hostname_validation(disable_hostname_validation));
    secure_memzero(&priv_key[0], priv_key.size());
    return options;
}

} // anon ns

std::unique_ptr<TransportSecurityOptions> read_options_from_json_string(const vespalib::string& json_data) {
    MemoryInput file_input(json_data);
    return load_from_input(file_input);
}

std::unique_ptr<TransportSecurityOptions> read_options_from_json_file(const vespalib::string& file_path) {
    MappedFileInput file_input(file_path);
    if (!file_input.valid()) {
        throw IllegalArgumentException(make_string("TLS config file '%s' could not be read", file_path.c_str()));
    }
    return load_from_input(file_input);
}

}
