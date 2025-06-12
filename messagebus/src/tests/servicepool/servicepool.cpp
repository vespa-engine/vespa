// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/network/rpcservicepool.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

TEST(ServicePoolTest, testMaxSize)
{
    Slobrok slobrok;
    TestServer me(Identity("me"), RoutingSpec(), slobrok);
    RPCNetwork & net = me.net;
    net.registerSession("foo");
    net.registerSession("bar");
    net.registerSession("baz");
    me.waitSlobrok("me/foo");
    me.waitSlobrok("me/bar");
    me.waitSlobrok("me/baz");
    RPCServicePool pool(net.getMirror(), 2);

    RPCServiceAddress::UP addr = pool.resolve("me/foo");
    EXPECT_EQ(1u, pool.getSize());
    EXPECT_TRUE(pool.hasService("me/foo"));
    EXPECT_TRUE(!pool.hasService("me/bar"));
    EXPECT_TRUE(!pool.hasService("me/baz"));

    addr = pool.resolve("me/foo");
    EXPECT_EQ(1u, pool.getSize());
    EXPECT_TRUE(pool.hasService("me/foo"));
    EXPECT_TRUE(!pool.hasService("me/bar"));
    EXPECT_TRUE(!pool.hasService("me/baz"));

    addr = pool.resolve("me/bar");
    EXPECT_EQ(2u, pool.getSize());
    EXPECT_TRUE(pool.hasService("me/foo"));
    EXPECT_TRUE(pool.hasService("me/bar"));
    EXPECT_TRUE(!pool.hasService("me/baz"));

    addr = pool.resolve("me/baz");
    EXPECT_EQ(2u, pool.getSize());
    EXPECT_TRUE(!pool.hasService("me/foo"));
    EXPECT_TRUE(pool.hasService("me/bar"));
    EXPECT_TRUE(pool.hasService("me/baz"));

    addr = pool.resolve("me/bar");
    EXPECT_EQ(2u, pool.getSize());
    EXPECT_TRUE(!pool.hasService("me/foo"));
    EXPECT_TRUE(pool.hasService("me/bar"));
    EXPECT_TRUE(pool.hasService("me/baz"));

    addr = pool.resolve("me/foo");
    EXPECT_EQ(2u, pool.getSize());
    EXPECT_TRUE(pool.hasService("me/foo"));
    EXPECT_TRUE(pool.hasService("me/bar"));
    EXPECT_TRUE(!pool.hasService("me/baz"));
}

GTEST_MAIN_RUN_ALL_TESTS()
