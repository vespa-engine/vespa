// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/tls/tls_context.h>
#include <vespa/vespalib/net/tls/transport_security_options.h>
#include <vespa/vespalib/net/tls/crypto_codec.h>
#include <iostream>
#include <stdlib.h>

using namespace vespalib;
using namespace vespalib::net::tls;

/*
 * Generated with the following commands:
 *
 * openssl ecparam -name prime256v1 -genkey -out ca.key
 *
 * openssl req -new -x509 -nodes -key ca.key \
 *    -sha256 -out ca.pem \
 *    -subj '/C=US/L=LooneyVille/O=ACME/OU=ACME test CA/CN=acme.example.com' \
 *    -days 10000
 *
 * openssl ecparam -name prime256v1 -genkey -out host.key
 *
 * openssl req -new -key host.key -out host.csr \
 *    -subj '/C=US/L=LooneyVille/O=Wile. E. Coyote, Ltd./CN=wile.example.com' \
 *    -sha256
 *
 * openssl x509 -req -in host.csr \
 *   -CA ca.pem \
 *   -CAkey ca.key \
 *   -CAcreateserial \
 *   -out host.pem \
 *   -days 10000 \
 *   -sha256
 *
 * TODO generate keypairs and certs at test-time to avoid any hard-coding
 * There certs are valid until 2046, so that buys us some time..!
 */

// ca.pem
constexpr const char* ca_pem = R"(-----BEGIN CERTIFICATE-----
MIIBuDCCAV4CCQDpVjQIixTxvDAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODA4MzEx
MDU3NDVaFw00NjAxMTYxMDU3NDVaMGQxCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTENMAsGA1UECgwEQUNNRTEVMBMGA1UECwwMQUNNRSB0ZXN0IENB
MRkwFwYDVQQDDBBhY21lLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAE1L7IzCN5pbyVnBATIHieuxq+hf9kWyn5yfjkXMhD52T5ITz1huq4nbiN
YtRoRP7XmipI60R/uiCHzERcsVz4rDAKBggqhkjOPQQDAgNIADBFAiEA6wmZDBca
y0aJ6ABtjbjx/vlmVDxdkaSZSgO8h2CkvIECIFktCkbZhDFfSvbqUScPOGuwkdGQ
L/EW2Bxp+1BPcYoZ
-----END CERTIFICATE-----)";

// host.pem
constexpr const char* cert_pem = R"(-----BEGIN CERTIFICATE-----
MIIBsTCCAVgCCQD6GfDh0ltpsjAKBggqhkjOPQQDAjBkMQswCQYDVQQGEwJVUzEU
MBIGA1UEBwwLTG9vbmV5VmlsbGUxDTALBgNVBAoMBEFDTUUxFTATBgNVBAsMDEFD
TUUgdGVzdCBDQTEZMBcGA1UEAwwQYWNtZS5leGFtcGxlLmNvbTAeFw0xODA4MzEx
MDU3NDVaFw00NjAxMTYxMDU3NDVaMF4xCzAJBgNVBAYTAlVTMRQwEgYDVQQHDAtM
b29uZXlWaWxsZTEeMBwGA1UECgwVV2lsZS4gRS4gQ295b3RlLCBMdGQuMRkwFwYD
VQQDDBB3aWxlLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
e+Y4hxt66em0STviGUj6ZDbxzoLoubXWRml8JDFrEc2S2433KWw2npxYSKVCyo3a
/Vo33V8/H0WgOXioKEZJxDAKBggqhkjOPQQDAgNHADBEAiAN+87hQuGv3z0Ja2BV
b8PHq2vp3BJHjeMuxWu4BFPn0QIgYlvIHikspgGatXRNMZ1gPC0oCccsJFcie+Cw
zL06UPI=
-----END CERTIFICATE-----)";

// host.key
constexpr const char* key_pem = R"(-----BEGIN EC PARAMETERS-----
BggqhkjOPQMBBw==
-----END EC PARAMETERS-----
-----BEGIN EC PRIVATE KEY-----
MHcCAQEEID6di2PFYn8hPrxPbkFDGkSqF+K8L520In7nx3g0jwzOoAoGCCqGSM49
AwEHoUQDQgAEe+Y4hxt66em0STviGUj6ZDbxzoLoubXWRml8JDFrEc2S2433KWw2
npxYSKVCyo3a/Vo33V8/H0WgOXioKEZJxA==
-----END EC PRIVATE KEY-----)";

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
    auto tls_opts = TransportSecurityOptions(ca_pem, cert_pem, key_pem);
    auto tls_ctx = TlsContext::create_default_context(tls_opts);
    auto client = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Client);
    auto server = CryptoCodec::create_default_codec(*tls_ctx, CryptoCodec::Mode::Server);

    EXPECT_TRUE(complete_handshake(*client, *server));
}

TEST("client can send single data frame to server after handshake") {
    // TODO move to fixture
    auto tls_opts = TransportSecurityOptions(ca_pem, cert_pem, key_pem);
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
