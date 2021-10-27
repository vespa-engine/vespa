// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iana_cipher_map.h"
#include <vespa/vespalib/stllike/hash_fun.h>
#include <utility>
#include <unordered_map>

namespace vespalib::net::tls {

using vespalib::stringref;
using CipherMapType = std::unordered_map<stringref, stringref, vespalib::hash<stringref>>;

namespace {

const CipherMapType& modern_cipher_suites_iana_to_openssl() {
    // Handpicked subset of supported ciphers from https://www.openssl.org/docs/manmaster/man1/ciphers.html
    // based on Modern spec from https://wiki.mozilla.org/Security/Server_Side_TLS
    // For TLSv1.2 we only allow RSA and ECDSA with ephemeral key exchange and GCM.
    // For TLSv1.3 we allow the DEFAULT group ciphers.
    // Note that we _only_ allow AEAD ciphers for either TLS version.
    static CipherMapType ciphers({
         {"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",         "ECDHE-RSA-AES128-GCM-SHA256"},
         {"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",         "ECDHE-RSA-AES256-GCM-SHA384"},
         {"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",       "ECDHE-ECDSA-AES128-GCM-SHA256"},
         {"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",       "ECDHE-ECDSA-AES256-GCM-SHA384"},
         {"TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",   "ECDHE-RSA-CHACHA20-POLY1305"},
         {"TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-ECDSA-CHACHA20-POLY1305"},
         {"TLS_AES_128_GCM_SHA256",                        "TLS13-AES-128-GCM-SHA256"},
         {"TLS_AES_256_GCM_SHA384",                        "TLS13-AES-256-GCM-SHA384"},
         {"TLS_CHACHA20_POLY1305_SHA256",                  "TLS13-CHACHA20-POLY1305-SHA256"}
    });
    return ciphers;
}

} // anon ns

const char* iana_cipher_suite_to_openssl(vespalib::stringref iana_name) {
    const auto& ciphers = modern_cipher_suites_iana_to_openssl();
    auto iter = ciphers.find(iana_name);
    return ((iter != ciphers.end()) ? iter->second.data() : nullptr);
}

std::vector<vespalib::string> modern_iana_cipher_suites() {
    const auto& ciphers = modern_cipher_suites_iana_to_openssl();
    std::vector<vespalib::string> iana_cipher_names;
    iana_cipher_names.reserve(ciphers.size());
    for (const auto& cipher : ciphers) {
        iana_cipher_names.emplace_back(cipher.first);
    }
    return iana_cipher_names;
}

}
