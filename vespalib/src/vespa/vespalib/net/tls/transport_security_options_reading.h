// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "transport_security_options.h"
#include <memory>

namespace vespalib::net::tls {

// TODO consider renaming TransportSecurityOptions -> TlsConfig

/**
 * Throws IoException if file_path or any files referenced by it can't be accessed
 * Throws IllegalArgumentException if file is not parseable as a valid TLS config file or
 *     if mandatory JSON fields are missing or incomplete.
 */
std::unique_ptr<TransportSecurityOptions> read_options_from_json_file(const vespalib::string& file_path);
// Same properties as read_options_from_json_file()
std::unique_ptr<TransportSecurityOptions> read_options_from_json_string(const vespalib::string& json_data);

}
