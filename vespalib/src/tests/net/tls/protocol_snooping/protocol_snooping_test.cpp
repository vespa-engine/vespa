// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_master.hpp>
#include <vespa/vespalib/net/tls/protocol_snooping.h>

using namespace vespalib;
using namespace vespalib::net::tls::snooping;

TEST(ProtocolSnoopingTest, min_header_bytes_to_observe_is_8) {
    EXPECT_EQ(8u, min_header_bytes_to_observe());
}

TlsSnoopingResult do_snoop(const unsigned char* buf) {
    return snoop_client_hello_header(reinterpret_cast<const char*>(buf));
}

TEST(ProtocolSnoopingTest, well_formed_TLSv1_0_packet_returns_ProbablyTls) {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQ(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, well_formed_TLSv1_2_packet_returns_ProbablyTls) {
    const unsigned char buf[] = { 22, 3, 3, 10, 255, 1, 0, 10 };
    EXPECT_EQ(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, mismatching_handshake_header_byte_1_returns_HandshakeMismatch) {
    const unsigned char buf[] = { 23, 3, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQ(TlsSnoopingResult::HandshakeMismatch, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, mismatching_major_version_byte_returns_ProtocolVersionMismatch) {
    const unsigned char buf[] = { 22, 2, 1, 10, 255, 1, 0, 10 };
    EXPECT_EQ(TlsSnoopingResult::ProtocolVersionMismatch, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, mismatching_minor_version_byte_returns_ProtocolVersionMismatch) {
    const unsigned char buf[] = { 22, 3, 0, 10, 255, 1, 0, 10 };
    EXPECT_EQ(TlsSnoopingResult::ProtocolVersionMismatch, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, oversized_record_returns_RecordSizeRfcViolation) {
    const unsigned char buf1[] = { 22, 3, 1, 255, 255, 1, 0, 10 }; // 64k
    //                                       ^^^^^^^^ big endian record length
    EXPECT_EQ(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf1));

    const unsigned char buf2[] = { 22, 3, 1, 72, 1, 1, 0, 10 }; // 18K+1
    EXPECT_EQ(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf2));
}

TEST(ProtocolSnoopingTest, undersized_record_returns_RecordSizeRfcViolation) {
    const unsigned char buf1[] = { 22, 3, 1, 0, 3, 1, 0, 0 };
    EXPECT_EQ(TlsSnoopingResult::RecordSizeRfcViolation, do_snoop(buf1));
}

TEST(ProtocolSnoopingTest, non_ClientHello_handshake_record_returns_RecordNotClientHello) {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 2, 0, 10 };
    //                                               ^ 1 == ClientHello
    EXPECT_EQ(TlsSnoopingResult::RecordNotClientHello, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, oversized_or_fragmented_ClientHello_record_returns_ClientHelloRecordTooBig) {
    const unsigned char buf[] = { 22, 3, 1, 10, 255, 1, 1, 10 };
    //                                                  ^ MSB of 24-bit record length
    EXPECT_EQ(TlsSnoopingResult::ClientHelloRecordTooBig, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, expected_ClientHello_record_size_mismatch_returns_ExpectedRecordSizeMismatch) {
    const unsigned char buf[] = { 22, 3, 1, 10, 2, 1, 0, 10 };
    //                                                   ^^ bits [8,16) of record length, should be 9
    EXPECT_EQ(TlsSnoopingResult::ExpectedRecordSizeMismatch, do_snoop(buf));
}

TEST(ProtocolSnoopingTest, valid_ClientHello_record_size_with_LSB_lt_4_returns_ProbablyTls) {
    const unsigned char buf[] = { 22, 3, 1, 10, 3, 1, 0, 9 };
    EXPECT_EQ(TlsSnoopingResult::ProbablyTls, do_snoop(buf));
}

GTEST_MAIN_RUN_ALL_TESTS()
