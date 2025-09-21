// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/net/crypto_engine.h>

using namespace vbench;
using vespalib::CountDownLatch;
using vespalib::test::Nexus;

auto null_crypto = std::make_shared<vespalib::NullCryptoEngine>();

TEST(HttpConnectionPoolTest, http_connection_pool) {
    size_t num_threads = 2;
    ServerSocket f1;
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        for (; f1.accept(*null_crypto); ) {}
                    } else {
                        Timer timer;
                        HttpConnection::UP conn;
                        HttpConnectionPool pool(null_crypto, timer);
                        conn = pool.getConnection(ServerSpec("localhost", f1.port()));
                        EXPECT_TRUE(conn);
                        pool.putConnection(std::move(conn));
                        EXPECT_FALSE(conn);
                        conn = pool.getConnection(ServerSpec("localhost", f1.port()));
                        EXPECT_TRUE(conn);
                        conn->stream().obtain(); // trigger eof
                        pool.putConnection(std::move(conn));
                        EXPECT_FALSE(conn);
                        conn = pool.getConnection(ServerSpec("localhost", f1.port()));
                        EXPECT_TRUE(conn);
                        f1.close();
                    }
                };
    Nexus::run(num_threads, task);
}

TEST(HttpConnectionPoolTest, stress_http_connection_pool)
{
    size_t num_threads = 256;
    ServerSocket f1;
    Timer f2;
    HttpConnectionPool f3(null_crypto, f2);
    CountDownLatch f4(num_threads-2);
    auto task = [&](Nexus &ctx){
                    if (ctx.thread_id() == 0) {
                        for (; f1.accept(*null_crypto); ) {}
                    } else {
                        while (f2.sample() < 5.0) {
                            HttpConnection::UP conn = f3.getConnection(ServerSpec("localhost", f1.port()));
                            EXPECT_TRUE(conn);
                            if (ctx.thread_id() > (num_threads / 2)) {
                                conn->stream().obtain(); // trigger eof
                            }
                            f3.putConnection(std::move(conn));
                            EXPECT_FALSE(conn);
                        }
                        if (ctx.thread_id() == 1) {
                            f4.await();
                            f1.close();
                        } else {
                            f4.countDown();
                        }
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
