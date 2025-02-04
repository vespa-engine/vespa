// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/file_acquirer/file_acquirer.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace config;

struct ServerFixture : FRT_Invokable {
    fnet::frt::StandaloneFRT server;
    FNET_Transport transport;
    FRT_Supervisor &orb;
    std::string spec;

    void init_rpc() {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("waitFor", "s", "s", FRT_METHOD(ServerFixture::RPC_waitFor), this);
        rb.MethodDesc("wait for and resolve file reference");
        rb.ParamDesc("file_ref", "file reference to wait for and resolve");
        rb.ReturnDesc("file_path", "actual path to the requested file");
    }

    ServerFixture()
        : server(),
          transport(),
          orb(server.supervisor())
    {
        init_rpc();
        orb.Listen(0);
        spec = vespalib::make_string("tcp/localhost:%d", orb.GetListenPort());
        transport.Start();
    }

    void RPC_waitFor(FRT_RPCRequest *req) {
        FRT_Values &params = *req->GetParams();
        FRT_Values &ret = *req->GetReturn();
        if (strcmp(params[0]._string._str, "my_ref") == 0) {
            ret.AddString("my_path");
        } else {
            req->SetError(FRTE_RPC_METHOD_FAILED, "invalid file reference");
        }
    }

    ~ServerFixture() {
        transport.ShutDown(true);
    }
};

TEST(FileAcquirerTest, require_that_files_can_be_acquired_over_rpc)
{
    ServerFixture f1;
    RpcFileAcquirer f2(f1.transport, f1.spec);
    EXPECT_EQ("my_path", f2.wait_for("my_ref", 60.0));
    EXPECT_EQ("", f2.wait_for("bogus_ref", 60.0));
}

GTEST_MAIN_RUN_ALL_TESTS()
