// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/net/crypto_engine.h>

using namespace vbench;

using InputReader = vespalib::InputReader;
using OutputWriter = vespalib::OutputWriter;

using vespalib::SimpleBuffer;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();

void checkMemory(const string &expect, const Memory &mem) {
    EXPECT_EQUAL(expect, string(mem.data, mem.size));
}

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

TEST_MT_F("verify request", 2, ServerSocket()) {
    if (thread_id == 0) {
        SimpleBuffer expect;
        {
            OutputWriter out(expect, 256);
            out.write("GET /this/is/the/url HTTP/1.1\r\n");
            out.write("Host: localhost\r\n");
            out.write("User-Agent: vbench\r\n");
            out.write("X-Yahoo-Vespa-Benchmarkdata: true\r\n");
            out.write("X-Yahoo-Vespa-Benchmarkdata-Coverage: true\r\n");
            out.write("\r\n");
        }
        SimpleBuffer actual;
        Stream::UP stream = f1.accept(*null_crypto);
        ASSERT_TRUE(stream.get() != 0);
        readUntil(*stream, actual, "\r\n\r\n");
        EXPECT_TRUE(expect == actual);
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(*null_crypto, ServerSpec("localhost", f1.port()),
                          "/this/is/the/url", handler);
    }
}

TEST_MT_F("verify connection close", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept(*null_crypto);
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        OutputWriter out(*stream, 256);
        out.write("HTTP/1.0 200\r\n");
        out.write("\r\n");
        out.write("data");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(*null_crypto, ServerSpec("localhost", f1.port()),
                          "/foo", handler);
        EXPECT_EQUAL(0u, handler.failures().size());
        EXPECT_EQUAL(0u, handler.headers().size());
        TEST_DO(checkMemory("data", handler.content()));
    }
}

TEST_MT_F("verify content length", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept(*null_crypto);
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        OutputWriter out(*stream, 256);
        out.write("HTTP/1.1 200\r\n");
        out.write("content-length: 4\r\n");
        out.write("\r\n");
        out.write("data");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(*null_crypto, ServerSpec("localhost", f1.port()),
                          "/foo", handler);
        EXPECT_EQUAL(0u, handler.failures().size());
        EXPECT_EQUAL(1u, handler.headers().size());
        TEST_DO(checkMemory("data", handler.content()));
    }
}

TEST_MT_F("verify chunked encoding", 2, ServerSocket()) {
    if (thread_id == 0) {
        Stream::UP stream = f1.accept(*null_crypto);
        SimpleBuffer ignore;
        readUntil(*stream, ignore, "\r\n\r\n");
        OutputWriter out(*stream, 256);
        out.write("HTTP/1.1 200\r\n");
        out.write("transfer-encoding: chunked\r\n");
        out.write("\r\n");
        out.write("2\r\n");
        out.write("da\r\n");
        out.write("2\r\n");
        out.write("ta\r\n");
        out.write("0\r\n");
        out.write("\r\n");
    } else {
        SimpleHttpResultHandler handler;
        HttpClient::fetch(*null_crypto, ServerSpec("localhost", f1.port()),
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
