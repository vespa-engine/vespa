// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/network/rpcserviceaddress.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

TEST(RpcServiceAddressTest, rpcserviceaddress_test) {
    {
        EXPECT_TRUE(RPCServiceAddress("", "bar").isMalformed());
        EXPECT_TRUE(RPCServiceAddress("foo", "bar").isMalformed());
        EXPECT_TRUE(RPCServiceAddress("foo/", "bar").isMalformed());
        EXPECT_TRUE(RPCServiceAddress("/foo", "bar").isMalformed());
    }
    {
        RPCServiceAddress addr("foo/bar/baz", "tcp/foo.com:42");
        EXPECT_TRUE(!addr.isMalformed());
        EXPECT_TRUE(addr.getServiceName() == "foo/bar/baz");
        EXPECT_TRUE(addr.getConnectionSpec() == "tcp/foo.com:42");
        EXPECT_TRUE(addr.getSessionName() == "baz");
    }
    {
        RPCServiceAddress addr("foo/bar", "tcp/foo.com:42");
        EXPECT_TRUE(!addr.isMalformed());
        EXPECT_TRUE(addr.getServiceName() == "foo/bar");
        EXPECT_TRUE(addr.getConnectionSpec() == "tcp/foo.com:42");
        EXPECT_TRUE(addr.getSessionName() == "bar");
    }
    {
        RPCServiceAddress addr("", "tcp/foo.com:42");
        EXPECT_TRUE(addr.isMalformed());
        EXPECT_TRUE(addr.getServiceName() == "");
        EXPECT_TRUE(addr.getConnectionSpec() == "tcp/foo.com:42");
        EXPECT_TRUE(addr.getSessionName() == "");
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
