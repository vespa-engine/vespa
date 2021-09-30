// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/slobrok/server/slobrokserver.h>
#include <vespa/config/config.h>
#include <vespa/config/common/configcontext.h>
#include <vespa/config-slobroks.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/vespalib/util/host_name.h>
#include <algorithm>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("configure_test");

using slobrok::api::MirrorAPI;
using slobrok::api::RegisterAPI;

using slobrok::ConfigShim;
using slobrok::SlobrokServer;
using slobrok::ConfiguratorFactory;

std::string
createSpec(int port)
{
    if (port == 0) {
        return std::string();
    }
    std::ostringstream str;
    str << "tcp/";
    str << vespalib::HostName::get();
    str << ":";
    str << port;
    return str.str();
}


struct SpecList
{
    MirrorAPI::SpecList _specList;
    SpecList() : _specList() {}
    SpecList(MirrorAPI::SpecList input) : _specList(input) {}
    SpecList &add(const char *name, const char *spec) {
        _specList.push_back(make_pair(std::string(name),
                                      std::string(spec)));
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
    std::string strVal() {
        sort();
        std::string ret = "{";
        for (MirrorAPI::SpecList::iterator it = _specList.begin();
             it != _specList.end();
             ++it)
        {
            ret += "[";
            ret += (*it).first;
            ret += " -> ";
            ret += (*it).second;
            ret += "]";
        };
        ret += "}";
        return ret;
    }
};


bool
compare(MirrorAPI &api, const char *pattern, SpecList expect)
{
    for (int i = 0; i < 600; ++i) {
        SpecList actual(api.lookup(pattern));
        if (actual == expect) {
            return true;
        }
        std::this_thread::sleep_for(100ms);
    }
    SpecList actual(api.lookup(pattern));
    std::cerr << "Actual: " << actual.strVal() << std::endl;
    return false;
}

TEST("configure_test") {

    fnet::frt::StandaloneFRT orb1;
    fnet::frt::StandaloneFRT orb2;

    config::ConfigSet set;
    cloud::config::SlobroksConfigBuilder srv1Builder;
    srv1Builder.slobrok.resize(2);
    srv1Builder.slobrok[0].connectionspec = createSpec(18524);
    srv1Builder.slobrok[1].connectionspec = createSpec(18525);

    cloud::config::SlobroksConfigBuilder srv2Builder;
    srv2Builder.slobrok.resize(2);
    srv2Builder.slobrok[0].connectionspec = createSpec(18524);
    srv2Builder.slobrok[1].connectionspec = createSpec(18525);

    set.addBuilder("server1", &srv1Builder);
    set.addBuilder("server2", &srv2Builder);

    cloud::config::SlobroksConfigBuilder cli1Builder;
    cli1Builder.slobrok.resize(1);
    cli1Builder.slobrok[0].connectionspec = createSpec(18524);

    cloud::config::SlobroksConfigBuilder cli2Builder;
    cli2Builder.slobrok.resize(1);
    cli2Builder.slobrok[0].connectionspec = createSpec(18525);

    cloud::config::SlobroksConfigBuilder cli3Builder;
    cli3Builder.slobrok.resize(1);
    cli3Builder.slobrok[0].connectionspec = createSpec(18524);

    set.addBuilder("client1", &cli1Builder);
    set.addBuilder("client2", &cli2Builder);
    set.addBuilder("client3", &cli3Builder);

    auto cfgCtx = std::make_shared<config::ConfigContext>(set);
    ConfigShim srvConfig1(18524, "server1", cfgCtx);
    ConfigShim srvConfig2(18525, "server2", cfgCtx);

    ConfiguratorFactory cliConfig1(config::ConfigUri("client1", cfgCtx));
    ConfiguratorFactory cliConfig2(config::ConfigUri("client2", cfgCtx));
    ConfiguratorFactory cliConfig3(config::ConfigUri("client3", cfgCtx));

    SlobrokServer serverOne(srvConfig1);
    SlobrokServer serverTwo(srvConfig2);

    MirrorAPI mirror1(orb1.supervisor(), cliConfig3); // NB this one will be changed
    MirrorAPI mirror2(orb2.supervisor(), cliConfig2);

    RegisterAPI reg1(orb1.supervisor(), cliConfig1);
    RegisterAPI reg2(orb2.supervisor(), cliConfig2);

    orb1.supervisor().Listen(18526);
    orb2.supervisor().Listen(18527);
    std::string myspec1 = createSpec(orb1.supervisor().GetListenPort());
    std::string myspec2 = createSpec(orb2.supervisor().GetListenPort());

    reg1.registerName("A");
    reg2.registerName("B");

    EXPECT_TRUE(compare(mirror1, "*", SpecList()
                       .add("A", myspec1.c_str())
                       .add("B", myspec2.c_str())));
    EXPECT_TRUE(compare(mirror2, "*", SpecList()
                       .add("A", myspec1.c_str())
                       .add("B", myspec2.c_str())));

    TEST_FLUSH();

    reg1.unregisterName("A");
    reg2.unregisterName("B");

    EXPECT_TRUE(compare(mirror1, "*", SpecList()));
    EXPECT_TRUE(compare(mirror2, "*", SpecList()));

    srv1Builder.slobrok.resize(1);
    srv1Builder.slobrok[0].connectionspec = createSpec(18524);
    srv2Builder.slobrok.resize(1);
    srv2Builder.slobrok[0].connectionspec = createSpec(18525);
    cfgCtx->reload();

    std::this_thread::sleep_for(6s); // reconfiguration time

    reg1.registerName("A");
    reg2.registerName("B");

    fnet::frt::StandaloneFRT orb3;
    fnet::frt::StandaloneFRT orb4;
    RegisterAPI  reg3(orb3.supervisor(), cliConfig1);
    RegisterAPI  reg4(orb4.supervisor(), cliConfig2);
    orb3.supervisor().Listen(18528);
    orb4.supervisor().Listen(18529);
    std::string myspec3 = createSpec(orb3.supervisor().GetListenPort());
    std::string myspec4 = createSpec(orb4.supervisor().GetListenPort());
    reg3.registerName("B");
    reg4.registerName("A");

    EXPECT_TRUE(compare(mirror1, "*", SpecList()
                       .add("A", myspec1.c_str())
                       .add("B", myspec3.c_str())));
    EXPECT_TRUE(compare(mirror2, "*", SpecList()
                       .add("A", myspec4.c_str())
                       .add("B", myspec2.c_str())));

    TEST_FLUSH();

    // test mirror API reconfiguration
    cli3Builder.slobrok.resize(1);
    cli3Builder.slobrok[0].connectionspec = createSpec(18525);
    cfgCtx->reload();

    EXPECT_TRUE(compare(mirror1, "*", SpecList()
                       .add("A", myspec4.c_str())
                       .add("B", myspec2.c_str())));

    serverOne.stop();
    serverTwo.stop();

    orb4.supervisor().GetTransport()->ShutDown(true);
    orb3.supervisor().GetTransport()->ShutDown(true);
    orb2.supervisor().GetTransport()->ShutDown(true);
    orb1.supervisor().GetTransport()->ShutDown(true);
}

TEST_MAIN() { TEST_RUN_ALL(); }