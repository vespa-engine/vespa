// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/process/process.h>
#include <vespa/vespalib/net/crypto_engine.h>

using namespace vbench;
using vespalib::Process;

using InputReader = vespalib::InputReader;
using OutputWriter = vespalib::OutputWriter;
using vespalib::SimpleBuffer;
using vespalib::test::Nexus;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();

bool endsWith(const Memory &mem, const string &str) {
    return (mem.size < str.size()) ? false
        : (strncmp(mem.data + mem.size - str.size(), str.data(), str.size()) == 0);
}

void readUntil(Input &input, SimpleBuffer &buffer, const string &end) {
    InputReader in(input);
    while (!endsWith(buffer.get(), end)) {
        char c = in.read();
        if (in.failed()) {
            return;
        }
        buffer.reserve(1).data[0] = c;
        buffer.commit(1);
    }
}

TEST(AppDumpurlTest, dumpurl_usage) {
    std::string out;
    EXPECT_FALSE(Process::run("../../apps/dumpurl/vbench_dumpurl_app", out));
    fprintf(stderr, "%s\n", out.c_str());
}

TEST(AppDumpurlTest, run_dumpurl) {
    size_t num_threads = 2;
    ServerSocket f1;
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        Stream::UP stream = f1.accept(*null_crypto);
                        SimpleBuffer ignore;
                        readUntil(*stream, ignore, "\r\n\r\n");
                        OutputWriter out(*stream, 256);
                        out.write("HTTP/1.1 200\r\n");
                        out.write("content-length: 4\r\n");
                        out.write("\r\n");
                        out.write("data");
                    } else {
                        std::string out;
                        EXPECT_TRUE(Process::run(strfmt("../../apps/dumpurl/vbench_dumpurl_app localhost %d /foo",
                                                        f1.port()).c_str(), out));
                        fprintf(stderr, "%s\n", out.c_str());
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
