// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/tls/transport_security_options.h>

namespace vespalib::test {

/**
 * Make security options allowing you to talk to yourself using
 * TLS. This is intended for testing purposes only.
 **/
vespalib::net::tls::TransportSecurityOptions make_tls_options_for_testing();

} // namespace vespalib::test
