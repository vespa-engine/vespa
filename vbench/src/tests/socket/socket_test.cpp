// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>

using namespace vbench;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();
auto tls_crypto = std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());

using OutputWriter = vespalib::OutputWriter;
using vespalib::CryptoEngine;

const size_t numLines = 100;

struct Agent {
    Stream::UP socket;
    Agent(Stream::UP s) : socket(std::move(s)) {}
    void write(const char *prefix) {
        OutputWriter out(*socket, 32);
        for (size_t i = 0; i < numLines; ++i) {
            out.printf("%s%zu\n", prefix, i);
        }
        out.write("\n");
    }
    void read(const char *prefix) {
        LineReader reader(*socket);
        for (size_t lines = 0; true; ++lines) {
            string line;
            reader.readLine(line);
            if (line.empty()) {
                EXPECT_EQUAL(numLines, lines);
                break;
            }
            EXPECT_EQUAL(strfmt("%s%zu", prefix, lines), line);
        }
    }
};

void verify_socket(CryptoEngine &crypto, ServerSocket &server_socket, size_t thread_id) {
    if (thread_id == 0) { // client
        Agent client(std::make_unique<Socket>(crypto, "localhost", server_socket.port()));
        client.write("client-");
        client.read("server-");
        TEST_BARRIER();   // #1
        LineReader reader(*client.socket);
        string line;
        EXPECT_FALSE(reader.readLine(line));
        EXPECT_TRUE(line.empty());
        EXPECT_TRUE(client.socket->eof());
        EXPECT_FALSE(client.socket->tainted());
    } else {              // server
        Agent server(server_socket.accept(crypto));
        server.read("client-");
        server.write("server-");
        TEST_BARRIER();   // #1
    }
}

TEST_MT_F("socket", 2, ServerSocket()) {
    TEST_DO(verify_socket(*null_crypto, f1, thread_id));
}

TEST_MT_F("secure socket", 2, ServerSocket()) {
    TEST_DO(verify_socket(*tls_crypto, f1, thread_id));
}

TEST_MAIN() { TEST_RUN_ALL(); }
