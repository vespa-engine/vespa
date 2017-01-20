// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/util/slaveproc.h>

using namespace vbench;
using vespalib::SlaveProc;

bool endsWith(const Memory &mem, const string &str) {
    return (mem.size < str.size()) ? false
        : (strncmp(mem.data + mem.size - str.size(), str.data(), str.size()) == 0);
}

void readUntil(Input &input, SimpleBuffer &buffer, const string &end) {
    ByteInput in(input);
    while (!endsWith(buffer.get(), end)) {
        int c = in.get();
        if (c < 0) {
            return;
        }
        buffer.reserve(1).data[0] = c;
        buffer.commit(1);
    }
}

TEST("vbench usage") {
    std::string out;
    EXPECT_FALSE(SlaveProc::run("../../apps/vbench/vbench_app", out));
    fprintf(stderr, "%s\n", out.c_str());
}

TEST_MT_F("run vbench", 2, ServerSocket()) {
    if (thread_id == 0) {
        for (;;) {
            Stream::UP stream = f1.accept();
            if (stream.get() == 0) {
                break;
            }
            SimpleBuffer ignore;
            readUntil(*stream, ignore, "\r\n\r\n");
            BufferedOutput out(*stream, 256);
            out.append("HTTP/1.1 200\r\n");
            out.append("content-length: 4\r\n");
            out.append("X-Yahoo-Vespa-NumHits 10\r\n");
            out.append("X-Yahoo-Vespa-TotalHitCount 100\r\n");
            out.append("\r\n");
            out.append("data");
        }
    } else {
        std::string out;
        EXPECT_TRUE(SlaveProc::run(strfmt("sed -i 's/_LOCAL_PORT_/%d/' vbench.cfg", f1.port()).c_str()));
        EXPECT_TRUE(SlaveProc::run("../../apps/vbench/vbench_app run vbench.cfg 2> vbench.out", out));
        fprintf(stderr, "%s\n", out.c_str());
        f1.close();
    }
}

TEST_MAIN_WITH_PROCESS_PROXY() { TEST_RUN_ALL(); }
