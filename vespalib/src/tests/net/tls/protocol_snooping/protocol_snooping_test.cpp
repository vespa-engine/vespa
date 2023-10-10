// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/tls/protocol_snooping.h>

using namespace vespalib;
using namespace vespalib::net::tls::snooping;

TEST("min_header_bytes_to_observe() is 8") {
    EXPECT_EQUAL(8u, min_header_bytes_to_observe());
}

TlsSnoopingResult do_snoop(const unsigned char* buf) {
    return snoop_client_hello_header(reinterpret_cast<const char*>(buf));
}

TEST("Well-formed TLSv1.0 packet returns ProbablyTls") {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQUAL(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

TEST("Well-formed TLSv1.2 packet returns ProbablyTls") {
    const unsigned char buf[] = { 22, 3, 3, 10, 255, 1, 0, 10 };
    EXPECT_EQUAL(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

TEST("Mismatching handshake header byte 1 returns HandshakeMismatch") {
    const unsigned char buf[] = { 23, 3, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQUAL(TlsSnoopingResult::HandshakeMismatch, do_snoop(buf));
}

TEST("Mismatching major version byte returns ProtocolVersionMismatch") {
    const unsigned char buf[] = { 22, 2, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQUAL(TlsSnoopingResult::ProtocolVersionMismatch, do_snoop(buf));
}

TEST("Mismatching minor version byte returns ProtocolVersionMismatch") {
    const unsigned char buf[] = { 22, 3, 0, 10, 255, 1, 0, 10 };
    EXPECT_EQUAL(TlsSnoopingResult::ProtocolVersionMismatch, do_snoop(buf));
}

TEST("Oversized record returns RecordSizeRfcViolation") {
    const unsigned char buf1[] = { 22, 3, 1, 255, 255, 1, 0, 10 }; // 64k
    //                                       ^^^^^^^^ big endian record length
    EXPECT_EQUAL(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf1));

    const unsigned char buf2[] = { 22, 3, 1, 72, 1, 1, 0, 10 }; // 18K+1
    EXPECT_EQUAL(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf2));
}

TEST("Undersized record returns RecordSizeRfcViolation") {
    const unsigned char buf1[] = { 22, 3, 1, 0, 3, 1, 0, 0 };
    EXPECT_EQUAL(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf1));
}

TEST("Non-ClientHello handshake record returns RecordNotClientHello") {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 2, 0, 10 };
    //                                               ^ 1 == ClientHello
    EXPECT_EQUAL(TlsSnoopingResult::RecordNotClientHello, do_snoop(buf));
}

TEST("Oversized or fragmented ClientHello record returns ClientHelloRecordTooBig") {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 1, 1, 10 };
    //                                                  ^ MSB of 24-bit record length
    EXPECT_EQUAL(TlsSnoopingResult::ClientHelloRecordTooBig, do_snoop(buf));
}

TEST("Expected ClientHello record size mismatch returns ExpectedRecordSizeMismatch") {
    const unsigned char buf[] = { 22, 3, 1, 10, 2, 1, 0, 10 };
    //                                                   ^^ bits [8,16) of record length, should be 9
    EXPECT_EQUAL(TlsSnoopingResult::ExpectedRecordSizeMismatch, do_snoop(buf));
}

TEST("Valid ClientHello record size with LSB < 4 returns ProbablyTls") {
    const unsigned char buf[] = { 22, 3, 1, 10, 3, 1, 0, 9 };
    EXPECT_EQUAL(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

TEST_MAIN() { TEST_RUN_ALL(); }
