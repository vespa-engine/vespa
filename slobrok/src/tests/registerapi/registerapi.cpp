// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/slobrok/sbregister.h>
#include <vespa/slobrok/server/slobrokserver.h>
#include <vespa/fnet/frt/supervisor.h>
#include <sstream>
#include <algorithm>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("registerapi_test");

using slobrok::api::MirrorAPI;
using slobrok::api::RegisterAPI;
using slobrok::SlobrokServer;


TEST_SETUP(Test);


std::string
createSpec(FRT_Supervisor &orb)
{
    if (orb.GetListenPort() == 0) {
        return std::string();
    }
    std::ostringstream str;
    str << "tcp/";
    str << vespalib::HostName::get();
    str << ":";
    str << orb.GetListenPort();
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
};


bool
compare(MirrorAPI &api, const char *pattern, SpecList expect)
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

int
Test::Main()
{
    TEST_INIT("registerapi_test");

    SlobrokServer mock(18548);
    std::this_thread::sleep_for(300ms);

    cloud::config::SlobroksConfigBuilder slobrokSpecs;
    cloud::config::SlobroksConfig::Slobrok sb;
    sb.connectionspec = "tcp/localhost:18548";
    slobrokSpecs.slobrok.push_back(sb);
    slobrok::ConfiguratorFactory config(config::ConfigUri::createFromInstance(slobrokSpecs));

    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & orb = server.supervisor();
    RegisterAPI reg(orb, config);
    MirrorAPI mirror(orb, config);
    orb.Listen(18549);
    std::string myspec = createSpec(orb);

    reg.registerName("A/x/w");
    EXPECT_TRUE(reg.busy());
    EXPECT_TRUE(compare(mirror, "A/x/w", SpecList().add("A/x/w", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList().add("A/x/w", myspec.c_str())));

    for (int i = 0; i < 30; i++) {
        if (reg.busy()) std::this_thread::sleep_for(100ms);
    }
    EXPECT_TRUE(!reg.busy());

    TEST_FLUSH();
    reg.registerName("B/x");
    EXPECT_TRUE(compare(mirror, "B/x", SpecList().add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList().add("A/x/w", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("C/x/z");
    EXPECT_TRUE(compare(mirror, "C/x/z", SpecList().add("C/x/z", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("D/y/z");
    EXPECT_TRUE(compare(mirror, "D/y/z", SpecList().add("D/y/z", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("E/y");
    EXPECT_TRUE(compare(mirror, "E/y", SpecList().add("E/y", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())
                       .add("E/y", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("F/y/w");
    EXPECT_TRUE(compare(mirror, "F/y/w", SpecList().add("F/y/w", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())
                       .add("E/y", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())
                       .add("F/y/w", myspec.c_str())));


    EXPECT_TRUE(compare(mirror, "*", SpecList()));

    EXPECT_TRUE(compare(mirror, "B/*", SpecList()
                       .add("B/x", myspec.c_str())));

    EXPECT_TRUE(compare(mirror, "*/y", SpecList()
                       .add("E/y", myspec.c_str())));

    EXPECT_TRUE(compare(mirror, "*/x/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())));

    EXPECT_TRUE(compare(mirror, "*/*/z", SpecList()
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())));

    EXPECT_TRUE(compare(mirror, "A/*/z", SpecList()));

    EXPECT_TRUE(compare(mirror, "A/*/w", SpecList()
                       .add("A/x/w", myspec.c_str())));

    TEST_FLUSH();
    reg.unregisterName("E/y");
    reg.unregisterName("C/x/z");
    reg.unregisterName("F/y/w");
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("D/y/z", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("E/y");
    reg.registerName("C/x/z");
    reg.registerName("F/y/w");
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())
                       .add("E/y", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())
                       .add("F/y/w", myspec.c_str())));

    TEST_FLUSH();
    reg.unregisterName("E/y");
    reg.unregisterName("C/x/z");
    reg.unregisterName("F/y/w");
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("D/y/z", myspec.c_str())));

    TEST_FLUSH();
    reg.registerName("E/y");
    reg.registerName("C/x/z");
    reg.registerName("F/y/w");
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", myspec.c_str())
                       .add("E/y", myspec.c_str())));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", myspec.c_str())
                       .add("C/x/z", myspec.c_str())
                       .add("D/y/z", myspec.c_str())
                       .add("F/y/w", myspec.c_str())));

    mock.stop();
    TEST_DONE();
}
