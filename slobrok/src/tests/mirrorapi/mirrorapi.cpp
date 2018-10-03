// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/config-slobroks.h>
#include <algorithm>
#include <vespa/slobrok/server/slobrokserver.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>

#include <vespa/log/log.h>
LOG_SETUP("mirrorapi_test");

using slobrok::api::MirrorAPI;
using slobrok::SlobrokServer;

TEST_SETUP(Test);

//-----------------------------------------------------------------------------

class Server : public FRT_Invokable
{
private:
    FRT_Supervisor _orb;
    std::string    _name;
    std::string    _slobrokSpec;

public:
    Server(std::string name, int port, std::string slobrokSpec);
    ~Server();
    void rpc_listNamesServed(FRT_RPCRequest *req);
    void reg();
};


Server::Server(std::string name, int port, std::string slobrokSpec)
    : _orb(),
      _name(name),
      _slobrokSpec(slobrokSpec)
{
    {
        FRT_ReflectionBuilder rb(&_orb);
        //---------------------------------------------------------------------
        rb.DefineMethod("slobrok.callback.listNamesServed", "", "S",
                        FRT_METHOD(Server::rpc_listNamesServed), this);
        rb.MethodDesc("Look up a rpcserver");
        rb.ReturnDesc("names", "The rpcserver names on this server");
        //---------------------------------------------------------------------
    }
    _orb.Listen(port);
    _orb.Start();
}


void
Server::reg()
{
    char spec[64];
    sprintf(spec, "tcp/localhost:%d", _orb.GetListenPort());

    FRT_RPCRequest *req = _orb.AllocRPCRequest();
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString(_name.c_str());
    req->GetParams()->AddString(spec);

    FRT_Target *sb = _orb.GetTarget(_slobrokSpec.c_str());
    sb->InvokeSync(req, 5.0);
    sb->SubRef();
    req->SubRef();
}


void
Server::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(1);
    dst.SetString(&names[0], _name.c_str());
}


Server::~Server()
{
    _orb.ShutDown(true);
}

//-----------------------------------------------------------------------------

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
        FastOS_Thread::Sleep(100);
    }
    return false;
}


int
Test::Main()
{
    TEST_INIT("mirrorapi_test");

    SlobrokServer mock(18501);
    FastOS_Thread::Sleep(300);

    Server a("A/x/w", 18502, "tcp/localhost:18501");
    Server b("B/x",   18503, "tcp/localhost:18501");
    Server c("C/x/z", 18504, "tcp/localhost:18501");
    Server d("D/y/z", 18505, "tcp/localhost:18501");
    Server e("E/y",   18506, "tcp/localhost:18501");
    Server f("F/y/w", 18507, "tcp/localhost:18501");

    cloud::config::SlobroksConfigBuilder specBuilder;
    cloud::config::SlobroksConfig::Slobrok slobrok;
    slobrok.connectionspec = "tcp/localhost:18501";
    specBuilder.slobrok.push_back(slobrok);
    FRT_Supervisor orb;
    MirrorAPI mirror(orb, config::ConfigUri::createFromInstance(specBuilder));
    EXPECT_TRUE(!mirror.ready());
    orb.Start();
    FastOS_Thread::Sleep(1000);

    a.reg();
    EXPECT_TRUE(compare(mirror, "A/x/w", SpecList().add("A/x/w", "tcp/localhost:18502")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList().add("A/x/w", "tcp/localhost:18502")));
    EXPECT_TRUE(compare(mirror, "*/*/w*", SpecList().add("A/x/w", "tcp/localhost:18502")));
    EXPECT_TRUE(compare(mirror, "A**", SpecList().add("A/x/w", "tcp/localhost:18502")));
    EXPECT_TRUE(compare(mirror, "**", SpecList().add("A/x/w", "tcp/localhost:18502")));
    EXPECT_TRUE(mirror.ready());

    TEST_FLUSH();
    b.reg();
    EXPECT_TRUE(compare(mirror, "B/x", SpecList().add("B/x", "tcp/localhost:18503")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", "tcp/localhost:18503")));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList().add("A/x/w", "tcp/localhost:18502")));

    TEST_FLUSH();
    c.reg();
    EXPECT_TRUE(compare(mirror, "C/x/z", SpecList().add("C/x/z", "tcp/localhost:18504")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", "tcp/localhost:18503")));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")
                       .add("C/x/z", "tcp/localhost:18504")));

    TEST_FLUSH();
    d.reg();
    EXPECT_TRUE(compare(mirror, "D/y/z", SpecList().add("D/y/z", "tcp/localhost:18505")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList().add("B/x", "tcp/localhost:18503")));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")
                       .add("C/x/z", "tcp/localhost:18504")
                       .add("D/y/z", "tcp/localhost:18505")));

    TEST_FLUSH();
    e.reg();
    EXPECT_TRUE(compare(mirror, "E/y", SpecList().add("E/y", "tcp/localhost:18506")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", "tcp/localhost:18503")
                       .add("E/y", "tcp/localhost:18506")));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")
                       .add("C/x/z", "tcp/localhost:18504")
                       .add("D/y/z", "tcp/localhost:18505")));

    TEST_FLUSH();
    f.reg();
    EXPECT_TRUE(compare(mirror, "F/y/w", SpecList().add("F/y/w", "tcp/localhost:18507")));
    EXPECT_TRUE(compare(mirror, "*/*", SpecList()
                       .add("B/x", "tcp/localhost:18503")
                       .add("E/y", "tcp/localhost:18506")));
    EXPECT_TRUE(compare(mirror, "*/*/*", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")
                       .add("C/x/z", "tcp/localhost:18504")
                       .add("D/y/z", "tcp/localhost:18505")
                       .add("F/y/w", "tcp/localhost:18507")));


    EXPECT_TRUE(compare(mirror, "*", SpecList()));

    EXPECT_TRUE(compare(mirror, "B/*", SpecList()
                       .add("B/x", "tcp/localhost:18503")));

    EXPECT_TRUE(compare(mirror, "*/y", SpecList()
                       .add("E/y", "tcp/localhost:18506")));

    EXPECT_TRUE(compare(mirror, "*/x/*", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")
                       .add("C/x/z", "tcp/localhost:18504")));

    EXPECT_TRUE(compare(mirror, "*/*/z", SpecList()
                       .add("C/x/z", "tcp/localhost:18504")
                       .add("D/y/z", "tcp/localhost:18505")));

    EXPECT_TRUE(compare(mirror, "A/*/z", SpecList()));

    EXPECT_TRUE(compare(mirror, "A/*/w", SpecList()
                       .add("A/x/w", "tcp/localhost:18502")));

    mock.stop();
    orb.ShutDown(true);
    TEST_DONE();
}
