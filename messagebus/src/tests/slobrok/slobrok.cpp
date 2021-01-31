// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/messagebus/network/rpcnetwork.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/vespalib/util/host_name.h>
#include <thread>

using slobrok::api::IMirrorAPI;

using namespace mbus;

string
createSpec(int port)
{
    std::ostringstream str;
    str << "tcp/";
    str << vespalib::HostName::get();
    str << ":";
    str << port;
    return str.str();
}

struct SpecList
{
    IMirrorAPI::SpecList _specList;
    SpecList() : _specList() {}
    SpecList(IMirrorAPI::SpecList input) : _specList(input) {}
    SpecList &add(const string &name, const string &spec) {
        _specList.push_back(std::make_pair(string(name),
                                      string(spec)));
        return *this;
    }
    void sort() {
        std::sort(_specList.begin(), _specList.end());
    }
    bool operator==(SpecList &rhs) { // NB: MUTATE!
        sort();
        rhs.sort();
        return _specList == rhs._specList;
    }
};

bool
compare(const IMirrorAPI &api, const string &pattern, SpecList expect)
{
    for (int i = 0; i < 250; ++i) {
        SpecList actual(api.lookup(pattern));
        if (actual == expect) {
            return true;
        }
        std::this_thread::sleep_for(100ms);
    }
    return false;
}

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("slobrok_test");
    Slobrok slobrok;
    RPCNetwork net1(RPCNetworkParams(slobrok.config())
                    .setIdentity(Identity("net/a")));
    RPCNetwork net2(RPCNetworkParams(slobrok.config())
                    .setIdentity(Identity("net/b")));
    RPCNetwork net3(RPCNetworkParams(slobrok.config())
                    .setIdentity(Identity("net/c")));
    ASSERT_TRUE(net1.start());
    ASSERT_TRUE(net2.start());
    ASSERT_TRUE(net3.start());
    string spec1 = createSpec(net1.getPort());
    string spec2 = createSpec(net2.getPort());
    string spec3 = createSpec(net3.getPort());

    net1.registerSession("foo");
    net2.registerSession("foo");
    net2.registerSession("bar");
    net3.registerSession("foo");
    net3.registerSession("bar");
    net3.registerSession("baz");

    EXPECT_TRUE(compare(net1.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/b/bar", spec2)
                       .add("net/c/foo", spec3)
                       .add("net/c/bar", spec3)
                       .add("net/c/baz", spec3)));
    EXPECT_TRUE(compare(net2.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/b/bar", spec2)
                       .add("net/c/foo", spec3)
                       .add("net/c/bar", spec3)
                       .add("net/c/baz", spec3)));
    EXPECT_TRUE(compare(net3.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/b/bar", spec2)
                       .add("net/c/foo", spec3)
                       .add("net/c/bar", spec3)
                       .add("net/c/baz", spec3)));

    net2.unregisterSession("bar");
    net3.unregisterSession("bar");
    net3.unregisterSession("baz");

    EXPECT_TRUE(compare(net1.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/c/foo", spec3)));
    EXPECT_TRUE(compare(net2.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/c/foo", spec3)));
    EXPECT_TRUE(compare(net3.getMirror(), "*/*/*", SpecList()
                       .add("net/a/foo", spec1)
                       .add("net/b/foo", spec2)
                       .add("net/c/foo", spec3)));

    net3.shutdown();
    net2.shutdown();
    net1.shutdown();
    TEST_DONE();
}
