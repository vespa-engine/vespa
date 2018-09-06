// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/tls/tls_context.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <iostream>
#include <stdlib.h>

using namespace vespalib;
using namespace vespalib::net::tls;

const char* decode_state_to_str(DecodeResult::State state) noexcept {
    switch (state) {
        case DecodeResult::State::Failed: return "Broken";
        case DecodeResult::State::OK:     return "OK";
        case DecodeResult::State::NeedsMorePeerData: return "NeedsMorePeerData";
        default:
            abort();
    }
}

const char* hs_state_to_str(HandshakeResult::State state) noexcept {
    switch (state) {
    case HandshakeResult::State::Failed: return "Broken";
    case HandshakeResult::State::Done:   return "Done";
    case HandshakeResult::State::NeedsMorePeerData: return "NeedsMorePeerData";
    default:
        abort();
    }
}

void log_handshake_result(const char* mode, const HandshakeResult& res) {
    fprintf(stderr, "(handshake) %s consumed %zu peer bytes, wrote %zu peer bytes. State: %s\n",
            mode, res.bytes_consumed, res.bytes_produced,
            hs_state_to_str(res.state));
}

void log_encode_result(const char* mode, const EncodeResult& res) {
    fprintf(stderr, "(encode) %s read %zu plaintext, wrote %zu cipher. State: %s\n",
            mode, res.bytes_consumed, res.bytes_produced,
            res.failed ? "Broken! D:" : "OK");
}

void log_decode_result(const char* mode, const DecodeResult& res) {
    fprintf(stderr, "(decode) %s read %zu cipher, wrote %zu plaintext. State: %s\n",
            mode, res.bytes_consumed, res.bytes_produced,
            decode_state_to_str(res.state));
}

bool complete_handshake(CryptoCodec& client, CryptoCodec& server) {
    // Not using vespalib::string here since it doesn't have erase(iter, length) implemented.
    std::string client_to_server_buf;
    std::string server_to_client_buf;

    HandshakeResult cli_res;
    HandshakeResult serv_res;
    while (!(cli_res.done() && serv_res.done())) {
        client_to_server_buf.resize(client.min_encode_buffer_size());
        server_to_client_buf.resize(server.min_encode_buffer_size());

        cli_res = client.handshake(server_to_client_buf.data(), serv_res.bytes_produced,
                                   client_to_server_buf.data(), client_to_server_buf.size());
        log_handshake_result("client", cli_res);
        server_to_client_buf.erase(server_to_client_buf.begin(), server_to_client_buf.begin() + cli_res.bytes_consumed);

        serv_res = server.handshake(client_to_server_buf.data(), cli_res.bytes_produced,
                                    server_to_client_buf.data(), server_to_client_buf.size());
        log_handshake_result("server", serv_res);
        client_to_server_buf.erase(client_to_server_buf.begin(), client_to_server_buf.begin() + serv_res.bytes_consumed);

        if (cli_res.failed() || serv_res.failed()) {
            return false;
        }
    }
    return true;
}

TEST("client and server can complete handshake") {
    // TODO move to fixture
    auto tls_opts = vespalib::test::make_tls_options_for_testing();
    auto tls_ctx = TlsContext::create_default_context(tls_opts);
    auto client = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Client);
    auto server = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Server);

    EXPECT_TRUE(complete_handshake(*client, *server));
}

TEST("client can send single data frame to server after handshake") {
    // TODO move to fixture
    auto tls_opts = vespalib::test::make_tls_options_for_testing();
    auto tls_ctx = TlsContext::create_default_context(tls_opts);
    auto client = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Client);
    auto server = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Server);

    ASSERT_TRUE(complete_handshake(*client, *server));

    std::string client_to_server_buf;
    client_to_server_buf.resize(client->min_encode_buffer_size());

    std::string client_plaintext = "Hellooo world! :D";
    auto cli_res = client->encode(client_plaintext.data(), client_plaintext.size(),
                                  client_to_server_buf.data(), client_to_server_buf.size());
    log_encode_result("client", cli_res);

    std::string server_plaintext_out;
    server_plaintext_out.resize(server->min_decode_buffer_size());
    auto serv_res = server->decode(client_to_server_buf.data(), cli_res.bytes_produced,
                                   server_plaintext_out.data(), server_plaintext_out.size());
    log_decode_result("server", serv_res);

    ASSERT_FALSE(cli_res.failed);
    ASSERT_FALSE(serv_res.failed());

    ASSERT_TRUE(serv_res.state == DecodeResult::State::OK);
    std::string data_received(server_plaintext_out.data(), serv_res.bytes_produced);
    EXPECT_EQUAL(client_plaintext, data_received);
}

/*
 * TODO tests:
 *  - full duplex read/write
 *  - read and write of > frame size data
 *  - handshakes with multi frame writes
 *  - completed handshake with pipelined data frame
 *  - short ciphertext reads on decode
 *  - short plaintext writes on decode (.. if we even want to support this..)
 *  - short ciphertext write on encode
 *  - peer certificate validation on server
 *  - peer certificate validation on client
 *  - detection of peer shutdown session
 */

TEST_MAIN() { TEST_RUN_ALL(); }
