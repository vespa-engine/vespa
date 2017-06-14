// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("http connection") {
    ServerSocket serverSocket;
    HttpConnection client(ServerSpec("localhost", serverSocket.port()));
    Stream::UP server = serverSocket.accept();
    EXPECT_TRUE(client.fresh());
    EXPECT_EQUAL("localhost", client.server().host);
    EXPECT_FALSE(client.mayReuse(0.1)); // still fresh
    client.touch(5.0);
    EXPECT_FALSE(client.fresh());
    EXPECT_TRUE(client.mayReuse(5.1));
    server.reset();
    client.stream().obtain(); // trigger eof
    EXPECT_FALSE(client.mayReuse(5.1));
}

TEST_MAIN() { TEST_RUN_ALL(); }
