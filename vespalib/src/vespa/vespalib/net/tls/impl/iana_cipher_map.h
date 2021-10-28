// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

#include <vector>

namespace vespalib::net::tls {

/**
 * Returns the OpenSSL cipher suite name for a given IANA cipher suite name, or nullptr if
 * there is no known mapping.
 *
 * Note that this only covers a very restricted subset of the existing IANA ciphers.
 */
const char* iana_cipher_suite_to_openssl(vespalib::stringref iana_name);

/**
 * Returns a vector of all IANA cipher suite names that we support internally.
 * It is guaranteed that any cipher suite name returned from this function will
 * have a non-nullptr return value from iana_cipher_suite_to_openssl(name).
 */
std::vector<vespalib::string> modern_iana_cipher_suites();

}
