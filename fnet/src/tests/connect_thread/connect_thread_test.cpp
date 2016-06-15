// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/connect_thread.h>

struct MyConn : public fnet::ExtConnectable {
    bool connected = false;
    void ext_connect() override { connected = true; }
};

TEST("require that connect thread will connect stuff") {
    std::vector<MyConn> conns(5);
    {
        fnet::ConnectThread thread;
        thread.connect_later(&conns[0]);
        thread.connect_later(&conns[2]);
        thread.connect_later(&conns[4]);
    }
    EXPECT_TRUE(conns[0].connected);
    EXPECT_TRUE(!conns[1].connected);
    EXPECT_TRUE(conns[2].connected);
    EXPECT_TRUE(!conns[3].connected);
    EXPECT_TRUE(conns[4].connected);
}

TEST_MAIN() { TEST_RUN_ALL(); }
