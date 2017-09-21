// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/network/rpcservicepool.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

class Test : public vespalib::TestApp {
private:
    void testMaxSize();

public:
    int Main() override {
        TEST_INIT("servicepool_test");

        testMaxSize(); TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

void
Test::testMaxSize()
{
    Slobrok slobrok;
    RPCNetwork net(RPCNetworkParams().setSlobrokConfig(slobrok.config()));
    RPCServicePool pool(net, 2);
    net.start();

    pool.resolve("foo");
    EXPECT_EQUAL(1u, pool.getSize());
    EXPECT_TRUE(pool.hasService("foo"));
    EXPECT_TRUE(!pool.hasService("bar"));
    EXPECT_TRUE(!pool.hasService("baz"));

    pool.resolve("foo");
    EXPECT_EQUAL(1u, pool.getSize());
    EXPECT_TRUE(pool.hasService("foo"));
    EXPECT_TRUE(!pool.hasService("bar"));
    EXPECT_TRUE(!pool.hasService("baz"));

    pool.resolve("bar");
    EXPECT_EQUAL(2u, pool.getSize());
    EXPECT_TRUE(pool.hasService("foo"));
    EXPECT_TRUE(pool.hasService("bar"));
    EXPECT_TRUE(!pool.hasService("baz"));

    pool.resolve("baz");
    EXPECT_EQUAL(2u, pool.getSize());
    EXPECT_TRUE(!pool.hasService("foo"));
    EXPECT_TRUE(pool.hasService("bar"));
    EXPECT_TRUE(pool.hasService("baz"));

    pool.resolve("bar");
    EXPECT_EQUAL(2u, pool.getSize());
    EXPECT_TRUE(!pool.hasService("foo"));
    EXPECT_TRUE(pool.hasService("bar"));
    EXPECT_TRUE(pool.hasService("baz"));

    pool.resolve("foo");
    EXPECT_EQUAL(2u, pool.getSize());
    EXPECT_TRUE(pool.hasService("foo"));
    EXPECT_TRUE(pool.hasService("bar"));
    EXPECT_TRUE(!pool.hasService("baz"));
}
