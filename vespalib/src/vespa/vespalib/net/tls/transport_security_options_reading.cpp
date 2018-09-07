// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "transport_security_options_reading.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/memory_input.h>

namespace vespalib::net::tls {

/*

 Proposed JSON format for TLS configuration file:

{
  "files": {
    "private-key": "myhost.key",
    "ca-certificates": "my_cas.pem",
    "certificates": "certs.pem"
  },
  // for later:
  "peer-taggers": [
    {
      "requirements":[
        {
          "field": "SAN"
          "must-match": "DNS:foo.bar.baz.*"
        }
      ],
      "tags": ["cluster-peers", "config-server"] // or "roles"? Avoid ambiguities with Athenz concepts
    },
    {
      "requirements":[
        { "field":"CN", "must-match": "config.blarg.*"}
      ],
      "tags": ["config-server"]
    }
  ]
}

 */

using namespace slime;

namespace {

constexpr const char* files_field = "files";
constexpr const char* private_key_field = "private-key";
constexpr const char* ca_certs_field = "ca-certificates";
constexpr const char* certs_field = "certificates";

void verify_referenced_file_exists(const vespalib::string& file_path) {
    if (!fileExists(file_path)) {
        throw IllegalArgumentException(make_string("File '%s' referenced by TLS config does not exist", file_path.c_str()));
    }
}

vespalib::string load_file_referenced_by_field(const Cursor& cursor, const char* field) {
    auto file_path = cursor[field].asString().make_string();
    if (file_path.empty()) {
        throw IllegalArgumentException(make_string("TLS config field '%s' has not been set", field));
    }
    verify_referenced_file_exists(file_path);
    return File::readAll(file_path);
}

std::unique_ptr<TransportSecurityOptions> load_from_input(Input& input) {
    Slime root;
    auto parsed = JsonFormat::decode(input, root);
    if (parsed == 0) {
        throw IllegalArgumentException("Provided TLS config file is not valid JSON");
    }
    auto& files = root[files_field];
    if (files.children() == 0) {
        throw IllegalArgumentException("TLS config root field 'files' is missing or empty");
    }
    // Note: we do no look at the _contents_ of the files; this is deferred to the
    // TLS context code which actually tries to extract key and certificate material
    // from them.
    auto ca_certs = load_file_referenced_by_field(files, ca_certs_field);
    auto certs    = load_file_referenced_by_field(files, certs_field);
    auto priv_key = load_file_referenced_by_field(files, private_key_field);

    return std::make_unique<TransportSecurityOptions>(std::move(ca_certs), std::move(certs), std::move(priv_key));
}

} // anon ns

std::unique_ptr<TransportSecurityOptions> read_options_from_json_string(const vespalib::string& json_data) {
    MemoryInput file_input(json_data);
    return load_from_input(file_input);
}

std::unique_ptr<TransportSecurityOptions> read_options_from_json_file(const vespalib::string& file_path) {
    if (!fileExists(file_path)) {
        throw IllegalArgumentException(make_string("TLS config file '%s' does not exist", file_path.c_str()));
    }
    MappedFileInput file_input(file_path);
    return load_from_input(file_input);
}

}
