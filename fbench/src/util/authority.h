// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/socket_spec.h>

/**
 * Assemble an SNI (Server Name Indication) spec that will be used
 * when handshaking over TLS. The authority will be used if
 * non-empty. Hostname/port will be used as fall-back. Note that the
 * SNI spec will also be used to generate the Host header used in
 * subsequent HTTP requests.
 *
 * @return sni spec
 * @param authority user-provided authority
 * @param hostname name of the host we are connecting to
 * @param port which port we are connecting to
 * @param use_https are we using https? (TLS)
 **/
vespalib::SocketSpec make_sni_spec(const std::string &authority, const char *hostname, int port, bool use_https);

/**
 * Use an SNI spec to generate a matching Host header to be used in
 * HTTP requests. Note that default port numbers will be omitted.
 *
 * @return host header value
 * @param sni_spec SNI spec
 * @param use_https are we using https? (TLS)
 **/
std::string make_host_header_value(const vespalib::SocketSpec &sni_spec, bool use_https);
