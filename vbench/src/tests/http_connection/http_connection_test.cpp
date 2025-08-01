// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/net/crypto_engine.h>

using namespace vbench;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();

TEST(HttpConnectionTest, http_connection) {
    ServerSocket serverSocket;
    HttpConnection client(*null_crypto, ServerSpec("localhost", serverSocket.port()));
    Stream::UP server = serverSocket.accept(*null_crypto);
    EXPECT_TRUE(client.fresh());
    EXPECT_EQ("localhost", client.server().host);
    EXPECT_FALSE(client.mayReuse(0.1)); // still fresh
    client.touch(5.0);
    EXPECT_FALSE(client.fresh());
    EXPECT_TRUE(client.mayReuse(5.1));
    server.reset();
    client.stream().obtain(); // trigger eof
    EXPECT_FALSE(client.mayReuse(5.1));
}

GTEST_MAIN_RUN_ALL_TESTS()
