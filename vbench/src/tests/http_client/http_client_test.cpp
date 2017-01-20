// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

void checkMemory(const string &expect, const Memory &mem) {
    EXPECT_EQUAL(expect, string(mem.data, mem.size));
}

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

TEST_MT_F("verify request", 2, ServerSocket()) {
    if (thread_id == 0) {
        SimpleBuffer expect;
        {
            BufferedOutput dst(expect, 256);
            dst.append("GET /this/is/the/url HTTP/1.1\r\n");
            dst.append("Host: localhost\r\n");
            dst.append("User-Agent: vbench\r\n");
            dst.append("X-Yahoo-Vespa-Benchmarkdata: true\r\n");
            dst.append("X-Yahoo-Vespa-Benchmarkdata-Coverage: true\r\n");
            dst.append("\r\n");
        }
        SimpleBuffer actual;
        Stream::UP stream = f1.accept();
        ASSERT_TRUE(stream.get() != 0);
        readUntil(*stream, actual, "\r\n\r\n");
        EXPECT_TRUE(expect == actual);
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(ServerSpec("localhost", f1.port()),
                          "/this/is/the/url", handler);
    }
}

TEST_MT_F("verify connection close", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept();
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        BufferedOutput out(*stream, 256);
        out.append("HTTP/1.0 200\r\n");
        out.append("\r\n");
        out.append("data");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(ServerSpec("localhost", f1.port()),
                          "/foo", handler);
        EXPECT_EQUAL(0u, handler.failures().size());
        EXPECT_EQUAL(0u, handler.headers().size());
        TEST_DO(checkMemory("data", handler.content()));
    }
}

TEST_MT_F("verify content length", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept();
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        BufferedOutput out(*stream, 256);
        out.append("HTTP/1.1 200\r\n");
        out.append("content-length: 4\r\n");
        out.append("\r\n");
        out.append("data");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(ServerSpec("localhost", f1.port()),
                          "/foo", handler);
        EXPECT_EQUAL(0u, handler.failures().size());
        EXPECT_EQUAL(1u, handler.headers().size());
        TEST_DO(checkMemory("data", handler.content()));
    }
}

TEST_MT_F("verify chunked encoding", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept();
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        BufferedOutput out(*stream, 256);
        out.append("HTTP/1.1 200\r\n");
        out.append("transfer-encoding: chunked\r\n");
        out.append("\r\n");
        out.append("2\r\n");
        out.append("da\r\n");
        out.append("2\r\n");
        out.append("ta\r\n");
        out.append("0\r\n");
        out.append("\r\n");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(ServerSpec("localhost", f1.port()),
                          "/foo", handler);
        if (handler.failures().size() > 0) {
            fprintf(stderr, "failure: %s\n", handler.failures()[0].c_str());
        }
        EXPECT_EQUAL(0u, handler.failures().size());
        EXPECT_EQUAL(1u, handler.headers().size());
        TEST_DO(checkMemory("data", handler.content()));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
