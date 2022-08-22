// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>

namespace vespalib::test {

/**
 * A socket spec representing "tcp/localhost:123". Used by unit tests
 * performing hostname verification against the tls options created
 * below.
 **/
extern SocketSpec local_spec;

/**
 * Make security options allowing you to talk to yourself using
 * TLS. This is intended for testing purposes only.
 **/
vespalib::net::tls::TransportSecurityOptions make_tls_options_for_testing();

/**
 * Make security options whose authz rules only grant the telemetry capability
 * set to the included certificate.
 *
 * Only useful for testing capability propagation and filtering.
 */
vespalib::net::tls::TransportSecurityOptions make_telemetry_only_capability_tls_options_for_testing();

} // namespace vespalib::test
