// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/child_process.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/thread.h>
#include <atomic>
#include <csignal>

// reserved in vespa/factory/doc/port-ranges.txt
static const int PORT0 = 18570;
static const int PORT1 = 18571;

using vespalib::ChildProcess;

bool runProc(ChildProcess &proc, std::atomic<bool> &done) {
    char buf[4_Ki];
    proc.close(); // close stdin
    while (proc.running() && !done) {
        if (!proc.eof()) {
            uint32_t res = proc.read(buf, sizeof(buf), 10);
            std::string tmp(buf, res);
            fprintf(stderr, "%s", tmp.c_str());
        }
        vespalib::Thread::sleep(10);
    }
    if (done && proc.running()) {
        kill(proc.getPid(), SIGTERM);
        return proc.wait(60000);
    }
    return !proc.failed();
}

bool runProc(const std::string &cmd) {
    bool ok = false;
    for (size_t retry = 0; !ok && retry < 60; ++retry) {
        if (retry > 0) {
            fprintf(stderr, "retrying command in 500ms...\n");
            vespalib::Thread::sleep(500);
        }
        std::atomic<bool> done(false);
        ChildProcess proc(cmd.c_str());
        ok = runProc(proc, done);
    }
    return ok;
}

TEST("usage") {
    std::atomic<bool> done(false);
    {
        ChildProcess proc("exec ../../examples/proxy/fnet_proxy_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/ping/fnet_pingserver_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/ping/fnet_pingclient_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/fnet_rpc_client_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/fnet_rpc_server_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/fnet_echo_client_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/vespa-rpc-info");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/vespa-rpc-invoke-bin");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/fnet_rpc_callback_server_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/fnet_rpc_callback_client_app");
        EXPECT_FALSE(runProc(proc, done));
    }
    {
        ChildProcess proc("exec ../../examples/frt/rpc/vespa-rpc-proxy");
        EXPECT_FALSE(runProc(proc, done));
    }
}

TEST("timeout") {
    std::string out;
    EXPECT_TRUE(ChildProcess::run("exec ../../examples/timeout/fnet_timeout_app", out));
    fprintf(stderr, "%s\n", out.c_str());
}

TEST_MT_F("ping", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/ping/fnet_pingserver_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/ping/fnet_pingclient_app tcp/localhost:%d",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("ping times out", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d",
                                                PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        float timeout_s = 0.1;
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/ping/fnet_pingclient_app tcp/localhost:%d %f",
                                                  PORT0, timeout_s).c_str()));
        f1 = true;
    }
}

TEST_MT_F("ping with proxy", 3, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/ping/fnet_pingserver_app tcp/%d",
                                                PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else if (thread_id == 1) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/proxy/fnet_proxy_app tcp/%d tcp/localhost:%d",
                                                PORT1, PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/ping/fnet_pingclient_app tcp/localhost:%d",
                                                  PORT1).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc client server", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d",
                                                PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_client_app tcp/localhost:%d",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc echo client", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_echo_client_app tcp/localhost:%d",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc info", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/vespa-rpc-info tcp/localhost:%d",
                                                  PORT0).c_str()));
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/vespa-rpc-info tcp/localhost:%d verbose",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc invoke", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/vespa-rpc-invoke-bin tcp/localhost:%d frt.rpc.echo "
                                                  "b:1 h:2 i:4 l:8 f:0.5 d:0.25 s:foo",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc callback client server", 2, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_callback_server_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_callback_client_app tcp/localhost:%d",
                                                  PORT0).c_str()));
        f1 = true;
    }
}

TEST_MT_F("rpc callback client server with proxy", 3, std::atomic<bool>()) {
    if (thread_id == 0) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_callback_server_app tcp/%d",
                                             PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else if (thread_id == 1) {
        ChildProcess proc(vespalib::make_string("exec ../../examples/frt/rpc/vespa-rpc-proxy tcp/%d tcp/localhost:%d",
                                             PORT1, PORT0).c_str());
        TEST_BARRIER();
        EXPECT_TRUE(runProc(proc, f1));
    } else {
        TEST_BARRIER();
        EXPECT_TRUE(runProc(vespalib::make_string("exec ../../examples/frt/rpc/fnet_rpc_callback_client_app tcp/localhost:%d",
                                                  PORT1).c_str()));
        f1 = true;
    }
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
