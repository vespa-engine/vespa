// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <stddef.h>

namespace vespalib::net::tls::snooping {

constexpr inline size_t min_header_bytes_to_observe() { return 8; }

enum class TlsSnoopingResult {
    ProbablyTls, // Very safe to assume TLSv1.x client
    HandshakeMismatch, // Almost guaranteed to trigger for plaintext RPC
    ProtocolVersionMismatch,
    RecordSizeRfcViolation,
    RecordNotClientHello,
    ClientHelloRecordTooBig,
    ExpectedRecordSizeMismatch
};

const char* to_string(TlsSnoopingResult) noexcept;
std::ostream& operator<<(std::ostream& os, TlsSnoopingResult);

// Precondition: buf is at least `min_header_bytes_to_observe()` bytes long. This is the minimum amount
// of bytes always sent for a packet in our existing plaintext production protocols and
// therefore the maximum we can expect to always be present.
// Yes, this is a pragmatic and delightfully leaky abstraction.
TlsSnoopingResult snoop_client_hello_header(const char* buf) noexcept;

const char* describe_result(TlsSnoopingResult result) noexcept;

}
