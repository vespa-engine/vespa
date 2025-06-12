// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/network/rpcservice.h>
#include <thread>

using namespace mbus;

bool
waitSlobrok(RPCNetwork &network, const string &pattern, size_t num)
{
    for (int i = 0; i < 1000; i++) {
        slobrok::api::IMirrorAPI::SpecList res = network.getMirror().lookup(pattern);
        if (res.size() == num) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    return false;
}

bool
testNullAddress(RPCNetwork &network, const string &pattern)
{
    RPCService service(network.getMirror(), pattern);
    RPCServiceAddress::UP obj = service.make_address();
    bool has_obj(obj);
    EXPECT_FALSE(has_obj);
    return !has_obj;
}

bool
testAddress(RPCNetwork &network, const string &pattern,
            const string &expectedSpec, const string &expectedSession)
{
    RPCService service(network.getMirror(), pattern);
    RPCServiceAddress::UP obj = service.make_address();
    bool has_obj(obj);
    EXPECT_TRUE(has_obj);
    if (!has_obj) {
        return false;
    }
    EXPECT_EQ(expectedSpec, obj->getConnectionSpec());
    EXPECT_EQ(expectedSession, obj->getSessionName());
    return ((expectedSpec == obj->getConnectionSpec()) &&
            (expectedSession == obj->getSessionName()));
}

TEST(ServiceAddressTest, testAddrServiceAddress)
{
    Slobrok slobrok;
    RPCNetwork network(RPCNetworkParams(slobrok.config())
                       .setIdentity(Identity("foo")));
    ASSERT_TRUE(network.start());

    EXPECT_TRUE(testNullAddress(network, "tcp"));
    EXPECT_TRUE(testNullAddress(network, "tcp/"));
    EXPECT_TRUE(testNullAddress(network, "tcp/localhost"));
    EXPECT_TRUE(testNullAddress(network, "tcp/localhost:"));
    EXPECT_TRUE(testNullAddress(network, "tcp/localhost:1977"));
    EXPECT_TRUE(testNullAddress(network, "tcp/localhost:1977/"));
    EXPECT_TRUE(testAddress(network, "tcp/localhost:1977/session", "tcp/localhost:1977", "session"));
    EXPECT_TRUE(testNullAddress(network, "tcp/localhost:/session"));
    EXPECT_TRUE(testNullAddress(network, "tcp/:1977/session"));
    EXPECT_TRUE(testNullAddress(network, "tcp/:/session"));

    network.shutdown();
}

TEST(ServiceAddressTest, testNameServiceAddress)
{
    Slobrok slobrok;
    RPCNetwork network(RPCNetworkParams(slobrok.config())
                       .setIdentity(Identity("foo")));
    ASSERT_TRUE(network.start());

    network.unregisterSession("session");
    ASSERT_TRUE(waitSlobrok(network, "foo/session", 0));
    EXPECT_TRUE(testNullAddress(network, "foo/session"));

    network.registerSession("session");
    ASSERT_TRUE(waitSlobrok(network, "foo/session", 1));
    EXPECT_TRUE(testAddress(network, "foo/session", network.getConnectionSpec().c_str(), "session"));

    network.shutdown();
}

GTEST_MAIN_RUN_ALL_TESTS()
