// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <atomic>
#include <chrono>
#include <thread>

using namespace proton::matching;
using vespalib::Doom;
using vespalib::steady_time;

namespace {

constexpr uint32_t many_hits = 1000;
constexpr uint32_t few_docs = 10;

struct QueryLimiterTest : ::testing::Test {
    std::atomic<steady_time> now;
    Doom                     doom;
    Doom                     doomed;
    QueryLimiter             limiter;

    QueryLimiterTest()
        : now(vespalib::steady_clock::now()),
          doom(now, now.load() + std::chrono::hours(1)),
          doomed(now, steady_time()),
          limiter() {}

    QueryLimiter::Token::UP limited_token(const Doom& d) {
        return limiter.getToken(d, few_docs, many_hits, true, false);
    }

    // gives up with a failure, rather than hanging, if the waiters never show up
    void wait_for_waiting(size_t n) {
        auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(30);
        while (limiter.num_waiting() < n) {
            if (std::chrono::steady_clock::now() > deadline) {
                ADD_FAILURE() << "timed out waiting for " << n << " waiters";
                return;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
};

TEST_F(QueryLimiterTest, no_limit_when_not_configured) {
    auto t1 = limited_token(doom);
    auto q1 = limiter.getQueryToken(doom, 8);
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, per_thread_token_only_taken_when_heuristic_says_so) {
    limiter.configure(4, 1.0, 100);
    auto t1 = limiter.getToken(doom, few_docs, many_hits, false, false); // no sorting/grouping
    auto t2 = limiter.getToken(doom, few_docs, 50, true, false);         // too few hits
    auto t3 = limiter.getToken(doom, 10000, many_hits, true, false);     // enough coverage
    EXPECT_EQ(0, limiter.active_threads());
    auto t4 = limited_token(doom);
    EXPECT_EQ(1, limiter.active_threads());
    t4.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, query_token_takes_one_unit_per_thread) {
    limiter.configure(10, 1.0, 100);
    auto q1 = limiter.getQueryToken(doom, 4);
    EXPECT_EQ(4, limiter.active_threads());
    auto q2 = limiter.getQueryToken(doom, 0);
    EXPECT_EQ(5, limiter.active_threads());
    q1.reset();
    EXPECT_EQ(1, limiter.active_threads());
    q2.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, query_token_is_capped_at_max_threads) {
    limiter.configure(3, 1.0, 100);
    auto q1 = limiter.getQueryToken(doom, 16);
    EXPECT_EQ(3, limiter.active_threads());
    q1.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, hard_doomed_request_is_let_through) {
    limiter.configure(2, 1.0, 100);
    auto q1 = limiter.getQueryToken(doom, 2);
    auto t1 = limited_token(doomed);
    auto q2 = limiter.getQueryToken(doomed, 2);
    EXPECT_EQ(5, limiter.active_threads());
    EXPECT_EQ(0u, limiter.num_waiting());
    q1.reset();
    t1.reset();
    q2.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, waits_until_capacity_is_released) {
    limiter.configure(4, 1.0, 100);
    auto              q1 = limiter.getQueryToken(doom, 4);
    std::atomic<bool> done(false);
    std::thread       thread([&] {
        auto t = limited_token(doom);
        done = true;
    });
    wait_for_waiting(1);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    EXPECT_FALSE(done);
    q1.reset();
    thread.join();
    EXPECT_TRUE(done);
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, large_request_is_not_starved_by_small_ones) {
    limiter.configure(2, 1.0, 100);
    auto                    t1 = limited_token(doom);
    std::atomic<bool>       query_done(false);
    std::atomic<bool>       small_done(false);
    QueryLimiter::Token::UP q;
    std::thread             query_thread([&] {
        q = limiter.getQueryToken(doom, 2);
        query_done = true;
    });
    wait_for_waiting(1);
    std::thread small_thread([&] {
        // would fit right now, but must queue behind the query token
        auto t = limited_token(doom);
        small_done = true;
    });
    wait_for_waiting(2);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    EXPECT_FALSE(query_done);
    EXPECT_FALSE(small_done);
    t1.reset();
    query_thread.join();
    EXPECT_TRUE(query_done);
    EXPECT_EQ(2, limiter.active_threads());
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    EXPECT_FALSE(small_done);
    q.reset();
    small_thread.join();
    EXPECT_TRUE(small_done);
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, reconfig_to_unlimited_wakes_waiters) {
    limiter.configure(1, 1.0, 100);
    auto        t1 = limited_token(doom);
    std::thread thread([&] { auto t = limited_token(doom); });
    wait_for_waiting(1);
    auto start = std::chrono::steady_clock::now();
    limiter.configure(0, 1.0, 100);
    thread.join();
    // well below the wait slice cap, so the waiter was woken by configure
    EXPECT_LT(std::chrono::steady_clock::now() - start, std::chrono::milliseconds(500));
    t1.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, reconfig_to_lower_max_lets_capped_query_token_through) {
    limiter.configure(4, 1.0, 100);
    auto                    t1 = limited_token(doom);
    QueryLimiter::Token::UP q;
    std::thread             thread([&] { q = limiter.getQueryToken(doom, 4); });
    wait_for_waiting(1);
    // the query token is now capped at 2 units; with 1 unit held by t1 it
    // still does not fit, but once t1 is released it does (4 units never would)
    limiter.configure(2, 1.0, 100);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    EXPECT_EQ(1u, limiter.num_waiting());
    t1.reset();
    thread.join();
    EXPECT_EQ(2, limiter.active_threads());
    q.reset();
    EXPECT_EQ(0, limiter.active_threads());
}

TEST_F(QueryLimiterTest, doomed_waiter_in_middle_of_queue_does_not_strand_others) {
    limiter.configure(1, 1.0, 100);
    Doom                    late_doom(now, now.load() + std::chrono::milliseconds(50));
    auto                    t1 = limited_token(doom);
    std::atomic<bool>       front_done(false);
    std::atomic<bool>       middle_done(false);
    std::atomic<bool>       back_done(false);
    QueryLimiter::Token::UP front_token;
    QueryLimiter::Token::UP middle_token;
    std::thread             front_thread([&] {
        front_token = limiter.getQueryToken(doom, 1);
        front_done = true;
    });
    wait_for_waiting(1);
    std::thread middle_thread([&] {
        middle_token = limited_token(late_doom);
        middle_done = true;
    });
    wait_for_waiting(2);
    std::thread back_thread([&] {
        auto t = limited_token(doom);
        back_done = true;
    });
    wait_for_waiting(3);
    now = now.load() + std::chrono::seconds(1); // only late_doom is now past hard doom
    middle_thread.join();
    EXPECT_TRUE(middle_done);
    EXPECT_EQ(2u, limiter.num_waiting());
    EXPECT_EQ(2, limiter.active_threads());
    t1.reset();
    middle_token.reset();
    front_thread.join();
    EXPECT_TRUE(front_done);
    std::this_thread::sleep_for(std::chrono::milliseconds(20));
    EXPECT_FALSE(back_done);
    front_token.reset();
    back_thread.join();
    EXPECT_TRUE(back_done);
    EXPECT_EQ(0, limiter.active_threads());
    EXPECT_EQ(0u, limiter.num_waiting());
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
