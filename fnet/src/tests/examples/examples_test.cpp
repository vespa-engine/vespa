// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>

#include <atomic>
#include <csignal>
#include <thread>

// reserved in vespa/factory/doc/port-ranges.txt
static const int PORT0 = 18570;

using vespalib::Process;
using vespalib::make_string_short::fmt;
using vespalib::test::Nexus;

int run_proc(Process& proc, std::string& output) {
    proc.close();
    for (auto mem = proc.obtain(); mem.size > 0; mem = proc.obtain()) {
        output.append(mem.data, mem.size);
        proc.evict(mem.size);
    }
    return proc.join();
}

void consume_result(Process& proc) {
    std::string output;
    int         status = run_proc(proc, output);
    fprintf(stderr, "child output(server): >>>%s<<<\n", output.c_str());
    if (status != 0) {
        // Allow 'killed by SIGTERM' result status. This is needed as
        // some clients will exit with success status even when the
        // server is not yet running, resulting in the server being
        // killed before it has installed any signal handlers.
        EXPECT_TRUE(status & 0x80000000);
        status &= 0x7fffffff;
        EXPECT_TRUE(WIFSIGNALED(status));
        EXPECT_EQ(WTERMSIG(status), SIGTERM);
    }
}

bool run_with_retry(const std::string& cmd) {
    for (size_t retry = 0; retry < 60; ++retry) {
        if (retry > 0) {
            fprintf(stderr, "retrying command in 500ms...\n");
            std::this_thread::sleep_for(500ms);
        }
        std::string output;
        Process     proc(cmd, true);
        int         status = run_proc(proc, output);
        fprintf(stderr, "child output(client): >>>%s<<<\n", output.c_str());
        if (status == 0) {
            return true;
        }
    }
    fprintf(stderr, "giving up...\n");
    return false;
}

TEST(ExamplesTest, usage) {
    EXPECT_FALSE(Process::run("exec ../../examples/ping/fnet_pingserver_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/ping/fnet_pingclient_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/fnet_rpc_client_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/fnet_rpc_server_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/fnet_echo_client_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/vespa-rpc-info"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/vespa-rpc-invoke-bin"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/fnet_rpc_callback_server_app"));
    EXPECT_FALSE(Process::run("exec ../../examples/frt/rpc/fnet_rpc_callback_client_app"));
}

TEST(ExamplesTest, timeout) {
    std::string out;
    EXPECT_TRUE(Process::run("exec ../../examples/timeout/fnet_timeout_app", out));
    fprintf(stderr, "%s\n", out.c_str());
}

TEST(ExamplesTest, ping) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/ping/fnet_pingserver_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(run_with_retry(fmt("exec ../../examples/ping/fnet_pingclient_app tcp/localhost:%d", PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, ping_times_out) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            float timeout_s = 0.1;
            ctx.barrier();
            EXPECT_TRUE(run_with_retry(
                fmt("exec ../../examples/ping/fnet_pingclient_app tcp/localhost:%d %f", PORT0, timeout_s)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, rpc_client_server) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(
                run_with_retry(fmt("exec ../../examples/frt/rpc/fnet_rpc_client_app tcp/localhost:%d", PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, rpc_echo_client) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(
                run_with_retry(fmt("exec ../../examples/frt/rpc/fnet_echo_client_app tcp/localhost:%d", PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, rpc_info) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(run_with_retry(fmt("exec ../../examples/frt/rpc/vespa-rpc-info tcp/localhost:%d", PORT0)));
            EXPECT_TRUE(
                run_with_retry(fmt("exec ../../examples/frt/rpc/vespa-rpc-info tcp/localhost:%d verbose", PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, rpc_invoke) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(run_with_retry(
                fmt("exec ../../examples/frt/rpc/vespa-rpc-invoke-bin tcp/localhost:%d frt.rpc.echo "
                                            "b:1 h:2 i:4 l:8 f:0.5 d:0.25 s:foo",
                                PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

TEST(ExamplesTest, rpc_callback_client_server) {
    constexpr size_t num_threads = 2;
    pid_t            f1(-1);
    auto             task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            Process proc(fmt("exec ../../examples/frt/rpc/fnet_rpc_callback_server_app tcp/%d", PORT0), true);
            f1 = proc.pid();
            ctx.barrier();
            consume_result(proc);
        } else {
            ctx.barrier();
            EXPECT_TRUE(run_with_retry(
                fmt("exec ../../examples/frt/rpc/fnet_rpc_callback_client_app tcp/localhost:%d", PORT0)));
            kill(f1, SIGTERM);
        }
    };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
