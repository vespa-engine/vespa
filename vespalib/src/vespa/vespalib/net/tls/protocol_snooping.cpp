// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "protocol_snooping.h"
#include <vespa/vespalib/util/size_literals.h>
#include <iostream>
#include <cstdlib>
#include <stdint.h>

namespace vespalib::net::tls::snooping {

namespace {

// Precondition for all helper functions: buffer is at least `min_header_bytes_to_observe()` bytes long

// From RFC 5246:
// 0x16 - Handshake content type byte of TLSCiphertext record
inline bool is_tls_handshake_packet(const char* buf) {
    return (buf[0] == 0x16);
}

// First byte of 2-byte ProtocolVersion, always 3 on TLSv1.2 and v1.3
// Next is the TLS minor version, either 1 or 3 depending on version (though the
// RFCs say it _should_ be 1 for backwards compatibility reasons).
// Yes, the TLS spec says that you should technically ignore the protocol version
// field here, but we want all the signals we can get.
inline bool is_expected_tls_protocol_version(const char* buf) {
    return ((buf[1] == 0x03) && ((buf[2] == 0x01) || (buf[2] == 0x03)));
}

// Length is big endian u16 in bytes 3, 4
inline uint16_t tls_record_length(const char* buf) {
    return (uint16_t(static_cast<unsigned char>(buf[3]) << 8)
            + static_cast<unsigned char>(buf[4]));
}

// First byte of Handshake record in byte 5, which shall be ClientHello (0x01)
inline bool is_client_hello_handshake_record(const char* buf) {
    return (buf[5] == 0x01);
}

// Last 2 bytes are the 2 first big-endian bytes of a 3-byte Handshake
// record length field. No support for records that are large enough that
// the MSB should ever be non-zero.
inline bool client_hello_record_size_within_expected_bounds(const char* buf) {
    return (buf[6] == 0x00);
}

// The byte after the MSB of the 24-bit handshake record size should be equal
// to the most significant byte of the record length value, minus the Handshake
// record header size.
// Again, we make the assumption that ClientHello messages are not fragmented,
// so their max size must be <= 16KiB. This also just happens to be a lower
// number than the minimum FS4/FRT packet type byte at the same location.
// Oooh yeah, leaky abstractions to the rescue!
inline bool handshake_record_size_matches_length(const char* buf, uint16_t length) {
    return (static_cast<unsigned char>(buf[7]) == ((length - 4) >> 8));
}

} // anon ns

TlsSnoopingResult snoop_client_hello_header(const char* buf) noexcept {
    if (!is_tls_handshake_packet(buf)) {
        return TlsSnoopingResult::HandshakeMismatch;
    }
    if (!is_expected_tls_protocol_version(buf)) {
        return TlsSnoopingResult::ProtocolVersionMismatch;
    }
    // Length of TLS record follows. Must be <= 16KiB + 2_Ki (16KiB + 256 on v1.3).
    // We expect that the first record contains _only_ a ClientHello with no coalescing
    // and no fragmentation. This is technically a violation of the TLS spec, but this
    // particular detection logic is only intended to be used against other Vespa nodes
    // where we control frame sizes and where such fragmentation should not take place.
    // We also do not support TLSv1.3 0-RTT which may trigger early data.
    uint16_t length = tls_record_length(buf);
    if ((length < 4) || (length > (16_Ki + 2_Ki))) {
        return TlsSnoopingResult::RecordSizeRfcViolation;
    }
    if (!is_client_hello_handshake_record(buf)) {
        return TlsSnoopingResult::RecordNotClientHello;
    }
    if (!client_hello_record_size_within_expected_bounds(buf)) {
        return TlsSnoopingResult::ClientHelloRecordTooBig;
    }
    if (!handshake_record_size_matches_length(buf, length)) {
        return TlsSnoopingResult::ExpectedRecordSizeMismatch;
    }
    // Hooray! It very probably most likely is a TLS connection! :D
    return TlsSnoopingResult::ProbablyTls;
}

const char* to_string(TlsSnoopingResult result) noexcept {
    switch (result) {
    case TlsSnoopingResult::ProbablyTls:                return "ProbablyTls";
    case TlsSnoopingResult::HandshakeMismatch:          return "HandshakeMismatch";
    case TlsSnoopingResult::ProtocolVersionMismatch:    return "ProtocolVersionMismatch";
    case TlsSnoopingResult::RecordSizeRfcViolation:     return "RecordSizeRfcViolation";
    case TlsSnoopingResult::RecordNotClientHello:       return "RecordNotClientHello";
    case TlsSnoopingResult::ClientHelloRecordTooBig:    return "ClientHelloRecordTooBig";
    case TlsSnoopingResult::ExpectedRecordSizeMismatch: return "ExpectedRecordSizeMismatch";
    }
    abort();
}

std::ostream& operator<<(std::ostream& os, TlsSnoopingResult result) {
    os << to_string(result);
    return os;
}

const char* describe_result(TlsSnoopingResult result) noexcept {
    switch (result) {
    case TlsSnoopingResult::ProbablyTls:
        return "client data matches TLS heuristics, very likely a TLS connection";
    case TlsSnoopingResult::HandshakeMismatch:
        return "not a TLS handshake packet";
    case TlsSnoopingResult::ProtocolVersionMismatch:
        return "ProtocolVersion mismatch";
    case TlsSnoopingResult::RecordSizeRfcViolation:
        return "ClientHello record size is greater than RFC allows";
    case TlsSnoopingResult::RecordNotClientHello:
        return "record is not ClientHello";
    case TlsSnoopingResult::ClientHelloRecordTooBig:
        return "ClientHello record is too big (fragmented?)";
    case TlsSnoopingResult::ExpectedRecordSizeMismatch:
        return "ClientHello vs Handshake header record size mismatch";
    }
    abort();
}

}
